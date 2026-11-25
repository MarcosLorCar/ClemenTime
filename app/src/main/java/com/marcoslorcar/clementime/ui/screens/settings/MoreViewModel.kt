package com.marcoslorcar.clementime.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.TermCodeParser
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import com.marcoslorcar.clementime.utils.IcsExporter
import com.marcoslorcar.clementime.utils.ScheduleDiffChecker
import com.marcoslorcar.clementime.worker.FcmTopicManager
import com.marcoslorcar.clementime.worker.ScheduleUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val syncMode: com.marcoslorcar.clementime.data.SyncMode = com.marcoslorcar.clementime.data.SyncMode.ONLINE,
    val isCheckingUpdates: Boolean = false,
    val pendingDiffs: List<SlotDiff> = emptyList(),
    val pendingRemoteSlots: List<JsonFlatSlot> = emptyList(),
    val showDiffBottomSheet: Boolean = false,
    val selectedUniversityId: String = "",
    val selectedFacultyId: String = "",
    val linkedFacultyLabel: String = "",
    val periodConfig: PeriodConfig = PeriodConfig.DEFAULT,
    val subjectCountsBySemester: Map<Int, Int> = emptyMap()
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
    @param:ApplicationContext private val context: Context? = null
) : ViewModel() {

    private data class LinkedFacultyInfo(
        val universityId: String = "",
        val facultyId: String = "",
        val label: String = ""
    )

    private val _appLanguage = MutableStateFlow(getCurrentLanguage())
    private val _isCheckingUpdates = MutableStateFlow(false)
    private val _pendingDiffs = MutableStateFlow<List<SlotDiff>>(emptyList())
    private val _pendingRemoteSlots = MutableStateFlow<List<JsonFlatSlot>>(emptyList())
    private val _pendingRemoteHash = MutableStateFlow("")
    private val _pendingSemester = MutableStateFlow(1)
    private val _showDiffBottomSheet = MutableStateFlow(false)

    private val linkedFacultyFlow = combine(
        settingsRepository.selectedUniversityIdFlow,
        settingsRepository.selectedFacultyIdFlow,
        settingsRepository.selectedUniversityNameFlow,
        settingsRepository.selectedFacultyNameFlow
    ) { uniId, facId, uniName, facName ->
        val label = if (facId.isBlank()) {
            ""
        } else {
            listOf(
                uniName.ifBlank { uniId.uppercase() },
                facName.ifBlank { facId.uppercase() }
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
        LinkedFacultyInfo(
            universityId = uniId,
            facultyId = facId,
            label = label
        )
    }

    private val subjectCountsBySemesterFlow = scheduleDao.getAllSubjectsWithSlots().map { subjects ->
        subjects.groupingBy { it.subject.semester }.eachCount()
    }

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
        settingsRepository.syncModeFlow,
        _isCheckingUpdates,
        _appLanguage,
        _pendingDiffs,
        _pendingRemoteSlots,
        _showDiffBottomSheet,
        linkedFacultyFlow,
        settingsRepository.periodConfigFlow,
        subjectCountsBySemesterFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val linkedFaculty = args[20] as LinkedFacultyInfo
        @Suppress("UNCHECKED_CAST")
        val subjectCounts = args[22] as Map<Int, Int>
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
            syncMode = args[14] as com.marcoslorcar.clementime.data.SyncMode,
            isCheckingUpdates = args[15] as Boolean,
            appLanguage = args[16] as String,
            pendingDiffs = args[17] as List<SlotDiff>,
            pendingRemoteSlots = args[18] as List<JsonFlatSlot>,
            showDiffBottomSheet = args[19] as Boolean,
            selectedUniversityId = linkedFaculty.universityId,
            selectedFacultyId = linkedFaculty.facultyId,
            linkedFacultyLabel = linkedFaculty.label,
            periodConfig = args[21] as PeriodConfig,
            subjectCountsBySemester = subjectCounts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MoreUiState(appLanguage = getCurrentLanguage())
    )

    private fun getCurrentLanguage(): String {
        val locales = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        return if (locales != null && !locales.isEmpty) {
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

    fun setPeriodConfig(config: PeriodConfig) {
        viewModelScope.launch {
            settingsRepository.setPeriodConfig(config)
            ScheduleWidgetUtils.updateWidget(context)
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

    fun setSyncMode(mode: com.marcoslorcar.clementime.data.SyncMode) {
        viewModelScope.launch {
            settingsRepository.setSyncMode(mode)
            if (context != null) {
                FcmTopicManager.syncRegistration(context, settingsRepository)
            }
        }
    }

    fun setAutoUpdateIntervalHours(hours: Int) {
        setAutoUpdateIntervalMinutes(if (hours == 15) 15 else hours * 60)
    }

    fun setAutoUpdateIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdateIntervalMinutes(minutes)
            if (context != null) {
                ScheduleUpdateWorker.schedulePeriodicWork(context, minutes)
            }
        }
    }

    fun debugShowFcmStatus(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val syncMode = settingsRepository.syncModeFlow.first()
            val gmsAvailable = if (context != null) FcmTopicManager.isGooglePlayServicesAvailable(context) else false
            val uni = settingsRepository.selectedUniversityIdFlow.first()
            val faculty = settingsRepository.selectedFacultyIdFlow.first()
            val fcmTopicPrefix = settingsRepository.selectedFcmTopicPrefixFlow.first()
            val targetTopic = if (faculty.isBlank()) {
                "None (no faculty linked)"
            } else {
                FcmTopicManager.topicFor(uni, faculty, fcmTopicPrefix.ifBlank { null })
            }
            val subscribedTopic = settingsRepository.subscribedFcmTopicFlow.first()
            val periodConfig = settingsRepository.periodConfigFlow.first()
            val periodHashes = (1..periodConfig.count).map { sem ->
                val code = TermCodeParser.formatTermCode(sem, periodConfig.type)
                val hash = settingsRepository.getLastKnownScheduleHashFlow(sem).first()
                code to hash
            }
            val report = buildString {
                appendLine("Sync Mode: $syncMode")
                appendLine("GMS Available: $gmsAvailable")
                appendLine("Target Topic: $targetTopic")
                appendLine("Subscribed Topic: ${subscribedTopic ?: "(none)"}")
                periodHashes.forEachIndexed { index, (code, hash) ->
                    val line = "$code Hash: ${hash.take(12).ifBlank { "(empty)" }}"
                    if (index < periodHashes.lastIndex) appendLine(line) else append(line)
                }
            }
            onResult(report)
        }
    }

    fun debugResetScheduleHashes() {
        viewModelScope.launch {
            settingsRepository.resetScheduleHashes()
            if (context != null) {
                importRepository.clearRemoteCache(context)
            }
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

    fun checkScheduleUpdatesNow(
        onNoUpdatesFound: () -> Unit = {},
        onNoFacultyLinked: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val facultyId = settingsRepository.selectedFacultyIdFlow.first()
            if (facultyId.isBlank()) {
                onNoFacultyLinked()
                return@launch
            }
            if (context == null) {
                onNoUpdatesFound()
                return@launch
            }

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
                    _pendingSemester.value = syncResult.semester ?: settingsRepository.currentSemesterFlow.first()
                    _showDiffBottomSheet.value = true
                } else {
                    onNoUpdatesFound()
                }
            } catch (_: Exception) {
                _isCheckingUpdates.value = false
                onNoUpdatesFound()
            }
        }
    }

    fun dismissDiffBottomSheet() {
        _showDiffBottomSheet.value = false
    }

    private suspend fun saveCurrentRemoteSubjectHashes(semester: Int, remoteSlots: List<JsonFlatSlot>) {
        if (remoteSlots.isEmpty()) return
        val activeSubjects = importRepository.getExistingActiveSubjects(semester)
        val subjectHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
            existingSubjects = activeSubjects,
            remoteSlots = remoteSlots,
            currentSemester = semester
        )
        settingsRepository.setLastRemoteSubjectHashes(semester, subjectHashes)
    }

    fun ignorePendingSlotDiffs() {
        viewModelScope.launch {
            try {
                val semester = _pendingSemester.value
                if (_pendingRemoteHash.value.isNotBlank()) {
                    settingsRepository.setLastKnownScheduleHash(semester, _pendingRemoteHash.value)
                }
                saveCurrentRemoteSubjectHashes(semester, _pendingRemoteSlots.value)
            } catch (_: Exception) {}
            _showDiffBottomSheet.value = false
            _pendingDiffs.value = emptyList()
            _pendingRemoteSlots.value = emptyList()
            _pendingRemoteHash.value = ""
        }
    }

    fun applyPendingSlotDiffs(
        selectedSubjectKeys: Set<String>? = null,
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val semester = _pendingSemester.value
                val universityId = settingsRepository.selectedUniversityIdFlow.first()
                val facultyId = settingsRepository.selectedFacultyIdFlow.first()
                val applied = importRepository.applySlotDiffs(
                    diffs = _pendingDiffs.value,
                    remoteSlots = _pendingRemoteSlots.value,
                    semester = semester,
                    selectedSubjectKeys = selectedSubjectKeys,
                    facultyId = facultyId,
                    universityId = universityId
                )
                // Only mark the version accepted if something was actually written; otherwise
                // the diff would never resurface even though nothing changed.
                if (applied > 0 && _pendingRemoteHash.value.isNotBlank()) {
                    settingsRepository.setLastKnownScheduleHash(semester, _pendingRemoteHash.value)
                    saveCurrentRemoteSubjectHashes(semester, _pendingRemoteSlots.value)
                }
                ScheduleWidgetUtils.updateWidget(context)
                _showDiffBottomSheet.value = false
                _pendingDiffs.value = emptyList()
                _pendingRemoteSlots.value = emptyList()
                _pendingRemoteHash.value = ""
                onSuccess(applied)
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
                val periodConfig = settingsRepository.periodConfigFlow.first()
                val jsonString = jsonScheduleParser.exportToJson("ClemenTime Export", subjects, periodConfig.type)

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

                val semesters = listOf(
                    IcsExporter.SemesterExportData(filterIcsSlots(s1Subjects), s1Start, s1End),
                    IcsExporter.SemesterExportData(filterIcsSlots(s2Subjects), s2Start, s2End)
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

    internal suspend fun buildIcsExportData(boundaries: List<LocalDate>): List<IcsExporter.SemesterExportData> {
        require(boundaries.size >= 2) { "At least 2 boundary dates are required" }
        val periodCount = boundaries.size - 1
        return (0 until periodCount).map { idx ->
            val semesterNumber = idx + 1
            val start = boundaries[idx]
            val end = if (idx < periodCount - 1) {
                boundaries[idx + 1].minusDays(1)
            } else {
                boundaries[idx + 1]
            }
            val activeSubjects = scheduleDao.getActiveSubjectsWithSlotsBySemester(semesterNumber).first()
            IcsExporter.SemesterExportData(filterIcsSlots(activeSubjects), start, end)
        }
    }

    fun exportFullYearToIcs(
        context: Context,
        customUri: Uri,
        boundaries: List<LocalDate>,
        onResult: (ExportStatus) -> Unit
    ) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val semesters = buildIcsExportData(boundaries)
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

    private fun filterIcsSlots(
        subjects: List<com.marcoslorcar.clementime.data.SubjectWithSlots>
    ): List<com.marcoslorcar.clementime.data.SubjectWithSlots> {
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
}
