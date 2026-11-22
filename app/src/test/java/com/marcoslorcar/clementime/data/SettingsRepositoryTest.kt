package com.marcoslorcar.clementime.data

import com.marcoslorcar.clementime.worker.FcmTopicManager
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
    fun defaultUniversityAndFaculty_emitsEmptyOnFreshInstall() = runTest {
        val repository = SettingsRepository(null)
        val uni = repository.selectedUniversityIdFlow.first()
        val faculty = repository.selectedFacultyIdFlow.first()
        val uniName = repository.selectedUniversityNameFlow.first()
        val facultyName = repository.selectedFacultyNameFlow.first()
        val prefix = repository.selectedFcmTopicPrefixFlow.first()
        assertEquals("", uni)
        assertEquals("", faculty)
        assertEquals("", uniName)
        assertEquals("", facultyName)
        assertEquals("", prefix)
    }

    @Test
    fun resolveSelectedUniversityAndFaculty_migratesLegacyHashesOrDefaultsEmpty() {
        // Fresh install: stored ID null/blank and no legacy hashes -> ""
        assertEquals("", SettingsRepository.resolveSelectedUniversityId(null, null, null))
        assertEquals("", SettingsRepository.resolveSelectedUniversityId("", "", ""))
        assertEquals("", SettingsRepository.resolveSelectedFacultyId(null, null, null))
        assertEquals("", SettingsRepository.resolveSelectedFacultyId("", "", ""))

        // Legacy user with saved hash 1 or 2 -> "uclm" / "esi"
        assertEquals(
            SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID,
            SettingsRepository.resolveSelectedUniversityId(null, "abc123", null)
        )
        assertEquals(
            SettingsRepository.LEGACY_FALLBACK_FACULTY_ID,
            SettingsRepository.resolveSelectedFacultyId(null, null, "def456")
        )

        // Explicit stored ID wins even if legacy hashes exist
        assertEquals(
            "upm",
            SettingsRepository.resolveSelectedUniversityId("upm", "abc123", "def456")
        )
        assertEquals(
            "etsiinf",
            SettingsRepository.resolveSelectedFacultyId("etsiinf", "abc123", "def456")
        )
    }

    @Test
    fun fcmTopicManager_topicFor_handlesCustomPrefixAndFallbacks() {
        assertEquals(
            "sched_uclm_esi",
            FcmTopicManager.topicFor("uclm", "esi")
        )
        assertEquals(
            "sched_uclm_medicina_cr",
            FcmTopicManager.topicFor("UCLM", "Medicina CR", "uclm_medicina_cr")
        )
        assertEquals(
            "sched_upm_etsiinf",
            FcmTopicManager.topicFor("upm", "etsiinf", "sched_UPM ETSiInf")
        )
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
