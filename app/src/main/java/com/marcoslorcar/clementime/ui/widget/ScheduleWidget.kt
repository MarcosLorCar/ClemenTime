package com.marcoslorcar.clementime.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.marcoslorcar.clementime.MainActivity
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleWidgetEntryPoint {
    fun scheduleDao(): ScheduleDao
    fun settingsRepository(): SettingsRepository
}

class ScheduleWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ScheduleWidgetEntryPoint::class.java
            )
        } catch (_: Throwable) {
            null
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val dayOffset = prefs[DAY_OFFSET_KEY] ?: (if (prefs[IS_TOMORROW_KEY] == true) 1 else 0)

            val subjectsWithSlots by remember(entryPoint) {
                entryPoint?.settingsRepository()?.currentSemesterFlow?.flatMapLatest { semester ->
                    entryPoint.scheduleDao().getActiveSubjectsWithSlotsBySemester(semester)
                } ?: kotlinx.coroutines.flow.flowOf(null)
            }.collectAsState(initial = null)

            val themeMode by remember(entryPoint) {
                entryPoint?.settingsRepository()?.themeFlow ?: kotlinx.coroutines.flow.flowOf("system")
            }.collectAsState(initial = "system")

            val selectedTheme by remember(entryPoint) {
                entryPoint?.settingsRepository()?.selectedThemeFlow ?: kotlinx.coroutines.flow.flowOf("clementine")
            }.collectAsState(initial = "clementine")

            val showNowLine by remember(entryPoint) {
                entryPoint?.settingsRepository()?.showNowLineFlow ?: kotlinx.coroutines.flow.flowOf(true)
            }.collectAsState(initial = true)

            val highContrast by remember(entryPoint) {
                entryPoint?.settingsRepository()?.highContrastFlow ?: kotlinx.coroutines.flow.flowOf(false)
            }.collectAsState(initial = false)

            val nowLineStyle by remember(entryPoint) {
                entryPoint?.settingsRepository()?.nowLineStyleFlow ?: kotlinx.coroutines.flow.flowOf("discrete")
            }.collectAsState(initial = "discrete")

            val dayStartTime by remember(entryPoint) {
                entryPoint?.settingsRepository()?.dayStartHourFlow?.let { hourFlow ->
                    combine(hourFlow, entryPoint.settingsRepository().dayStartMinuteFlow) { h, m ->
                        LocalTime.of(h, m)
                    }
                } ?: kotlinx.coroutines.flow.flowOf(LocalTime.of(8, 30))
            }.collectAsState(initial = LocalTime.of(8, 30))

            val dayEndTime by remember(entryPoint) {
                entryPoint?.settingsRepository()?.dayEndHourFlow?.let { hourFlow ->
                    combine(hourFlow, entryPoint.settingsRepository().dayEndMinuteFlow) { h, m ->
                        LocalTime.of(h, m)
                    }
                } ?: kotlinx.coroutines.flow.flowOf(LocalTime.of(21, 30))
            }.collectAsState(initial = LocalTime.of(21, 30))

            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> {
                    (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

            val launchAppAction = remember(context) {
                val intent = Intent().apply {
                    component = ComponentName(context, MainActivity::class.java)
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                actionStartActivity(intent)
            }

            GlanceTheme {
                if (entryPoint == null) {
                    WidgetErrorState()
                } else {
                    when (val data = subjectsWithSlots) {
                        null -> WidgetLoadingState()
                        else -> ScheduleWidgetContent(
                            dayOffset = dayOffset,
                            subjectsWithSlots = data,
                            showNowLine = showNowLine,
                            nowLineStyle = nowLineStyle,
                            highContrast = highContrast,
                            isDarkTheme = isDarkTheme,
                            selectedTheme = selectedTheme,
                            dayStartTime = dayStartTime,
                            dayEndTime = dayEndTime,
                            launchAppAction = launchAppAction
                        )
                    }
                }
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(ScheduleWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    val current = this[REFRESH_KEY] ?: 0
                    this[REFRESH_KEY] = current + 1
                    this[DAY_OFFSET_KEY] = 0
                    this[IS_TOMORROW_KEY] = false
                }
            }
            update(context, glanceId)
        }
    }
}

val REFRESH_KEY = androidx.datastore.preferences.core.intPreferencesKey("refresh_count")

