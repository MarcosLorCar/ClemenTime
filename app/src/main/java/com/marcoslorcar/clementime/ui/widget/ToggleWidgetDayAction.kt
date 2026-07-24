package com.marcoslorcar.clementime.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

val IS_TOMORROW_KEY = booleanPreferencesKey("is_tomorrow_selected")

class ToggleWidgetDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[IS_TOMORROW_KEY] ?: false
            prefs.toMutablePreferences().apply {
                this[IS_TOMORROW_KEY] = !current
            }
        }
        ScheduleWidget().updateAll(context)
    }
}
