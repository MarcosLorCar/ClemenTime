package com.marcoslorcar.clementime.worker

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.messaging.FirebaseMessaging
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SyncMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates school-scoped FCM topic subscriptions and non-GMS / failure WorkManager fallback.
 */
object FcmTopicManager {

    private const val TAG = "FcmTopicManager"
    const val PERIODIC_FALLBACK_INTERVAL_MINUTES = 360
    private val INVALID_TOPIC_CHAR_REGEX = Regex("[^a-z0-9_-]")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Builds the canonical FCM topic name matching `schedules/script/notify_fcm.py`.
     * Returns `null` if neither a valid custom prefix nor a non-blank `(universityId, facultyId)` pair is provided.
     */
    fun topicFor(
        universityId: String,
        facultyId: String,
        fcmTopicPrefix: String? = null
    ): String? {
        val customPrefix = fcmTopicPrefix
            ?.trim()
            ?.lowercase()
            ?.removePrefix("sched_")
            ?.replace(INVALID_TOPIC_CHAR_REGEX, "_")
            ?.takeIf { it.isNotBlank() }
        if (customPrefix != null) {
            return "sched_$customPrefix"
        }
        val uni = universityId.trim().lowercase().replace(INVALID_TOPIC_CHAR_REGEX, "_")
        val fac = facultyId.trim().lowercase().replace(INVALID_TOPIC_CHAR_REGEX, "_")
        if (uni.isBlank() || fac.isBlank()) {
            return null
        }
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
    ) {
        val appContext = context.applicationContext ?: context
        syncRegistrationInternal(
            settingsRepository = settingsRepository,
            isGmsAvailable = { isGooglePlayServicesAvailable(appContext) },
            onUnsubscribe = { topic ->
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            },
            onSetAutoInitEnabled = { enabled ->
                FirebaseMessaging.getInstance().isAutoInitEnabled = enabled
            },
            onSubscribe = { topic, onSuccess, onFailure ->
                FirebaseMessaging.getInstance().subscribeToTopic(topic)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error -> onFailure(error) }
                    .addOnCanceledListener { onFailure(null) }
            },
            onEnsurePeriodicFallback = {
                ScheduleUpdateWorker.ensurePeriodicWorkScheduled(appContext, PERIODIC_FALLBACK_INTERVAL_MINUTES)
            },
            onCancelPeriodicFallback = {
                ScheduleUpdateWorker.cancelPeriodicWork(appContext)
            },
            callbackScope = scope
        )
    }

    internal suspend fun syncRegistrationInternal(
        settingsRepository: SettingsRepository,
        isGmsAvailable: () -> Boolean,
        onUnsubscribe: (String) -> Unit,
        onSetAutoInitEnabled: (Boolean) -> Unit,
        onSubscribe: (topic: String, onSuccess: () -> Unit, onFailure: (Throwable?) -> Unit) -> Unit,
        onEnsurePeriodicFallback: () -> Unit,
        onCancelPeriodicFallback: () -> Unit,
        callbackScope: CoroutineScope = scope,
        onLogWarning: (String, Throwable?) -> Unit = { msg, err -> Log.w(TAG, msg, err) }
    ) = withContext(Dispatchers.IO) {
        val syncMode = settingsRepository.syncModeFlow.first()
        val universityId = settingsRepository.selectedUniversityIdFlow.first()
        val facultyId = settingsRepository.selectedFacultyIdFlow.first()
        val fcmTopicPrefix = settingsRepository.selectedFcmTopicPrefixFlow.first()
        val previousTopic = settingsRepository.subscribedFcmTopicFlow.first()
        val targetTopic = topicFor(universityId, facultyId, fcmTopicPrefix)

        if (syncMode == SyncMode.OFFLINE || facultyId.isBlank() || targetTopic == null) {
            if (isGmsAvailable()) {
                try {
                    if (!previousTopic.isNullOrBlank()) {
                        onUnsubscribe(previousTopic)
                    }
                    if (syncMode == SyncMode.OFFLINE) {
                        onSetAutoInitEnabled(false)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onLogWarning("Failed to update FCM registration in offline/unlinked state", e)
                }
            }
            if (!previousTopic.isNullOrBlank()) {
                settingsRepository.setSubscribedFcmTopic("")
            }
            onCancelPeriodicFallback()
            return@withContext
        }

        // Online mode: prefer FCM topic push when GMS is available; fall back to periodic
        // WorkManager on de-Googled devices without Google Play Services or when subscription fails.
        if (!isGmsAvailable()) {
            onEnsurePeriodicFallback()
            return@withContext
        }

        try {
            onSetAutoInitEnabled(true)

            if (!previousTopic.isNullOrBlank() && previousTopic != targetTopic) {
                onUnsubscribe(previousTopic)
            }

            onSubscribe(
                targetTopic,
                {
                    onCancelPeriodicFallback()
                    callbackScope.launch {
                        try {
                            settingsRepository.setSubscribedFcmTopic(targetTopic)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            onLogWarning("Failed to persist subscribed FCM topic", e)
                        }
                    }
                },
                { error ->
                    if (error != null) {
                        onLogWarning("FCM topic subscription failed for $targetTopic; enabling periodic fallback", error)
                    }
                    onEnsurePeriodicFallback()
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onLogWarning("FCM registration threw synchronously; enabling periodic fallback", e)
            onEnsurePeriodicFallback()
        }
    }
}
