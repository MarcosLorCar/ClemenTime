package com.marcoslorcar.clementime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marcoslorcar.clementime.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
open class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context?
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val scrollableTabsKey = booleanPreferencesKey("scrollable_tabs")
    private val showNowLineKey = booleanPreferencesKey("show_now_line")
    private val nowLineStyleKey = stringPreferencesKey("now_line_style")
    private val highContrastKey = booleanPreferencesKey("high_contrast")
    private val selectedThemeKey = stringPreferencesKey("selected_theme")
    private val githubRepoBaseUrlKey = stringPreferencesKey("github_repo_base_url")
    private val isOnboardingCompletedKey = booleanPreferencesKey("is_onboarding_completed")
    private val onboardingTooltipsEnabledKey = booleanPreferencesKey("onboarding_tooltips_enabled")
    private val hasSeenImportConflictTooltipKey = booleanPreferencesKey("has_seen_import_conflict_tooltip")
    private val hasSeenOptimizerTooltipKey = booleanPreferencesKey("has_seen_optimizer_tooltip")
    private val hasSeenResolverPrioritiesTooltipKey = booleanPreferencesKey("has_seen_resolver_priorities_tooltip")
    private val hasSeenResolverApplyTooltipKey = booleanPreferencesKey("has_seen_resolver_apply_tooltip")
    private val hasSeenAddSlotTooltipKey = booleanPreferencesKey("has_seen_add_slot_tooltip")
    private val hasSeenImportPreviewTooltipKey = booleanPreferencesKey("has_seen_import_preview_tooltip")
    private val hasSeenLabSelectionTooltipKey = booleanPreferencesKey("has_seen_lab_selection_tooltip")
    private val currentSemesterKey = intPreferencesKey("current_semester")
    private val hasManuallyChangedSemesterKey = booleanPreferencesKey("has_manually_changed_semester")
    private val wasSemesterAutoChangedKey = booleanPreferencesKey("was_semester_auto_changed")
    private val dayStartHourKey = intPreferencesKey("day_start_hour")
    private val dayStartMinuteKey = intPreferencesKey("day_start_minute")
    private val dayEndHourKey = intPreferencesKey("day_end_hour")
    private val dayEndMinuteKey = intPreferencesKey("day_end_minute")
    private val autoUpdateIntervalHoursKey = intPreferencesKey("auto_update_interval_hours")
    private val autoUpdateIntervalMinutesKey = intPreferencesKey("auto_update_interval_minutes")
    private val lastScheduleSyncTimestampKey = longPreferencesKey("last_schedule_sync_timestamp")
    private fun lastKnownScheduleHashKey(semester: Int) = stringPreferencesKey("last_known_schedule_hash_$semester")
    private fun lastNotifiedScheduleHashKey(semester: Int) = stringPreferencesKey("last_notified_schedule_hash_$semester")


    open val themeFlow: Flow<String>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[themeKey] ?: "system"
            } ?: kotlinx.coroutines.flow.flowOf("system")
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf("system")
        }


    open val scrollableTabsFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[scrollableTabsKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val showNowLineFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[showNowLineKey] ?: true
            } ?: kotlinx.coroutines.flow.flowOf(true)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(true)
        }


    open val nowLineStyleFlow: Flow<String>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[nowLineStyleKey] ?: "discrete"
            } ?: kotlinx.coroutines.flow.flowOf("discrete")
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf("discrete")
        }


    open val highContrastFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[highContrastKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val selectedThemeFlow: Flow<String>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[selectedThemeKey] ?: "clementine"
            } ?: kotlinx.coroutines.flow.flowOf("clementine")
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf("clementine")
        }


    open val githubRepoBaseUrlFlow: Flow<String>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[githubRepoBaseUrlKey] ?: DEFAULT_GITHUB_REPO_BASE_URL
            } ?: kotlinx.coroutines.flow.flowOf(DEFAULT_GITHUB_REPO_BASE_URL)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(DEFAULT_GITHUB_REPO_BASE_URL)
        }


    open val isOnboardingCompletedFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[isOnboardingCompletedKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val onboardingTooltipsEnabledFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[onboardingTooltipsEnabledKey] ?: true
            } ?: kotlinx.coroutines.flow.flowOf(true)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(true)
        }


    open val hasSeenImportConflictTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenImportConflictTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val hasSeenOptimizerTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenOptimizerTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val hasSeenResolverPrioritiesTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenResolverPrioritiesTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val hasSeenResolverApplyTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenResolverApplyTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }


    open val hasSeenAddSlotTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenAddSlotTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }

    open val hasSeenImportPreviewTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenImportPreviewTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }

    open val hasSeenLabSelectionTooltipFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasSeenLabSelectionTooltipKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }

    open val currentSemesterFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[currentSemesterKey] ?: 1
            } ?: kotlinx.coroutines.flow.flowOf(1)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(1)
        }

    open val hasManuallyChangedSemesterFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[hasManuallyChangedSemesterKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }

    open val wasSemesterAutoChangedFlow: Flow<Boolean>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[wasSemesterAutoChangedKey] ?: false
            } ?: kotlinx.coroutines.flow.flowOf(false)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(false)
        }

    open val dayStartHourFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[dayStartHourKey] ?: 8
            } ?: kotlinx.coroutines.flow.flowOf(8)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(8)
        }

    open val dayStartMinuteFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[dayStartMinuteKey] ?: 30
            } ?: kotlinx.coroutines.flow.flowOf(30)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(30)
        }

    open val dayEndHourFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[dayEndHourKey] ?: 21
            } ?: kotlinx.coroutines.flow.flowOf(21)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(21)
        }

    open val dayEndMinuteFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[dayEndMinuteKey] ?: 30
            } ?: kotlinx.coroutines.flow.flowOf(30)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(30)
        }

    open val autoUpdateIntervalMinutesFlow: Flow<Int>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[autoUpdateIntervalMinutesKey]
                    ?: ((preferences[autoUpdateIntervalHoursKey] ?: DEFAULT_AUTO_UPDATE_INTERVAL_HOURS) * 60)
            } ?: kotlinx.coroutines.flow.flowOf(DEFAULT_AUTO_UPDATE_INTERVAL_HOURS * 60)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(DEFAULT_AUTO_UPDATE_INTERVAL_HOURS * 60)
        }

    open val autoUpdateIntervalHoursFlow: Flow<Int>
        get() = autoUpdateIntervalMinutesFlow.map { minutes ->
            if (minutes in 1..59) minutes else minutes / 60
        }

    open fun getLastKnownScheduleHashFlow(semester: Int): Flow<String> = try {
        context?.dataStore?.data?.map { preferences ->
            preferences[lastKnownScheduleHashKey(semester)] ?: ""
        } ?: kotlinx.coroutines.flow.flowOf("")
    } catch (_: Throwable) {
        kotlinx.coroutines.flow.flowOf("")
    }

    /**
     * The remote hash we have already posted a notification for. Kept separate from
     * [getLastKnownScheduleHashFlow], which records the version the user has *accepted* or
     * ignored: the diff sheet re-runs the sync to repopulate itself, so marking a version
     * "seen" on notify would leave the sheet empty when the user taps through.
     */
    open fun getLastNotifiedScheduleHashFlow(semester: Int): Flow<String> = try {
        context?.dataStore?.data?.map { preferences ->
            preferences[lastNotifiedScheduleHashKey(semester)] ?: ""
        } ?: kotlinx.coroutines.flow.flowOf("")
    } catch (_: Throwable) {
        kotlinx.coroutines.flow.flowOf("")
    }

    open val lastScheduleSyncTimestampFlow: Flow<Long>
        get() = try {
            context?.dataStore?.data?.map { preferences ->
                preferences[lastScheduleSyncTimestampKey] ?: 0L
            } ?: kotlinx.coroutines.flow.flowOf(0L)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(0L)
        }


    open suspend fun setThemeMode(theme: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[themeKey] = theme
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setGithubRepoBaseUrl(baseUrl: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[githubRepoBaseUrlKey] = baseUrl
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setScrollableTabs(scrollable: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[scrollableTabsKey] = scrollable
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setShowNowLine(show: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[showNowLineKey] = show
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setNowLineStyle(style: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[nowLineStyleKey] = style
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHighContrast(enabled: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[highContrastKey] = enabled
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setSelectedTheme(theme: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[selectedThemeKey] = theme
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setOnboardingCompleted(completed: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[isOnboardingCompletedKey] = completed
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setOnboardingTooltipsEnabled(enabled: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[onboardingTooltipsEnabledKey] = enabled
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHasSeenImportConflictTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenImportConflictTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHasSeenOptimizerTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenOptimizerTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHasSeenResolverPrioritiesTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenResolverPrioritiesTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHasSeenResolverApplyTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenResolverApplyTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }


    open suspend fun setHasSeenAddSlotTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenAddSlotTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setHasSeenImportPreviewTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenImportPreviewTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setHasSeenLabSelectionTooltip(seen: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasSeenLabSelectionTooltipKey] = seen
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setCurrentSemester(semester: Int) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[currentSemesterKey] = semester
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setHasManuallyChangedSemester(hasChanged: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[hasManuallyChangedSemesterKey] = hasChanged
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setWasSemesterAutoChanged(autoChanged: Boolean) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[wasSemesterAutoChangedKey] = autoChanged
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setDayStartTime(hour: Int, minute: Int) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[dayStartHourKey] = hour
                preferences[dayStartMinuteKey] = minute
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setDayEndTime(hour: Int, minute: Int) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[dayEndHourKey] = hour
                preferences[dayEndMinuteKey] = minute
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setAutoUpdateIntervalMinutes(minutes: Int) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[autoUpdateIntervalMinutesKey] = minutes
                preferences[autoUpdateIntervalHoursKey] = if (minutes in 1..59) minutes else minutes / 60
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setAutoUpdateIntervalHours(hours: Int) {
        setAutoUpdateIntervalMinutes(hours * 60)
    }

    open suspend fun resetScheduleHashes() {
        try {
            context?.dataStore?.edit { preferences ->
                preferences.remove(lastKnownScheduleHashKey(1))
                preferences.remove(lastKnownScheduleHashKey(2))
                preferences.remove(lastNotifiedScheduleHashKey(1))
                preferences.remove(lastNotifiedScheduleHashKey(2))
                preferences.remove(lastScheduleSyncTimestampKey)
            }
        } catch (_: Throwable) {}
    }

    open suspend fun resetOnboardingAndTooltips() {
        try {
            context?.dataStore?.edit { preferences ->
                preferences.remove(isOnboardingCompletedKey)
                preferences.remove(onboardingTooltipsEnabledKey)
                preferences.remove(hasSeenImportConflictTooltipKey)
                preferences.remove(hasSeenOptimizerTooltipKey)
                preferences.remove(hasSeenResolverPrioritiesTooltipKey)
                preferences.remove(hasSeenResolverApplyTooltipKey)
                preferences.remove(hasSeenAddSlotTooltipKey)
                preferences.remove(hasSeenImportPreviewTooltipKey)
                preferences.remove(hasSeenLabSelectionTooltipKey)
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setLastKnownScheduleHash(semester: Int, hash: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[lastKnownScheduleHashKey(semester)] = hash
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setLastNotifiedScheduleHash(semester: Int, hash: String) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[lastNotifiedScheduleHashKey(semester)] = hash
            }
        } catch (_: Throwable) {}
    }

    open suspend fun setLastScheduleSyncTimestamp(timestamp: Long) {
        try {
            context?.dataStore?.edit { preferences ->
                preferences[lastScheduleSyncTimestampKey] = timestamp
            }
        } catch (_: Throwable) {}
    }

    companion object {
        const val DEFAULT_GITHUB_REPO_BASE_URL = BuildConfig.DEFAULT_GITHUB_REPO_BASE_URL
        const val FALLBACK_GITHUB_REPO_BASE_URL = BuildConfig.FALLBACK_GITHUB_REPO_BASE_URL

        /** Hours between background schedule checks. 0 disables them. */
        const val DEFAULT_AUTO_UPDATE_INTERVAL_HOURS = 6
    }
}

