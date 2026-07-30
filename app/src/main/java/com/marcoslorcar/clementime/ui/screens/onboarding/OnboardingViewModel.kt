package com.marcoslorcar.clementime.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class OnboardingUiState(
    val themeMode: String = "system",
    val selectedTheme: String = "clementine",
    val appLanguage: String = "en",
    val autoUpdateIntervalHours: Int = 0
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _appLanguage = MutableStateFlow(getCurrentLanguage())

    val uiState: StateFlow<OnboardingUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.selectedThemeFlow,
        settingsRepository.autoUpdateIntervalHoursFlow,
        _appLanguage
    ) { theme: String, selectedTheme: String, interval: Int, lang: String ->
        OnboardingUiState(
            themeMode = theme,
            selectedTheme = selectedTheme,
            appLanguage = lang,
            autoUpdateIntervalHours = interval
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingUiState(appLanguage = getCurrentLanguage())
    )

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) {
            locales.get(0)?.language ?: "en"
        } else {
            val systemLanguage = Locale.getDefault().language
            if (systemLanguage == "es") "es" else "en"
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setSelectedTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        val localeList = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setAutoUpdateIntervalHours(hours: Int, context: android.content.Context) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateIntervalHours(hours)
            com.marcoslorcar.clementime.worker.ScheduleUpdateWorker.schedulePeriodicWork(context, hours)
        }
    }

    suspend fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
    }
}
