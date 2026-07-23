package com.github.marcoslorcar.clementime.ui.screens

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.marcoslorcar.clementime.data.ScheduleDao
import com.github.marcoslorcar.clementime.data.SettingsRepository
import com.github.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val appLanguage: String = "en",
    val scrollableTabs: Boolean = false
)

sealed interface ExportStatus {
    object Idle : ExportStatus
    object Exporting : ExportStatus
    data class Success(val message: String) : ExportStatus
    data class Error(val error: String) : ExportStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleDao: ScheduleDao,
    private val jsonScheduleParser: JsonScheduleParser
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.scrollableTabsFlow
    ) { theme, scrollable ->
        SettingsUiState(
            themeMode = theme,
            appLanguage = getCurrentLanguage(),
            scrollableTabs = scrollable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(appLanguage = getCurrentLanguage())
    )

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) {
            locales.get(0)?.language ?: "en"
        } else {
            val systemLanguage = java.util.Locale.getDefault().language
            if (systemLanguage == "es") "es" else "en"
        }
    }

    fun setThemeMode(theme: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun setAppLanguage(lang: String) {
        val localeList = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setScrollableTabs(scrollable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScrollableTabs(scrollable)
        }
    }

    fun exportData(context: Context, customUri: Uri, onResult: (ExportStatus) -> Unit) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val subjects = scheduleDao.getAllSubjectsWithSlots().first()
                val jsonString = jsonScheduleParser.exportToJson("ClemenTime Export", subjects)
                
                context.contentResolver.openOutputStream(customUri)?.use { out ->
                    out.write(jsonString.toByteArray())
                }
                onResult(ExportStatus.Success("Backup saved successfully"))
            } catch (e: Exception) {
                onResult(ExportStatus.Error("Export failed: ${e.localizedMessage}"))
            }
        }
    }
}
