package com.marcoslorcar.clementime.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import com.marcoslorcar.clementime.utils.IcsExporter
import com.marcoslorcar.clementime.worker.ScheduleUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject

import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.utils.SlotDiff

data class MoreUiState(
    val themeMode: String = "system",
    val appLanguage: String = "en",
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val selectedTheme: String = "clementine",
    val githubRepoBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
    val onboardingTooltipsEnabled: Boolean = true,
    val dayStartTime: LocalTime = LocalTime.of(8, 30),
    val dayEndTime: LocalTime = LocalTime.of(21, 30),
    val autoUpdateIntervalHours: Int = SettingsRepository.DEFAULT_AUTO_UPDATE_INTERVAL_HOURS,
    val lastScheduleSyncTimestamp: Long = 0L,
    val isCheckingUpdates: Boolean = false,
    val pendingDiffs: List<SlotDiff> = emptyList(),
    val pendingRemoteSlots: List<JsonFlatSlot> = emptyList(),
    val showDiffBottomSheet: Boolean = false
)

sealed interface ExportStatus {
    object Exporting : ExportStatus
    data class Success(val message: String) : ExportStatus
    data class Error(val error: String) : ExportStatus
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleDao: ScheduleDao,
    private val jsonScheduleParser: JsonScheduleParser,
    private val importRepository: ImportRepository,
    private val apiService: GitHubScheduleApiService? = null,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _appLanguage = MutableStateFlow(getCurrentLanguage())
    private val _isCheckingUpdates = MutableStateFlow(false)
    private val _pendingDiffs = MutableStateFlow<List<SlotDiff>>(emptyList())
    private val _pendingRemoteSlots = MutableStateFlow<List<JsonFlatSlot>>(emptyList())
    private val _pendingRemoteHash = MutableStateFlow("")
    private val _showDiffBottomSheet = MutableStateFlow(false)

    val uiState: StateFlow<MoreUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.scrollableTabsFlow,
        settingsRepository.showNowLineFlow,
        settingsRepository.nowLineStyleFlow,
        settingsRepository.highContrastFlow,
        settingsRepository.selectedThemeFlow,
        settingsRepository.githubRepoBaseUrlFlow,
        settingsRepository.onboardingTooltipsEnabledFlow,
        settingsRepository.dayStartHourFlow,
        settingsRepository.dayStartMinuteFlow,
        settingsRepository.dayEndHourFlow,
        settingsRepository.dayEndMinuteFlow,
        settingsRepository.autoUpdateIntervalMinutesFlow,
        settingsRepository.lastScheduleSyncTimestampFlow,
        _isCheckingUpdates,
        _appLanguage,
        _pendingDiffs,
        _pendingRemoteSlots,
        _showDiffBottomSheet
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        MoreUiState(
            themeMode = args[0] as String,
            scrollableTabs = args[1] as Boolean,
            showNowLine = args[2] as Boolean,
            nowLineStyle = args[3] as String,
            highContrast = args[4] as Boolean,
            selectedTheme = args[5] as String,
            githubRepoBaseUrl = args[6] as String,
            onboardingTooltipsEnabled = args[7] as Boolean,
            dayStartTime = LocalTime.of(args[8] as Int, args[9] as Int),
            dayEndTime = LocalTime.of(args[10] as Int, args[11] as Int),
            autoUpdateIntervalHours = args[12] as Int,
            lastScheduleSyncTimestamp = args[13] as Long,
            isCheckingUpdates = args[14] as Boolean,
            appLanguage = args[15] as String,
            pendingDiffs = args[16] as List<SlotDiff>,
            pendingRemoteSlots = args[17] as List<JsonFlatSlot>,
            showDiffBottomSheet = args[18] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MoreUiState(appLanguage = getCurrentLanguage())
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
        _appLanguage.value = lang
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

    fun setDayStartTime(time: LocalTime) {
        viewModelScope.launch {
            val snappedTime = snapTo30Minutes(time)
            settingsRepository.setDayStartTime(snappedTime.hour, snappedTime.minute)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun setDayEndTime(time: LocalTime) {
        viewModelScope.launch {
            val snappedTime = snapTo30Minutes(time)
            settingsRepository.setDayEndTime(snappedTime.hour, snappedTime.minute)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun setAutoUpdateIntervalHours(hours: Int) {
        setAutoUpdateIntervalMinutes(if (hours == 15) 15 else hours * 60)
    }

    fun setAutoUpdateIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateIntervalMinutes(minutes)
            ScheduleUpdateWorker.schedulePeriodicWork(context, minutes)
        }
    }

    fun debugTriggerWorkerNow() {
        ScheduleUpdateWorker.enqueueOneTimeWork(context)
    }

    fun debugResetScheduleHashes() {
        viewModelScope.launch {
            settingsRepository.resetScheduleHashes()
        }
    }

    fun debugSimulateScheduleDiff() {
        val mockDiffs = listOf(
            SlotDiff(
                subjectCode = "DEBUG101",
                subjectName = "Debug Systems & Testing",
                changeType = com.marcoslorcar.clementime.utils.DiffType.MODIFIED,
                oldDetail = "Mon 09:00-11:00 (Aula 1.1)",
                newDetail = "Mon 10:00-12:00 (Aula 2.3)"
            )
        )
        _pendingDiffs.value = mockDiffs
        _pendingRemoteHash.value = "debug_simulated_hash_${System.currentTimeMillis()}"
        _showDiffBottomSheet.value = true
    }

    fun debugResetOnboardingAndTooltips() {
        viewModelScope.launch {
            settingsRepository.resetOnboardingAndTooltips()
        }
    }

    fun checkScheduleUpdatesNow(onNoUpdatesFound: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            try {
                // Deliberately not enqueueOneTimeWork(): performSync below does the same
                // check inline and shows the diff sheet. Running the worker too would post
                // a redundant notification for the sheet the user is already looking at.
                val syncResult = ScheduleUpdateWorker.performSync(
                    context = context,
                    settingsRepository = settingsRepository,
                    importRepository = importRepository,
                    apiService = apiService,
                    ignoreInterval = true
                )
                _isCheckingUpdates.value = false
                if (syncResult.diffs.isNotEmpty()) {
                    _pendingDiffs.value = syncResult.diffs
                    _pendingRemoteSlots.value = syncResult.remoteSlots
                    _pendingRemoteHash.value = syncResult.remoteHash
                    _showDiffBottomSheet.value = true
                } else {
                    onNoUpdatesFound?.invoke()
                }
            } catch (_: Exception) {
                _isCheckingUpdates.value = false
                onNoUpdatesFound?.invoke()
            }
        }
    }

    fun dismissDiffBottomSheet() {
        _showDiffBottomSheet.value = false
    }

    fun ignorePendingSlotDiffs() {
        viewModelScope.launch {
            try {
                if (_pendingRemoteHash.value.isNotBlank()) {
                    val currentSemester = settingsRepository.currentSemesterFlow.first()
                    settingsRepository.setLastKnownScheduleHash(currentSemester, _pendingRemoteHash.value)
                }
            } catch (_: Exception) {}
            _showDiffBottomSheet.value = false
            _pendingDiffs.value = emptyList()
            _pendingRemoteSlots.value = emptyList()
            _pendingRemoteHash.value = ""
        }
    }

    fun applyPendingSlotDiffs(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val currentSemester = settingsRepository.currentSemesterFlow.first()
                val applied = importRepository.applySlotDiffs(
                    _pendingDiffs.value, _pendingRemoteSlots.value, currentSemester
                )
                // Only mark the version accepted if something was actually written; otherwise
                // the diff would never resurface even though nothing changed.
                if (applied > 0 && _pendingRemoteHash.value.isNotBlank()) {
                    settingsRepository.setLastKnownScheduleHash(currentSemester, _pendingRemoteHash.value)
                }
                ScheduleWidgetUtils.updateWidget(context)
                _showDiffBottomSheet.value = false
                _pendingDiffs.value = emptyList()
                _pendingRemoteSlots.value = emptyList()
                _pendingRemoteHash.value = ""
                onSuccess?.invoke()
            } catch (_: Exception) {
                _showDiffBottomSheet.value = false
            }
        }
    }

