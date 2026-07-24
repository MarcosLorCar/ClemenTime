package com.marcoslorcar.clementime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
                preferences[githubRepoBaseUrlKey] ?: "https://raw.githubusercontent.com/MarcosLorCar/ClemenTime/master/schedules/dist/"
            } ?: kotlinx.coroutines.flow.flowOf("https://raw.githubusercontent.com/MarcosLorCar/ClemenTime/master/schedules/dist/")
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf("https://raw.githubusercontent.com/MarcosLorCar/ClemenTime/master/schedules/dist/")
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
}

