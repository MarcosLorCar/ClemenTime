package com.marcoslorcar.clementime.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.time.LocalDate

val IS_TOMORROW_KEY = booleanPreferencesKey("is_tomorrow_selected")
val DAY_OFFSET_KEY = intPreferencesKey("day_offset")
val NAVIGATED_DATE_KEY = stringPreferencesKey("navigated_date")
val DIRECTION_KEY = ActionParameters.Key<Int>("direction")

fun calculateNewDayOffset(
    currentOffset: Int,
    lastNavigatedDate: String?,
    todayDate: String,
    direction: Int
): Int {
    val baseOffset = if (lastNavigatedDate == todayDate) currentOffset else 0
    var newOffset = (baseOffset + direction) % 5
    if (newOffset < 0) newOffset += 5
    return newOffset
}

fun resolveEffectiveDayOffset(
    savedOffset: Int,
    lastNavigatedDate: String?,
    todayDate: String
): Int {
    return if (lastNavigatedDate == todayDate) savedOffset else 0
}

class ResetWidgetDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val todayStr = LocalDate.now().toString()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[DAY_OFFSET_KEY] = 0
                this[IS_TOMORROW_KEY] = false
                this[NAVIGATED_DATE_KEY] = todayStr
            }
        }
        ScheduleWidget().update(context, glanceId)
    }
}

class NavigateWidgetDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val direction = parameters[DIRECTION_KEY] ?: 1
        val todayStr = LocalDate.now().toString()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val lastDate = prefs[NAVIGATED_DATE_KEY]
            val currentOffset = prefs[DAY_OFFSET_KEY] ?: (if (prefs[IS_TOMORROW_KEY] == true) 1 else 0)
            val newOffset = calculateNewDayOffset(
                currentOffset = currentOffset,
                lastNavigatedDate = lastDate,
                todayDate = todayStr,
                direction = direction
            )
            prefs.toMutablePreferences().apply {
                this[DAY_OFFSET_KEY] = newOffset
                this[IS_TOMORROW_KEY] = newOffset != 0
                this[NAVIGATED_DATE_KEY] = todayStr
            }
        }
        ScheduleWidget().update(context, glanceId)
    }
}
