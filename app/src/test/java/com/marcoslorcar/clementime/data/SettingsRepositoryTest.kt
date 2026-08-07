package com.marcoslorcar.clementime.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaultAutoUpdateIntervalMinutes_returns360() = runTest {
        val repository = SettingsRepository(null)
        val minutes = repository.autoUpdateIntervalMinutesFlow.first()
        assertEquals(360, minutes)
    }

    @Test
    fun defaultAutoUpdateIntervalHours_returns6() = runTest {
        val repository = SettingsRepository(null)
        val hours = repository.autoUpdateIntervalHoursFlow.first()
        assertEquals(6, hours)
    }
}
