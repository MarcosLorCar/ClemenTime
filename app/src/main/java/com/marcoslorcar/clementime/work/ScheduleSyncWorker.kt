package com.marcoslorcar.clementime.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcoslorcar.clementime.MainActivity
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.JsonTimeSlot
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ScheduleSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val importRepository: ImportRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduleDao: ScheduleDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "schedule_sync_worker"
        private const val CHANNEL_ID = "schedule_updates"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d("ScheduleSyncWorker", "Worker started check...")
        val autoUpdateEnabled = settingsRepository.scheduleNotificationsEnabledFlow.first()
        if (!autoUpdateEnabled) {
            Log.d("ScheduleSyncWorker", "Auto-update disabled, skipping.")
            return Result.success()
        }

        val allSubjects = scheduleDao.getAllSubjectsWithSlots().first()
        Log.d("ScheduleSyncWorker", "Found ${allSubjects.size} total subjects in DB")
        
        val activeSubjects = allSubjects.filter { it.subject.isActive && it.subject.remotePath != null }
        if (activeSubjects.isEmpty()) {
            Log.d("ScheduleSyncWorker", "No active subjects with remotePath found. Subjects in DB: ${allSubjects.map { it.subject.name + " (path=" + it.subject.remotePath + ")" }}")
            return Result.success()
        }

        val affectedSubjectIds = mutableSetOf<String>()
        val remotePaths = activeSubjects.mapNotNull { it.subject.remotePath }.distinct()
        Log.d("ScheduleSyncWorker", "Checking ${remotePaths.size} remote paths: $remotePaths")

        for (path in remotePaths) {
            Log.d("ScheduleSyncWorker", "Fetching fresh schema for path: $path")
            val file = ImportFile(id = "sync_${path.hashCode()}", title = "Sync", remotePath = path)
            val result = importRepository.fetchRemoteScheduleSchema(appContext, file, forceRefresh = true)
            
            result.onSuccess { schema ->
                Log.d("ScheduleSyncWorker", "Successfully fetched schema: ${schema.title}")
                val subjectsInSchema = getAllSubjectsInSchema(schema)
                
                activeSubjects.filter { it.subject.remotePath == path }.forEach { localSws ->
                    val remoteSub = subjectsInSchema.find { it.code == localSws.subject.code }
                    if (remoteSub != null) {
                        val changed = detectChanges(localSws, remoteSub)
                        Log.d("ScheduleSyncWorker", "Subject ${localSws.subject.name}: changed=$changed")
                        if (changed) {
                            affectedSubjectIds.add(localSws.subject.id.toString())
                        }
                    } else {
                        Log.d("ScheduleSyncWorker", "Subject ${localSws.subject.code} not found in remote schema")
                    }
                }
            }.onFailure { error ->
                Log.e("ScheduleSyncWorker", "Failed to fetch remote schema for $path", error)
            }
        }

        if (affectedSubjectIds.isNotEmpty()) {
            Log.d("ScheduleSyncWorker", "Detected changes for subjects: $affectedSubjectIds")
            settingsRepository.setHasPendingScheduleUpdate(true)
            val currentAffected = settingsRepository.affectedSubjectIdsFlow.first()
            settingsRepository.setAffectedSubjectIds(currentAffected + affectedSubjectIds)
            
            val pushEnabled = settingsRepository.notifyViaPushFlow.first()
            if (pushEnabled) {
                Log.d("ScheduleSyncWorker", "Push notifications enabled, showing notification.")
                showNotification()
            } else {
                Log.d("ScheduleSyncWorker", "Push notifications disabled, skipping system notification.")
            }
        } else {
            Log.d("ScheduleSyncWorker", "No changes detected in any subject.")
        }

        settingsRepository.setLastScheduleSyncTimestamp(System.currentTimeMillis())

        return Result.success()
    }

    private fun getAllSubjectsInSchema(schema: ScheduleJsonSchema): List<JsonSubject> {
        val list = schema.subjects.toMutableList()
        schema.years.forEach { year ->
            list.addAll(year.subjects)
            year.groups.forEach { group ->
                list.addAll(group.subjects)
            }
        }
        return list
    }

    private fun detectChanges(local: SubjectWithSlots, remote: JsonSubject): Boolean {
        // Simple comparison: compare theory slots and lab variants
        val localTheory = local.slots.filter { it.entryType == EntryType.THEORY }
        if (localTheory.size != remote.theorySlots.size) return true
        
        for (slot in remote.theorySlots) {
            if (localTheory.none { it.matches(slot) }) return true
        }

        if (local.subject.selectedLabGroup != null) {
            val remoteLab = remote.labVariants[local.subject.selectedLabGroup] ?: return true
            val localLab = local.slots.filter { it.entryType == EntryType.LAB && it.labGroupName == local.subject.selectedLabGroup }
            
            if (localLab.size != remoteLab.size) return true
            for (slot in remoteLab) {
                if (localLab.none { it.matches(slot) }) return true
            }
        } else {
            // If no group selected, check if variants changed
            if (local.slots.filter { it.entryType == EntryType.LAB }.isNotEmpty() && remote.labVariants.isEmpty()) return true
            if (local.slots.filter { it.entryType == EntryType.LAB }.isEmpty() && remote.labVariants.isNotEmpty()) return true
            // Could do more thorough check here, but this is a good start
        }

        return false
    }

    private fun ClassSlot.matches(json: JsonTimeSlot): Boolean {
        return dayOfWeek.name == json.dayOfWeek &&
                startTime.toString() == json.startTime &&
                endTime.toString() == json.endTime &&
                classroom == json.classroom &&
                professor == json.professor
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showNotification() {
        createNotificationChannel()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh) // Assuming ic_refresh exists
            .setContentTitle(appContext.getString(R.string.schedule_update_notification_title))
            .setContentText(appContext.getString(R.string.schedule_update_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(appContext)) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notify(1001, builder.build())
                }
            } catch (_: SecurityException) {
                // Ignore
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = appContext.getString(R.string.schedule_update_notification_channel_name)
            val descriptionText = appContext.getString(R.string.auto_update_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
