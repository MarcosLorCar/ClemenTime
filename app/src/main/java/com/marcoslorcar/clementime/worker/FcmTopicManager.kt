package com.marcoslorcar.clementime.worker

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.messaging.FirebaseMessaging
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates school-scoped FCM topic subscriptions and non-GMS WorkManager fallback.
 */
object FcmTopicManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun topicFor(universityId: String, facultyId: String): String {
        val uni = universityId.trim().lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
            .ifBlank { SettingsRepository.DEFAULT_UNIVERSITY_ID }
        val fac = facultyId.trim().lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
            .ifBlank { SettingsRepository.DEFAULT_FACULTY_ID }
        return "sched_${uni}_${fac}"
    }

    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return runCatching {
            GoogleApiAvailabilityLight.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    suspend fun syncRegistration(
        context: Context,
        settingsRepository: SettingsRepository
    ) = withContext(Dispatchers.IO) {
        val syncMode = settingsRepository.syncModeFlow.first()
        val universityId = settingsRepository.selectedUniversityIdFlow.first()
        val facultyId = settingsRepository.selectedFacultyIdFlow.first()
        val previousTopic = settingsRepository.subscribedFcmTopicFlow.first()
        val targetTopic = topicFor(universityId, facultyId)

        if (syncMode == SyncMode.OFFLINE) {
            ScheduleUpdateWorker.cancelPeriodicWork(context)
            runCatching {
                val messaging = FirebaseMessaging.getInstance()
                if (!previousTopic.isNullOrBlank()) {
                    messaging.unsubscribeFromTopic(previousTopic)
                }
                messaging.unsubscribeFromTopic(targetTopic)
                messaging.isAutoInitEnabled = false
            }
            settingsRepository.setSubscribedFcmTopic(null)
            return@withContext
        }

        // Online mode: prefer FCM topic push when GMS is available; fall back to periodic
        // WorkManager only on de-Googled devices without Google Play Services.
        if (!isGooglePlayServicesAvailable(context)) {
            ScheduleUpdateWorker.ensurePeriodicWorkScheduled(context, 360)
            return@withContext
        }

        val registered = runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true

            if (!previousTopic.isNullOrBlank() && previousTopic != targetTopic) {
                messaging.unsubscribeFromTopic(previousTopic)
            }

            messaging.subscribeToTopic(targetTopic)
                .addOnSuccessListener {
                    scope.launch {
                        settingsRepository.setSubscribedFcmTopic(targetTopic)
                    }
                }
                .addOnFailureListener {
                    if (!isGooglePlayServicesAvailable(context)) {
                        ScheduleUpdateWorker.ensurePeriodicWorkScheduled(context, 360)
                    }
                }
        }

        if (registered.isSuccess) {
            ScheduleUpdateWorker.cancelPeriodicWork(context)
        } else {
            ScheduleUpdateWorker.ensurePeriodicWorkScheduled(context, 360)
        }
    }
}
