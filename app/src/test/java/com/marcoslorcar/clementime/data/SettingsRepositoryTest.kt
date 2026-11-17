package com.marcoslorcar.clementime.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaultAutoUpdateIntervalMinutes_isDisabled() = runTest {
        val repository = SettingsRepository(null)
        val minutes = repository.autoUpdateIntervalMinutesFlow.first()
        assertEquals(0, minutes)
    }

    @Test
    fun defaultUniversityAndFaculty_fallbacksToUclmAndEsi() = runTest {
        val repository = SettingsRepository(null)
        val uni = repository.selectedUniversityIdFlow.first()
        val faculty = repository.selectedFacultyIdFlow.first()
        assertEquals("uclm", uni)
        assertEquals("esi", faculty)
    }

    @Test
    fun defaultSyncModeAndFcmDefaults_fallbacksToDefaults() = runTest {
        val repository = SettingsRepository(null)
        val syncMode = repository.syncModeFlow.first()
        val topic = repository.subscribedFcmTopicFlow.first()
        val subjectHashes = repository.getLastRemoteSubjectHashesFlow(1).first()
        assertEquals(SyncMode.ONLINE, syncMode)
        assertNull(topic)
        assertTrue(subjectHashes.isEmpty())
    }

    @Test
    fun resolveLegacySyncMode_migratesV133UsersAccurately() {
        // Explicit sync_mode wins over legacy interval keys
        assertEquals(
            SyncMode.OFFLINE,
            SettingsRepository.resolveLegacySyncMode("OFFLINE", explicitMinutes = 360, explicitHours = 6)
        )
        assertEquals(
            SyncMode.ONLINE,
            SettingsRepository.resolveLegacySyncMode("ONLINE", explicitMinutes = 0, explicitHours = 0)
        )
        // Fresh or v1.3.3 default (never touched interval setting) -> ONLINE (zero-friction FCM)
        assertEquals(
            SyncMode.ONLINE,
            SettingsRepository.resolveLegacySyncMode(null, explicitMinutes = null, explicitHours = null)
        )
        // Legacy user with periodic polling enabled -> ONLINE
        assertEquals(
            SyncMode.ONLINE,
            SettingsRepository.resolveLegacySyncMode(null, explicitMinutes = 360, explicitHours = 6)
        )
        // Legacy user who explicitly turned auto-update off (0) -> OFFLINE
        assertEquals(
            SyncMode.OFFLINE,
            SettingsRepository.resolveLegacySyncMode(null, explicitMinutes = 0, explicitHours = 0)
        )
        assertEquals(
            SyncMode.OFFLINE,
            SettingsRepository.resolveLegacySyncMode(null, explicitMinutes = null, explicitHours = 0)
        )
    }
}
