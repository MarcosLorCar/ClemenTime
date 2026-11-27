package com.marcoslorcar.clementime.ui.model

import com.marcoslorcar.clementime.data.SettingsRepository
import kotlinx.coroutines.flow.combine
import java.time.LocalTime

internal data class ScheduleDisplaySettings(
    val scrollableTabs: Boolean,
    val showNowLine: Boolean,
    val nowLineStyle: String,
    val highContrast: Boolean
)

internal fun SettingsRepository.scheduleDisplaySettings() = combine(
    scrollableTabsFlow,
    showNowLineFlow,
    nowLineStyleFlow,
    highContrastFlow,
    ::ScheduleDisplaySettings
)

internal data class ScheduleHours(val start: LocalTime, val end: LocalTime)

internal fun SettingsRepository.scheduleHours() = combine(
    dayStartHourFlow,
    dayStartMinuteFlow,
    dayEndHourFlow,
    dayEndMinuteFlow
) { startHour, startMinute, endHour, endMinute ->
    ScheduleHours(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute))
}
