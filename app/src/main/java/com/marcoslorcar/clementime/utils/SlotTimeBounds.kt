package com.marcoslorcar.clementime.utils

import java.time.LocalTime

/**
 * Validation for manually entered class slot times against the user's configured visible day
 * range (`SettingsRepository.dayStartHour/Minute` .. `dayEndHour/Minute`).
 *
 * Pure Kotlin with no Android dependencies so it stays unit-testable, in the same spirit as
 * [ConflictSolver].
 *
 * Policy: only times the user *newly picks* are validated. A slot that already sits outside
 * the range - because the range was narrowed after the slot was created - stays editable and
 * saveable, so its classroom or professor can still be changed. Callers therefore validate a
 * freshly selected [LocalTime] rather than the slot as a whole.
 */
object SlotTimeBounds {

    /** True when [time] falls within [dayStartTime]..[dayEndTime], inclusive of both ends. */
    fun isWithinRange(
        time: LocalTime,
        dayStartTime: LocalTime,
        dayEndTime: LocalTime
    ): Boolean = !time.isBefore(dayStartTime) && !time.isAfter(dayEndTime)

    /**
     * Clamps [time] into [dayStartTime]..[dayEndTime]. Used for a time the app *derives*
     * (such as the counterpart end time auto-filled from a chosen start), never for a value
     * the user picked explicitly - those are rejected instead, so the choice is not silently
     * changed.
     */
    fun clampToRange(
        time: LocalTime,
        dayStartTime: LocalTime,
        dayEndTime: LocalTime
    ): LocalTime = when {
        time.isBefore(dayStartTime) -> dayStartTime
        time.isAfter(dayEndTime) -> dayEndTime
        else -> time
    }

    /**
     * Derives the end time for a newly chosen [start], preferring [durationMinutes] but
     * shortening rather than overflowing past [dayEndTime]. Returns null when [start] is at
     * or after [dayEndTime], leaving the caller to reject the selection.
     */
    fun deriveEndTime(
        start: LocalTime,
        durationMinutes: Int,
        dayEndTime: LocalTime
    ): LocalTime? {
        if (!start.isBefore(dayEndTime)) return null
        val naive = start.plusMinutes(durationMinutes.toLong())
        // plusMinutes wraps around midnight; treat a wrap as overflowing the day.
        return if (naive.isAfter(start) && !naive.isAfter(dayEndTime)) naive else dayEndTime
    }

    /**
     * Derives the start time for a newly chosen [end], preferring [durationMinutes] but not
     * running earlier than [dayStartTime]. Returns null when [end] is at or before
     * [dayStartTime].
     */
    fun deriveStartTime(
        end: LocalTime,
        durationMinutes: Int,
        dayStartTime: LocalTime
    ): LocalTime? {
        if (!end.isAfter(dayStartTime)) return null
        val naive = end.minusMinutes(durationMinutes.toLong())
        // minusMinutes wraps around midnight for early end times.
        return if (naive.isBefore(end) && !naive.isBefore(dayStartTime)) naive else dayStartTime
    }
}
