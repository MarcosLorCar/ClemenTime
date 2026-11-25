package com.marcoslorcar.clementime.worker

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SyncMode
import com.marcoslorcar.clementime.data.TermCodeParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class ClemenTimeFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var scheduleDao: ScheduleDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTopicManager.syncRegistration(applicationContext, settingsRepository)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(10_000L) {
                if (settingsRepository.syncModeFlow.first() == SyncMode.OFFLINE) {
                    return@withTimeoutOrNull
                }

                val data = message.data
                val activeSemesters = scheduleDao.getAllSubjectsWithSlots().first()
                    .filter { it.subject.isActive }
                    .map { it.subject.semester }
                    .distinct()

                val shouldSync = activeSemesters.any { semester ->
                    val incomingHash = data.entries.firstOrNull { (key, _) ->
                        key.startsWith("hash_", ignoreCase = true) &&
                            TermCodeParser.parseTermIndex(key.substringAfter("_")) == semester
                    }?.value ?: run {
                        val payloadTerm = data["semester"] ?: data["term"]
                        if (TermCodeParser.parseTermIndex(payloadTerm) == semester) {
                            data["hash"]
                        } else {
                            null
                        }
                    }
                    val lastKnownHash = settingsRepository
                        .getLastKnownScheduleHashFlow(semester)
                        .first()
                    incomingHash.isNullOrBlank() || incomingHash != lastKnownHash
                }

                if (shouldSync) {
                    ScheduleUpdateWorker.enqueueOneTimeWork(applicationContext, expedited = true)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
