package com.marcoslorcar.clementime.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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

    @Test
    fun testResolveWidgetDayPillText_onSaturday_displaysDayNameWithoutTomorrow() {
        // On Saturday, offset 0 shows Monday (baseDate is Monday).
        // Monday is NOT tomorrow on Saturday; it should display "Mon".
        val saturday = LocalDate.of(2026, 10, 10)
        val monday = getWeekdayDate(saturday, 0)
        assertEquals(LocalDate.of(2026, 10, 12), monday)

        val pillText = resolveWidgetDayPillText(
            targetDate = monday,
            todayDate = saturday,
            dayName = "Mon",
            todayFormat = "Today • %s",
            tomorrowFormat = "Tomorrow • %s"
        )
        assertEquals("Mon", pillText)
    }

    @Test
    fun testResolveWidgetDayPillText_onSunday_displaysTomorrowMon() {
        // On Sunday, offset 0 shows Monday.
        // Monday IS tomorrow on Sunday; it should display "Tomorrow • Mon".
        val sunday = LocalDate.of(2026, 10, 11)
        val monday = getWeekdayDate(sunday, 0)
        assertEquals(LocalDate.of(2026, 10, 12), monday)

        val pillText = resolveWidgetDayPillText(
            targetDate = monday,
            todayDate = sunday,
            dayName = "Mon",
            todayFormat = "Today • %s",
            tomorrowFormat = "Tomorrow • %s"
        )
        assertEquals("Tomorrow • Mon", pillText)
    }

    @Test
    fun testResolveWidgetDayPillText_onFriday_displaysTodayFri() {
        val friday = LocalDate.of(2026, 10, 9)
        val target = getWeekdayDate(friday, 0)
        assertEquals(friday, target)

        val pillText = resolveWidgetDayPillText(
            targetDate = target,
            todayDate = friday,
            dayName = "Fri",
            todayFormat = "Today • %s",
            tomorrowFormat = "Tomorrow • %s"
        )
        assertEquals("Today • Fri", pillText)
    }

    @Test
    fun testResolveWidgetDayPillText_onFriday_navigatingToMonday_displaysMonWithoutTomorrow() {
        val friday = LocalDate.of(2026, 10, 9)
        val monday = getWeekdayDate(friday, 1)
        assertEquals(LocalDate.of(2026, 10, 12), monday)

        val pillText = resolveWidgetDayPillText(
            targetDate = monday,
            todayDate = friday,
            dayName = "Mon",
            todayFormat = "Today • %s",
            tomorrowFormat = "Tomorrow • %s"
        )
        assertEquals("Mon", pillText)
    }

    @Test
    fun testResolveWidgetDayPillText_midweekTomorrow() {
        val monday = LocalDate.of(2026, 10, 5)
        val tuesday = getWeekdayDate(monday, 1)
        assertEquals(LocalDate.of(2026, 10, 6), tuesday)

        val pillText = resolveWidgetDayPillText(
            targetDate = tuesday,
            todayDate = monday,
            dayName = "Tue",
            todayFormat = "Today • %s",
            tomorrowFormat = "Tomorrow • %s"
        )
        assertEquals("Tomorrow • Tue", pillText)
    }

    @Test
    fun testResolveWidgetForwardBtnText_onFriday_displaysMonArrow() {
        val friday = LocalDate.of(2026, 10, 9)
        val nextWeekday = stepWeekday(friday, 1)
        assertEquals(LocalDate.of(2026, 10, 12), nextWeekday)

        val btnText = resolveWidgetForwardBtnText(
            todayDate = friday,
            nextWeekday = nextWeekday,
            nextDayName = "Mon",
            tomorrowText = "Tomorrow →"
        )
        assertEquals("Mon →", btnText)
    }

    @Test
    fun testResolveWidgetForwardBtnText_midweek_displaysTomorrowArrow() {
        val monday = LocalDate.of(2026, 10, 5)
        val nextWeekday = stepWeekday(monday, 1)
        assertEquals(LocalDate.of(2026, 10, 6), nextWeekday)

        val btnText = resolveWidgetForwardBtnText(
            todayDate = monday,
            nextWeekday = nextWeekday,
            nextDayName = "Tue",
            tomorrowText = "Tomorrow →"
        )
        assertEquals("Tomorrow →", btnText)
    }
}
