package com.marcoslorcar.clementime.ui.screens.schedule

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.model.toEntity
import com.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedTab: ScheduleTab = ScheduleTab.MONDAY,
    val selectedSemester: Int = 1,
    val subjectsWithSlots: List<SubjectWithSlots> = emptyList(),
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val hasOverlaps: Boolean = false,
    val highlightSlotId: Long? = null,
    val isSemesterSwitcherVisible: Boolean = false,
    val hasAnySubjects: Boolean = false,
    val onboardingTooltipsEnabled: Boolean = true,
    val hasSeenOptimizerTooltip: Boolean = false,
    val showAutoChangeTooltip: Boolean = false,
    val dayStartTime: LocalTime = LocalTime.of(8, 30),
    val dayEndTime: LocalTime = LocalTime.of(21, 30)
)

private data class SettingsAndHighlight(
    val scrollable: Boolean,
    val showNowLine: Boolean,
    val nowLineStyle: String,
    val highContrast: Boolean,
    val highlightSlotId: Long?,
    val onboardingTooltipsEnabled: Boolean,
    val hasSeenOptimizerTooltip: Boolean,
    val selectedSemester: Int,
    val wasSemesterAutoChanged: Boolean,
    val dayStartTime: LocalTime,
    val dayEndTime: LocalTime
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val route = runCatching { savedStateHandle.toRoute<ScheduleListRoute>() }.getOrNull()

    private val initialTab: ScheduleTab = run {
        val dayName = route?.dayOfWeek ?: savedStateHandle.get<String>("dayOfWeek")
        if (dayName != null) {
            ScheduleTab.entries.find { it.dayOfWeek.name.equals(dayName, ignoreCase = true) } ?: ScheduleTab.MONDAY
        } else {
            val today = LocalDate.now().dayOfWeek
            ScheduleTab.entries.find { it.dayOfWeek == today } ?: ScheduleTab.MONDAY
        }
    }

    private val _selectedTab = MutableStateFlow(initialTab)
    private val _isSemesterSwitcherVisible = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("dayOfWeek", route?.dayOfWeek).collect { dayName ->
                if (dayName != null) {
                    val targetTab = ScheduleTab.entries.find { it.dayOfWeek.name.equals(dayName, ignoreCase = true) }
                    if (targetTab != null) {
                        _selectedTab.value = targetTab
                    }
                }
            }
        }
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleDao.getAllSubjectsWithSlots(),
        _selectedTab,
        _isSemesterSwitcherVisible,
        combine<Any?, SettingsAndHighlight>(
            settingsRepository.scrollableTabsFlow,
            settingsRepository.showNowLineFlow,
            settingsRepository.nowLineStyleFlow,
            settingsRepository.highContrastFlow,
            savedStateHandle.getStateFlow("highlightSlotId", route?.highlightSlotId),
            settingsRepository.onboardingTooltipsEnabledFlow,
            settingsRepository.hasSeenOptimizerTooltipFlow,
            settingsRepository.currentSemesterFlow,
            settingsRepository.wasSemesterAutoChangedFlow,
            settingsRepository.dayStartHourFlow,
            settingsRepository.dayStartMinuteFlow,
            settingsRepository.dayEndHourFlow,
            settingsRepository.dayEndMinuteFlow
        ) { args ->
            SettingsAndHighlight(
                scrollable = args[0] as Boolean,
                showNowLine = args[1] as Boolean,
                nowLineStyle = args[2] as String,
                highContrast = args[3] as Boolean,
                highlightSlotId = args[4] as Long?,
                onboardingTooltipsEnabled = args[5] as Boolean,
                hasSeenOptimizerTooltip = args[6] as Boolean,
                selectedSemester = args[7] as Int,
                wasSemesterAutoChanged = args[8] as Boolean,
                dayStartTime = LocalTime.of(args[9] as Int, args[10] as Int),
                dayEndTime = LocalTime.of(args[11] as Int, args[12] as Int)
            )
        }
    ) { allSubjects, selectedTab, isSwitcherVisible, settings ->
        val semesterSubjects = allSubjects.filter { 
            (it.subject.semester == settings.selectedSemester || it.subject.semester == 3) && 
            it.subject.isActive 
        }
        val filteredSubjects = semesterSubjects.map { sWithSlots ->
            val filteredSlots = sWithSlots.slots.filter { slot ->
                slot.entryType == EntryType.THEORY || 
                sWithSlots.subject.selectedLabGroup == null || 
                slot.labGroupName == sWithSlots.subject.selectedLabGroup
            }
            sWithSlots.copy(slots = filteredSlots)
        }

        val hasOverlaps = detectAnyOverlap(filteredSubjects)

        ScheduleUiState(
            isLoading = false,
            selectedTab = selectedTab,
            selectedSemester = settings.selectedSemester,
            subjectsWithSlots = filteredSubjects,
            scrollableTabs = settings.scrollable,
            showNowLine = settings.showNowLine,
            nowLineStyle = settings.nowLineStyle,
            highContrast = settings.highContrast,
            hasOverlaps = hasOverlaps,
            highlightSlotId = settings.highlightSlotId,
            isSemesterSwitcherVisible = isSwitcherVisible,
            hasAnySubjects = allSubjects.isNotEmpty(),
            onboardingTooltipsEnabled = settings.onboardingTooltipsEnabled,
            hasSeenOptimizerTooltip = settings.hasSeenOptimizerTooltip,
            showAutoChangeTooltip = settings.wasSemesterAutoChanged,
            dayStartTime = settings.dayStartTime,
            dayEndTime = settings.dayEndTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(isLoading = true, selectedTab = initialTab)
    )

    fun changeTab(tab: ScheduleTab) {
        _selectedTab.value = tab
    }

    fun changeSemester(semester: Int) {
        viewModelScope.launch {
            settingsRepository.setCurrentSemester(semester)
            settingsRepository.setHasManuallyChangedSemester(true)
            settingsRepository.setWasSemesterAutoChanged(false)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun onHighlightConsumed() {
        savedStateHandle["highlightSlotId"] = null
        savedStateHandle["dayOfWeek"] = null
    }

    fun saveSlot(slot: ClassSlotUiModel) {
        viewModelScope.launch {
            val entity = slot.toEntity(slot.subjectId)
            if (entity != null) {
                scheduleDao.updateSlot(entity)
                ScheduleWidgetUtils.updateWidget(context)
            }
        }
    }

    fun toggleSemesterSwitcher() {
        _isSemesterSwitcherVisible.value = !_isSemesterSwitcherVisible.value
        if (_isSemesterSwitcherVisible.value) {
            viewModelScope.launch {
                settingsRepository.setHasManuallyChangedSemester(true)
                settingsRepository.setWasSemesterAutoChanged(false)
            }
        }
    }

    fun closeSemesterSwitcher() {
        _isSemesterSwitcherVisible.value = false
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch {
            scheduleDao.deleteSlotById(slotId)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun markOptimizerTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenOptimizerTooltip(true)
        }
    }

    fun markAutoSemesterChangeTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setWasSemesterAutoChanged(false)
        }
    }

    private fun detectAnyOverlap(subjects: List<SubjectWithSlots>): Boolean {
        val allSlots = subjects.flatMap { s -> s.slots.map { s.subject to it } }
        val slotsByDay = allSlots.groupBy { it.second.dayOfWeek }
        for (daySlots in slotsByDay.values) {
            val sorted = daySlots.sortedBy { it.second.startTime }
            for (i in 0 until sorted.size - 1) {
                for (j in i + 1 until sorted.size) {
                    val sub1 = sorted[i].first
                    val sub2 = sorted[j].first
                    val s1 = sorted[i].second
                    val s2 = sorted[j].second
                    if (!sub1.isDummy && !sub2.isDummy && !s1.isIgnored && !s2.isIgnored && s1.startTime < s2.endTime && s2.startTime < s1.endTime) {
                        return true
                    }
                }
            }
        }
        return false
    }

}
