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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcoslorcar.clementime.MainActivity
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.utils.ScheduleDiffChecker
import com.marcoslorcar.clementime.utils.SlotDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

data class SyncResult(
    val diffs: List<SlotDiff> = emptyList(),
    val remoteSlots: List<JsonFlatSlot> = emptyList()
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
            val syncResult = performSync(
                context = context,
                settingsRepository = settingsRepository,
                importRepository = importRepository,
                apiService = apiService,
                ignoreInterval = true
            )

            if (syncResult.diffs.isNotEmpty()) {
                val changedSubjects = syncResult.diffs.map { it.subjectCode.ifBlank { it.subjectName } }.distinct()
                val subjectListStr = changedSubjects.joinToString(", ")
                val messageText = context.getString(R.string.schedule_updates_found, subjectListStr)
                postNotification(context, messageText)
            }

            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val CHANNEL_ID = "CLEMENTIME_SCHEDULE_UPDATE_CHANNEL"
        const val WORK_TAG = "ScheduleUpdateWorkerTag"
        const val UNIQUE_WORK_NAME = "ScheduleUpdateWork"
        const val ONE_TIME_WORK_NAME = "ScheduleUpdateWork_OneTime"

        fun enqueueOneTimeWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodicWork(context: Context, hours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (hours <= 0) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            } else {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(hours.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    request
                )
            }
        }

        suspend fun performSync(
            @Suppress("UNUSED_PARAMETER") context: Context,
            settingsRepository: SettingsRepository,
            importRepository: ImportRepository,
            apiService: GitHubScheduleApiService?,
            ignoreInterval: Boolean = false
        ): SyncResult {
            if (!ignoreInterval) {
                val intervalHours = settingsRepository.autoUpdateIntervalHoursFlow.first()
                if (intervalHours <= 0) return SyncResult()
            }

            val currentSemester = settingsRepository.currentSemesterFlow.first()
            val baseUrl = settingsRepository.githubRepoBaseUrlFlow.first()

            val remoteSummariesResult = importRepository.fetchRemoteSchedules(baseUrl)
            if (remoteSummariesResult.isFailure) {
                return SyncResult()
            }

            val summaries = remoteSummariesResult.getOrNull() ?: return SyncResult()
            val targetSemesterId = "${currentSemester}C"
            val summary = summaries.find {
                it.id.equals(targetSemesterId, ignoreCase = true) ||
                        it.path.contains(targetSemesterId, ignoreCase = true)
            } ?: summaries.firstOrNull() ?: return SyncResult()

            val remoteHash = summary.hash ?: ""
            val lastKnownHash = settingsRepository.getLastKnownScheduleHashFlow(currentSemester).first()

            if (lastKnownHash.isBlank()) {
                if (remoteHash.isNotBlank()) {
                    settingsRepository.setLastKnownScheduleHash(currentSemester, remoteHash)
                }
                settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())
                return SyncResult()
            }

            if (lastKnownHash == remoteHash) {
                settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())
                return SyncResult()
            }

            if (apiService == null) return SyncResult()

            val fullUrl = importRepository.normalizeGitHubUrl(
                if (baseUrl.endsWith("/")) "${baseUrl}${summary.path}" else "${baseUrl}/${summary.path}"
            )

            val rawResponse = apiService.getRawScheduleSchema(fullUrl)
            if (!rawResponse.isSuccessful) return SyncResult()

            val jsonString = rawResponse.body()?.string() ?: return SyncResult()

            val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val remoteSlots = try {
                jsonParser.decodeFromString<List<JsonFlatSlot>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }

            if (remoteSlots.isEmpty()) return SyncResult()

            val existingActiveSubjects = importRepository.getExistingActiveSubjects(currentSemester)
            val diffs = ScheduleDiffChecker.findDiffs(existingActiveSubjects, remoteSlots, currentSemester)

            settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())
            settingsRepository.setLastKnownScheduleHash(currentSemester, remoteHash)

            return SyncResult(diffs = diffs, remoteSlots = remoteSlots)
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
                putExtra("SHOW_SCHEDULE_DIFF", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
