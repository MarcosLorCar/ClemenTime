package com.marcoslorcar.clementime.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcoslorcar.clementime.MainActivity
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.utils.ScheduleDiffChecker
import com.marcoslorcar.clementime.utils.SemesterUtils
import com.marcoslorcar.clementime.utils.SlotDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import androidx.core.graphics.toColorInt

data class SyncResult(
    val diffs: List<SlotDiff> = emptyList(),
    val remoteSlots: List<JsonFlatSlot> = emptyList(),
    val remoteHash: String = "",
    val semester: Int? = null
)

@HiltWorker
class ScheduleUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val importRepository: ImportRepository,
    private val apiService: GitHubScheduleApiService?
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val facultyId = settingsRepository.selectedFacultyIdFlow.first()
            if (facultyId.isBlank()) {
                android.util.Log.d(WORK_TAG, "Skipping schedule update check: no faculty linked yet.")
                return Result.success()
            }

            val syncResult = performSync(
                context = context,
                settingsRepository = settingsRepository,
                importRepository = importRepository,
                apiService = apiService,
                ignoreInterval = true
            )

            if (syncResult.diffs.isNotEmpty()) {
                // Only notify once per remote version. The accepted-version hash is not
                // advanced here (the diff sheet re-syncs to populate itself), so without
                // this the same notification would re-fire every interval forever.
                val semester = syncResult.semester ?: settingsRepository.currentSemesterFlow.first()
                val alreadyNotified = settingsRepository
                    .getLastNotifiedScheduleHashFlow(semester).first()

                if (syncResult.remoteHash.isBlank() || syncResult.remoteHash != alreadyNotified) {
                    val changedSubjects = syncResult.diffs.map { it.subjectCode.ifBlank { it.subjectName } }.distinct()
                    val subjectListStr = changedSubjects.joinToString(", ")
                    val messageText = context.getString(R.string.schedule_updates_found, subjectListStr)
                    postNotification(context, messageText)

                    if (syncResult.remoteHash.isNotBlank()) {
                        settingsRepository.setLastNotifiedScheduleHash(semester, syncResult.remoteHash)
                    }
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val CHANNEL_ID = "CLEMENTIME_SCHEDULE_UPDATE_CHANNEL"
        const val EXTRA_SHOW_SCHEDULE_DIFF = "SHOW_SCHEDULE_DIFF"
        const val WORK_TAG = "ScheduleUpdateWorkerTag"
        const val UNIQUE_WORK_NAME = "ScheduleUpdateWork"
        const val ONE_TIME_WORK_NAME = "ScheduleUpdateWork_OneTime"

        fun enqueueOneTimeWork(context: Context, expedited: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)

            if (expedited) {
                requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        /** Applies a user-chosen interval in minutes or hours, restarting the period immediately. */
        fun schedulePeriodicWork(context: Context, intervalValue: Int) {
            enqueuePeriodic(context, intervalValue, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        /**
         * Re-arms periodic work on app start. Uses KEEP rather than CANCEL_AND_REENQUEUE:
         * restarting the period on every launch would mean a frequently-opened app never
         * lets the interval elapse, so the worker would never run.
         */
        fun ensurePeriodicWorkScheduled(context: Context, intervalValue: Int) {
            enqueuePeriodic(context, intervalValue, ExistingPeriodicWorkPolicy.KEEP)
        }

        private fun enqueuePeriodic(
            context: Context,
            intervalValue: Int,
            policy: ExistingPeriodicWorkPolicy
        ) {
            val workManager = WorkManager.getInstance(context)
            val minutes = when {
                intervalValue <= 0 -> 0
                intervalValue == 15 -> 15
                intervalValue <= 24 -> intervalValue * 60
                else -> intervalValue
            }

            if (minutes <= 0) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            } else {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(minutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()

                workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, policy, request)
            }
        }

        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        suspend fun performSync(
            context: Context,
            settingsRepository: SettingsRepository,
            importRepository: ImportRepository,
            apiService: GitHubScheduleApiService?,
            ignoreInterval: Boolean = false
        ): SyncResult {
            val syncMode = settingsRepository.syncModeFlow.first()
            if (syncMode == com.marcoslorcar.clementime.data.SyncMode.OFFLINE) return SyncResult()

            if (!ignoreInterval) {
                val intervalMinutes = settingsRepository.autoUpdateIntervalMinutesFlow.first()
                if (intervalMinutes <= 0) return SyncResult()
            }

            val currentSemester = settingsRepository.currentSemesterFlow.first()
            val universityId = settingsRepository.selectedUniversityIdFlow.first()
            val facultyId = settingsRepository.selectedFacultyIdFlow.first()
            if (facultyId.isBlank()) return SyncResult()

            val baseUrl = settingsRepository.githubRepoBaseUrlFlow.first()

            val remoteSummariesResult = importRepository.fetchRemoteSchedules(
                rawBaseUrl = baseUrl,
                facultyId = facultyId,
                cacheBust = true,
                universityId = universityId
            )
            if (remoteSummariesResult.isFailure) {
                return SyncResult()
            }

            val (effectiveBaseUrl, summaries) = remoteSummariesResult.getOrNull() ?: return SyncResult()
            val semestersPerYear = settingsRepository.semestersPerYearFlow.first()
            val summarySemesters = SemesterUtils.resolveSummarySemesters(summaries).values
            val semestersToCheck = (listOf(currentSemester) + (1..semestersPerYear) + summarySemesters).distinct()

            for (semester in semestersToCheck) {
                val result = performSyncForSemester(
                    context = context,
                    settingsRepository = settingsRepository,
                    importRepository = importRepository,
                    apiService = apiService,
                    effectiveBaseUrl = effectiveBaseUrl,
                    summaries = summaries,
                    semester = semester,
                    universityId = universityId,
                    facultyId = facultyId
                )
                if (result != null && result.diffs.isNotEmpty()) {
                    settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())
                    return result
                }
            }

            settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())
            return SyncResult(semester = currentSemester)
        }

        private suspend fun performSyncForSemester(
            context: Context,
            settingsRepository: SettingsRepository,
            importRepository: ImportRepository,
            apiService: GitHubScheduleApiService?,
            effectiveBaseUrl: String,
            summaries: List<RemoteScheduleSummary>,
            semester: Int,
            universityId: String,
            facultyId: String
        ): SyncResult? {
            // No fallback to summaries.first(): diffing against another semester's schedule
            // would mark every subject as changed.
            val summary = SemesterUtils.findSummaryForSemester(summaries, semester) ?: return null

            val existingActiveSubjects = importRepository.getExistingActiveSubjects(semester)
            val remoteHash = summary.hash ?: ""
            val lastKnownHash = settingsRepository.getLastKnownScheduleHashFlow(semester).first()

            if (existingActiveSubjects.isEmpty()) {
                if (remoteHash.isNotBlank() && lastKnownHash != remoteHash) {
                    settingsRepository.setLastKnownScheduleHash(semester, remoteHash)
                }
                return null
            }

            if (lastKnownHash.isNotBlank() && lastKnownHash == remoteHash) {
                return null
            }

            if (apiService == null) return null

            val fullUrl = importRepository.normalizeGitHubUrl(
                if (effectiveBaseUrl.endsWith("/")) "${effectiveBaseUrl}${summary.path}" else "${effectiveBaseUrl}/${summary.path}"
            )
            val cacheBustedUrl = if (remoteHash.isNotBlank()) {
                if (fullUrl.contains("?")) "$fullUrl&v=$remoteHash" else "$fullUrl?v=$remoteHash"
            } else {
                fullUrl
            }

            val rawResponse = apiService.getRawScheduleSchema(cacheBustedUrl)
            if (!rawResponse.isSuccessful) return null

            val jsonString = rawResponse.body()?.string() ?: return null

            val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val remoteSlots = try {
                jsonParser.decodeFromString<List<JsonFlatSlot>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }

            if (remoteSlots.isEmpty()) return null

            var previousSubjectHashes = settingsRepository.getLastRemoteSubjectHashesFlow(semester).first()
            if (previousSubjectHashes.isEmpty()) {
                val cachedSlots = importRepository.getCachedRemoteSlotsForSemester(
                    context = context,
                    semester = semester,
                    universityId = universityId,
                    facultyId = facultyId
                )
                if (cachedSlots.isNotEmpty()) {
                    previousSubjectHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
                        existingSubjects = existingActiveSubjects,
                        remoteSlots = cachedSlots,
                        currentSemester = semester
                    )
                    settingsRepository.setLastRemoteSubjectHashes(semester, previousSubjectHashes)
                }
            }

            val currentSubjectHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
                existingSubjects = existingActiveSubjects,
                remoteSlots = remoteSlots,
                currentSemester = semester
            )
            val diffs = ScheduleDiffChecker.findDiffs(
                existingSubjects = existingActiveSubjects,
                remoteSlots = remoteSlots,
                currentSemester = semester,
                previousRemoteSubjectHashes = previousSubjectHashes
            )

            // The published schedule changed, but not for any subject this user follows
            // (or only for subjects whose upstream schedule didn't change since manual edits).
            if (diffs.isEmpty() && remoteHash.isNotBlank()) {
                settingsRepository.setLastKnownScheduleHash(semester, remoteHash)
                settingsRepository.setLastRemoteSubjectHashes(semester, currentSubjectHashes)
                val resolvedTermId = summary.id.ifBlank { "${semester}C" }
                val scopedId = if (universityId.isNotBlank() && facultyId.isNotBlank()) {
                    "${universityId}_${facultyId}_${resolvedTermId}"
                } else {
                    resolvedTermId
                }
                importRepository.cacheRemoteSlots(
                    context = context,
                    fileId = scopedId,
                    title = summary.title,
                    description = summary.description,
                    remotePath = fullUrl,
                    rawJson = jsonString,
                    updatedTime = summary.updatedTime,
                    universityId = universityId.ifBlank { null },
                    facultyId = facultyId.ifBlank { null },
                    termId = resolvedTermId
                )
            }

            return SyncResult(
                diffs = diffs,
                remoteSlots = remoteSlots,
                remoteHash = remoteHash,
                semester = semester
            )
        }

        @SuppressLint("MissingPermission")
        private fun postNotification(context: Context, contentText: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.auto_update_interval_title),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for automatic schedule updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_SCHEDULE_DIFF, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor("#f8981d".toColorInt())
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            try {
                notificationManager.notify(1001, notification)
            } catch (_: SecurityException) {
                // Ignore missing POST_NOTIFICATIONS permission gracefully
            }
        }
    }
}