    private fun snapTo30Minutes(time: LocalTime): LocalTime {
        return when (time.minute) {
            in 0..14 -> time.withMinute(0)
            in 15..44 -> time.withMinute(30)
            else -> time.plusHours(1).withMinute(0)
        }
    }

    fun setGithubRepoBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setGithubRepoBaseUrl(url)
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
                onResult(ExportStatus.Success(context.getString(R.string.export_success_local)))
            } catch (e: Exception) {
                onResult(ExportStatus.Error(context.getString(R.string.export_error_prefix, e.localizedMessage)))
            }
        }
    }

    fun exportFullYearToIcs(
        context: Context,
        customUri: Uri,
        s1Start: LocalDate,
        s1End: LocalDate,
        s2Start: LocalDate,
        s2End: LocalDate,
        onResult: (ExportStatus) -> Unit
    ) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val s1Subjects = scheduleDao.getActiveSubjectsWithSlotsBySemester(1).first()
                val s2Subjects = scheduleDao.getActiveSubjectsWithSlotsBySemester(2).first()

                fun filterSlots(subjects: List<com.marcoslorcar.clementime.data.SubjectWithSlots>): List<com.marcoslorcar.clementime.data.SubjectWithSlots> {
                    return subjects.map { sws ->
                        sws.copy(
                            slots = sws.slots.filter { slot ->
                                !slot.isIgnored && (
                                        slot.entryType == EntryType.THEORY ||
                                                slot.labGroupName == sws.subject.selectedLabGroup
                                        )
                            }
                        )
                    }
                }

                val semesters = listOf(
                    IcsExporter.SemesterExportData(filterSlots(s1Subjects), s1Start, s1End),
                    IcsExporter.SemesterExportData(filterSlots(s2Subjects), s2Start, s2End)
                )

                val icsString = IcsExporter.generateIcsContent(semesters)

                context.contentResolver.openOutputStream(customUri)?.use { out ->
                    out.write(icsString.toByteArray())
                }
                onResult(ExportStatus.Success(context.getString(R.string.export_success_local)))
            } catch (e: Exception) {
                onResult(ExportStatus.Error(context.getString(R.string.export_error_prefix, e.localizedMessage)))
            }
        }
    }
}
