package com.marcoslorcar.clementime.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleWidgetLogicTest {

    @Test
    fun testCalculateNextMidnightMillis() {
        val zone = ZoneId.of("Europe/Madrid")
        val current = ZonedDateTime.of(2026, 9, 13, 14, 30, 0, 0, zone)
        val nextMidnightMillis = ScheduleWidgetUtils.calculateNextMidnightMillis(current)

        val expectedNextMidnight = ZonedDateTime.of(2026, 9, 14, 0, 0, 1, 0, zone)
        val expectedMillis = expectedNextMidnight.toInstant().toEpochMilli()

        assertEquals(expectedMillis, nextMidnightMillis)
        assertTrue(nextMidnightMillis > current.toInstant().toEpochMilli())

        // Test just before midnight
        val justBeforeMidnight = ZonedDateTime.of(2026, 9, 13, 23, 59, 59, 0, zone)
        val triggerJustBefore = ScheduleWidgetUtils.calculateNextMidnightMillis(justBeforeMidnight)
        val expectedJustBefore = ZonedDateTime.of(2026, 9, 14, 0, 0, 1, 0, zone)
        assertEquals(expectedJustBefore.toInstant().toEpochMilli(), triggerJustBefore)
        assertEquals(2000L, triggerJustBefore - justBeforeMidnight.toInstant().toEpochMilli())
    }

    @Test
    fun testResolveEffectiveDayOffset_sameDayPreservesOffset() {
        val today = "2026-09-13"
        val effective = resolveEffectiveDayOffset(
            savedOffset = 1,
            lastNavigatedDate = today,
            todayDate = today
        )
        assertEquals(1, effective)
    }

    @Test
    fun testResolveEffectiveDayOffset_differentDayResetsToZero() {
        val yesterday = "2026-09-12"
        val today = "2026-09-13"
        val effective = resolveEffectiveDayOffset(
            savedOffset = 1,
            lastNavigatedDate = yesterday,
            todayDate = today
        )
        assertEquals(0, effective)
    }

    @Test
    fun testResolveEffectiveDayOffset_nullDateResetsToZero() {
        val today = "2026-09-13"
        val effective = resolveEffectiveDayOffset(
            savedOffset = 2,
            lastNavigatedDate = null,
            todayDate = today
        )
        assertEquals(0, effective)
    }

    @Test
    fun testCalculateNewDayOffset_sameDayNavigation() {
        val today = "2026-09-13"

        // Forward step
        val next = calculateNewDayOffset(
            currentOffset = 0,
            lastNavigatedDate = today,
            todayDate = today,
            direction = 1
        )
        assertEquals(1, next)

        // Backward step wraps around 5 weekdays (0 -> 4)
        val prev = calculateNewDayOffset(
            currentOffset = 0,
            lastNavigatedDate = today,
            todayDate = today,
            direction = -1
        )
        assertEquals(4, prev)

        // Forward cycling from last day (4 -> 0)
        val wrapForward = calculateNewDayOffset(
            currentOffset = 4,
            lastNavigatedDate = today,
            todayDate = today,
            direction = 1
        )
        assertEquals(0, wrapForward)
    }

    @Test
    fun testCalculateNewDayOffset_newDayResetsBaseToZero() {
        val yesterday = "2026-09-12"
        val today = "2026-09-13"

        // User was on offset 2 yesterday. Today they tap forward: should start from base 0 -> 1
        val forwardOnNewDay = calculateNewDayOffset(
            currentOffset = 2,
            lastNavigatedDate = yesterday,
            todayDate = today,
            direction = 1
        )
        assertEquals(1, forwardOnNewDay)

        // User was on offset 2 yesterday. Today they tap backward: should start from base 0 -> 4
        val backwardOnNewDay = calculateNewDayOffset(
            currentOffset = 2,
            lastNavigatedDate = yesterday,
            todayDate = today,
            direction = -1
        )
        assertEquals(4, backwardOnNewDay)
    }
}
