package com.marcoslorcar.clementime.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SlotTimeBoundsTest {

    private val dayStart: LocalTime = LocalTime.of(8, 30)
    private val dayEnd: LocalTime = LocalTime.of(21, 30)

    @Test
    fun `time inside the range is accepted`() {
        assertTrue(SlotTimeBounds.isWithinRange(LocalTime.of(12, 0), dayStart, dayEnd))
    }

    @Test
    fun `both boundaries are inclusive`() {
        assertTrue(SlotTimeBounds.isWithinRange(dayStart, dayStart, dayEnd))
        assertTrue(SlotTimeBounds.isWithinRange(dayEnd, dayStart, dayEnd))
    }

    @Test
    fun `time before the range is rejected`() {
        assertFalse(SlotTimeBounds.isWithinRange(LocalTime.of(8, 29), dayStart, dayEnd))
        assertFalse(SlotTimeBounds.isWithinRange(LocalTime.of(7, 0), dayStart, dayEnd))
    }

    @Test
    fun `time after the range is rejected`() {
        assertFalse(SlotTimeBounds.isWithinRange(LocalTime.of(21, 31), dayStart, dayEnd))
        assertFalse(SlotTimeBounds.isWithinRange(LocalTime.of(23, 0), dayStart, dayEnd))
    }

    @Test
    fun `clamp pulls out of range values to the nearest bound`() {
        assertEquals(dayStart, SlotTimeBounds.clampToRange(LocalTime.of(6, 0), dayStart, dayEnd))
        assertEquals(dayEnd, SlotTimeBounds.clampToRange(LocalTime.of(23, 0), dayStart, dayEnd))
    }

    @Test
    fun `clamp leaves in range values untouched`() {
        val time = LocalTime.of(15, 0)
        assertEquals(time, SlotTimeBounds.clampToRange(time, dayStart, dayEnd))
    }

    @Test
    fun `derived end time uses the default duration when it fits`() {
        assertEquals(
            LocalTime.of(10, 30),
            SlotTimeBounds.deriveEndTime(LocalTime.of(9, 0), 90, dayEnd)
        )
    }

    @Test
    fun `derived end time is shortened rather than overflowing the day`() {
        assertEquals(
            dayEnd,
            SlotTimeBounds.deriveEndTime(LocalTime.of(21, 0), 90, dayEnd)
        )
    }

    /** plusMinutes wraps past midnight; a wrap must not produce an end before the start. */
    @Test
    fun `derived end time does not wrap past midnight`() {
        val end = SlotTimeBounds.deriveEndTime(LocalTime.of(23, 30), 90, LocalTime.of(23, 59))
        assertEquals(LocalTime.of(23, 59), end)
    }

    @Test
    fun `derived end time is null when start is at or after the day end`() {
        assertNull(SlotTimeBounds.deriveEndTime(dayEnd, 90, dayEnd))
        assertNull(SlotTimeBounds.deriveEndTime(LocalTime.of(22, 0), 90, dayEnd))
    }

    @Test
    fun `derived start time uses the default duration when it fits`() {
        assertEquals(
            LocalTime.of(9, 0),
            SlotTimeBounds.deriveStartTime(LocalTime.of(10, 30), 90, dayStart)
        )
    }

    @Test
    fun `derived start time is not earlier than the day start`() {
        assertEquals(
            dayStart,
            SlotTimeBounds.deriveStartTime(LocalTime.of(9, 0), 90, dayStart)
        )
    }

    /** minusMinutes wraps past midnight for an early end time. */
    @Test
    fun `derived start time does not wrap past midnight`() {
        val start = SlotTimeBounds.deriveStartTime(LocalTime.of(0, 30), 90, LocalTime.of(0, 0))
        assertEquals(LocalTime.of(0, 0), start)
    }

    @Test
    fun `derived start time is null when end is at or before the day start`() {
        assertNull(SlotTimeBounds.deriveStartTime(dayStart, 90, dayStart))
        assertNull(SlotTimeBounds.deriveStartTime(LocalTime.of(7, 0), 90, dayStart))
    }
}
