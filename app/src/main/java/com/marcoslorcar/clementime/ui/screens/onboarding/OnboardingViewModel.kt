package com.marcoslorcar.clementime.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.work.ScheduleSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val scheduleNotificationsEnabled: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _appLanguage = MutableStateFlow(getCurrentLanguage())

    val uiState: StateFlow<OnboardingUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.selectedThemeFlow,
        settingsRepository.scheduleNotificationsEnabledFlow,
        _appLanguage
    ) { theme: String, selectedTheme: String, notifications: Boolean, lang: String ->
        OnboardingUiState(
            themeMode = theme,
            selectedTheme = selectedTheme,
            appLanguage = lang,
            scheduleNotificationsEnabled = notifications
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

    fun setScheduleNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScheduleNotificationsEnabled(enabled)
            // Note: We don't enqueue work here yet, we'll do it in completeOnboarding if enabled
        }
    }

    suspend fun completeOnboarding() {
        if (uiState.value.scheduleNotificationsEnabled) {
            ScheduleSyncWorker.enqueuePeriodicWork(context)
        }
        settingsRepository.setOnboardingCompleted(true)
    }
}
