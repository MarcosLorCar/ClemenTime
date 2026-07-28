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
    val autoUpdateEnabled: Boolean = false,
    val notifyViaPush: Boolean = true,
    val notifyViaApp: Boolean = true
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
        settingsRepository.notifyViaPushFlow,
        settingsRepository.notifyViaAppFlow,
        _appLanguage
    ) { args: Array<Any?> ->
        OnboardingUiState(
            themeMode = args[0] as String,
            selectedTheme = args[1] as String,
            autoUpdateEnabled = args[2] as Boolean,
            notifyViaPush = args[3] as Boolean,
            notifyViaApp = args[4] as Boolean,
            appLanguage = args[5] as String
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

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScheduleNotificationsEnabled(enabled)
        }
    }

    fun setNotifyViaPush(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyViaPush(enabled)
        }
    }

    fun setNotifyViaApp(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotifyViaApp(enabled)
        }
    }

    suspend fun completeOnboarding() {
        if (uiState.value.autoUpdateEnabled) {
            ScheduleSyncWorker.enqueuePeriodicWork(context)
        }
        settingsRepository.setOnboardingCompleted(true)
    }
}
