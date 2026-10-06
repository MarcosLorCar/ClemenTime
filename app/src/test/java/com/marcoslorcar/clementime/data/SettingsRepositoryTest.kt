package com.marcoslorcar.clementime.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun defaultSyncModeAndNotifyEnrolledOnly_fallbacksToDefaults() = runTest {
        val repository = SettingsRepository(null)
        val syncMode = repository.syncModeFlow.first()
        val notifyEnrolledOnly = repository.notifyEnrolledOnlyFlow.first()
        assertEquals(SyncMode.ONLINE, syncMode)
        assertEquals(true, notifyEnrolledOnly)
    }
}

