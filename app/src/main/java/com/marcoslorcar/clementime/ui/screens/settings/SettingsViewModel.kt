package com.marcoslorcar.clementime.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.BuildConfig
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.api.GitHubRelease
import com.marcoslorcar.clementime.data.api.UpdateApiService
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val appLanguage: String = "en",
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val selectedTheme: String = "clementine",
    val githubRepoBaseUrl: String = "https://raw.githubusercontent.com/MarcosLorCar/ClemenTime/master/schedules/dist/",
    val onboardingTooltipsEnabled: Boolean = true,
    val updateState: UpdateState = UpdateState.Idle
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val release: GitHubRelease) : UpdateState
    object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

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
    private val jsonScheduleParser: JsonScheduleParser,
    private val updateApiService: UpdateApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _updateState = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(UpdateState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine<Any?, SettingsUiState>(
        settingsRepository.themeFlow,
        settingsRepository.scrollableTabsFlow,
        settingsRepository.showNowLineFlow,
        settingsRepository.nowLineStyleFlow,
        settingsRepository.highContrastFlow,
        settingsRepository.selectedThemeFlow,
        settingsRepository.githubRepoBaseUrlFlow,
        settingsRepository.onboardingTooltipsEnabledFlow,
        _updateState
    ) { args: Array<Any?> ->
        SettingsUiState(
            themeMode = args[0] as String,
            scrollableTabs = args[1] as Boolean,
            showNowLine = args[2] as Boolean,
            nowLineStyle = args[3] as String,
            highContrast = args[4] as Boolean,
            selectedTheme = args[5] as String,
            githubRepoBaseUrl = args[6] as String,
            onboardingTooltipsEnabled = args[7] as Boolean,
            updateState = args[8] as UpdateState,
            appLanguage = getCurrentLanguage()
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
            val systemLanguage = Locale.getDefault().language
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


    fun setShowNowLine(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowNowLine(show)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setNowLineStyle(style: String) {
        viewModelScope.launch {
            settingsRepository.setNowLineStyle(style)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHighContrast(enabled)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setSelectedTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setOnboardingTooltipsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOnboardingTooltipsEnabled(enabled)
        }
    }

    fun setGithubRepoBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setGithubRepoBaseUrl(url)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val response = updateApiService.getLatestRelease()
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val currentVersion = BuildConfig.VERSION_NAME
                        val latestVersion = release.tag_name.removePrefix("v")
                        if (isNewer(latestVersion, currentVersion)) {
                            _updateState.value = UpdateState.UpdateAvailable(release)
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = UpdateState.Error("Empty response body")
                    }
                } else {
                    _updateState.value = UpdateState.Error("Error: ${response.code()}")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        // Simple comparison for semantic versioning or tag names
        // Ideally use a proper semver library or robust parsing
        val latestClean = latest.split("-")[0]
        val currentClean = current.split("-")[0]
        
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateState.Idle
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
