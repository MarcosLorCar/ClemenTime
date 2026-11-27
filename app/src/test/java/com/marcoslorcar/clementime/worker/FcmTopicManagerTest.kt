package com.marcoslorcar.clementime.worker

import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SyncMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FcmTopicManagerTest {

    @Test
    fun topicFor_matchesPythonSanitizationAndReturnsNullOnBlankIds() {
        assertEquals("sched_uclm_esi", FcmTopicManager.topicFor("uclm", "esi"))
        assertEquals("sched_u_c_l_m_e_s_i", FcmTopicManager.topicFor("u.c.l.m", "e.s.i"))
        assertEquals("sched_upm_etsiinf", FcmTopicManager.topicFor("upm", "etsiinf", "sched_UPM ETSiInf"))
        assertEquals("sched_upm_etsiinf", FcmTopicManager.topicFor("upm", "etsiinf", "SCHED_upm.etsiinf"))
        assertNull(FcmTopicManager.topicFor("", "esi"))
        assertNull(FcmTopicManager.topicFor("uclm", "   "))
        assertNull(FcmTopicManager.topicFor("", "", "sched_"))
    }

    @Test
    fun syncRegistration_cancelsPeriodicWorkOnlyAfterAsyncSuccess() = runTest {
        val settings = FakeFcmSettingsRepository(
            syncMode = SyncMode.ONLINE,
            universityId = "uclm",
            facultyId = "esi"
        )
        var capturedOnSuccess: (() -> Unit)? = null
        var cancelCount = 0
        var ensureCount = 0

        FcmTopicManager.syncRegistrationInternal(
            settingsRepository = settings,
            isGmsAvailable = { true },
            onUnsubscribe = {},
            onSetAutoInitEnabled = {},
            onSubscribe = { _, onSuccess, _ -> capturedOnSuccess = onSuccess },
            onEnsurePeriodicFallback = { ensureCount++ },
            onCancelPeriodicFallback = { cancelCount++ },
            callbackScope = this,
            onLogWarning = { _, _ -> }
        )

        // Before subscribeToTopic completes, periodic fallback must NOT be canceled yet.
        assertEquals(0, cancelCount)
        assertEquals(0, ensureCount)
        assertNull(settings.subscribedTopic.value)

        capturedOnSuccess?.invoke()
        advanceUntilIdle()

        assertEquals(1, cancelCount)
        assertEquals(0, ensureCount)
        assertEquals("sched_uclm_esi", settings.subscribedTopic.value)
    }

    @Test
    fun syncRegistration_schedulesPeriodicFallbackOnAsyncFailureEvenWhenGmsAvailable() = runTest {
        val settings = FakeFcmSettingsRepository(
            syncMode = SyncMode.ONLINE,
            universityId = "uclm",
            facultyId = "esi"
        )
        var capturedOnFailure: ((Throwable?) -> Unit)? = null
        var cancelCount = 0
        var ensureCount = 0

        FcmTopicManager.syncRegistrationInternal(
            settingsRepository = settings,
            isGmsAvailable = { true },
            onUnsubscribe = {},
            onSetAutoInitEnabled = {},
            onSubscribe = { _, _, onFailure -> capturedOnFailure = onFailure },
            onEnsurePeriodicFallback = { ensureCount++ },
            onCancelPeriodicFallback = { cancelCount++ },
            callbackScope = this,
            onLogWarning = { _, _ -> }
        )

        capturedOnFailure?.invoke(IllegalStateException("Transient FIS error"))
        advanceUntilIdle()

        assertEquals(0, cancelCount)
        assertEquals(1, ensureCount)
        assertNull(settings.subscribedTopic.value)
    }

    @Test
    fun syncRegistration_schedulesPeriodicFallbackWhenSubscribeThrowsSynchronously() = runTest {
        val settings = FakeFcmSettingsRepository(
            syncMode = SyncMode.ONLINE,
            universityId = "uclm",
            facultyId = "esi"
        )
        var cancelCount = 0
        var ensureCount = 0

        FcmTopicManager.syncRegistrationInternal(
            settingsRepository = settings,
            isGmsAvailable = { true },
            onUnsubscribe = {},
            onSetAutoInitEnabled = {},
            onSubscribe = { _, _, _ -> throw IllegalStateException("FirebaseApp not initialized") },
            onEnsurePeriodicFallback = { ensureCount++ },
            onCancelPeriodicFallback = { cancelCount++ },
            callbackScope = this,
            onLogWarning = { _, _ -> }
        )

        assertEquals(0, cancelCount)
        assertEquals(1, ensureCount)
    }

    @Test
    fun syncRegistration_offlineModeUnsubscribesDisablesAutoInitAndCancelsPeriodic() = runTest {
        val settings = FakeFcmSettingsRepository(
            syncMode = SyncMode.OFFLINE,
            universityId = "uclm",
            facultyId = "esi",
            initialSubscribedTopic = "sched_uclm_esi"
        )
        val unsubscribed = mutableListOf<String>()
        var autoInitValue: Boolean? = null
        var cancelCount = 0
        var subscribeCalled = false

        FcmTopicManager.syncRegistrationInternal(
            settingsRepository = settings,
            isGmsAvailable = { true },
            onUnsubscribe = { unsubscribed.add(it) },
            onSetAutoInitEnabled = { autoInitValue = it },
            onSubscribe = { _, _, _ -> subscribeCalled = true },
            onEnsurePeriodicFallback = {},
            onCancelPeriodicFallback = { cancelCount++ },
            callbackScope = this,
            onLogWarning = { _, _ -> }
        )

        assertEquals(listOf("sched_uclm_esi"), unsubscribed)
        assertEquals(false, autoInitValue)
        assertFalse(subscribeCalled)
        assertEquals(1, cancelCount)
        assertEquals("", settings.subscribedTopic.value)
    }

    @Test
    fun syncRegistration_withoutGmsSchedulesPeriodicFallbackWithoutFirebase() = runTest {
        val settings = FakeFcmSettingsRepository(
            syncMode = SyncMode.ONLINE,
            universityId = "uclm",
            facultyId = "esi"
        )
        var ensureCount = 0
        var subscribeCalled = false

        FcmTopicManager.syncRegistrationInternal(
            settingsRepository = settings,
            isGmsAvailable = { false },
            onUnsubscribe = {},
            onSetAutoInitEnabled = {},
            onSubscribe = { _, _, _ -> subscribeCalled = true },
            onEnsurePeriodicFallback = { ensureCount++ },
            onCancelPeriodicFallback = {},
            callbackScope = this,
            onLogWarning = { _, _ -> }
        )

        assertFalse(subscribeCalled)
        assertEquals(1, ensureCount)
        assertTrue(settings.subscribedTopic.value.isNullOrBlank())
    }
}

private class FakeFcmSettingsRepository(
    syncMode: SyncMode = SyncMode.ONLINE,
    universityId: String = "",
    facultyId: String = "",
    fcmTopicPrefix: String = "",
    initialSubscribedTopic: String? = null
) : SettingsRepository(context = null) {
    val syncModeState = MutableStateFlow(syncMode)
    val uniIdState = MutableStateFlow(universityId)
    val facIdState = MutableStateFlow(facultyId)
    val topicPrefixState = MutableStateFlow(fcmTopicPrefix)
    val subscribedTopic = MutableStateFlow(initialSubscribedTopic)

    override val syncModeFlow: Flow<SyncMode> = syncModeState
    override val selectedUniversityIdFlow: Flow<String> = uniIdState
    override val selectedFacultyIdFlow: Flow<String> = facIdState
    override val selectedFcmTopicPrefixFlow: Flow<String> = topicPrefixState
    override val subscribedFcmTopicFlow: Flow<String?> = subscribedTopic

    override suspend fun setSubscribedFcmTopic(topic: String?) {
        subscribedTopic.value = topic
    }
}
