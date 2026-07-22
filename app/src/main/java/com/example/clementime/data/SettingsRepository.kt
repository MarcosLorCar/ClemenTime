package com.example.clementime.data

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
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("app_language")
    private val scrollableTabsKey = booleanPreferencesKey("scrollable_tabs")
    private val syncDirectoryKey = stringPreferencesKey("sync_directory_uri")

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[themeKey] ?: "system"
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[languageKey] ?: "en"
    }

    val scrollableTabsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[scrollableTabsKey] ?: false
    }

    val syncDirectoryFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[syncDirectoryKey]
    }

    suspend fun setThemeMode(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme
        }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[languageKey] = lang
        }
    }

    suspend fun setScrollableTabs(scrollable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[scrollableTabsKey] = scrollable
        }
    }

    suspend fun setSyncDirectoryUri(uriString: String?) {
        context.dataStore.edit { preferences ->
            if (uriString == null) {
                preferences.remove(syncDirectoryKey)
            } else {
                preferences[syncDirectoryKey] = uriString
            }
        }
    }
}
