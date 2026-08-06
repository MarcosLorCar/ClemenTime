package com.marcoslorcar.clementime.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

val IS_TOMORROW_KEY = booleanPreferencesKey("is_tomorrow_selected")
val DAY_OFFSET_KEY = intPreferencesKey("day_offset")
val DIRECTION_KEY = ActionParameters.Key<Int>("direction")

class ResetWidgetDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[DAY_OFFSET_KEY] = 0
                this[IS_TOMORROW_KEY] = false
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
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val currentOffset = prefs[DAY_OFFSET_KEY] ?: (if (prefs[IS_TOMORROW_KEY] == true) 1 else 0)
            var newOffset = (currentOffset + direction) % 5
            if (newOffset < 0) newOffset += 5
            prefs.toMutablePreferences().apply {
                this[DAY_OFFSET_KEY] = newOffset
                this[IS_TOMORROW_KEY] = newOffset != 0
            }
        }
        ScheduleWidget().update(context, glanceId)
    }
}
