package com.marcoslorcar.clementime.ui.screens.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.model.scheduleDisplaySettings
import com.marcoslorcar.clementime.ui.model.scheduleHours
import com.marcoslorcar.clementime.ui.model.toEntity
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val periodConfig: PeriodConfig = PeriodConfig.DEFAULT,
    val subjectsWithSlots: List<SubjectWithSlots> = emptyList(),
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val hasOverlaps: Boolean = false,
    val isSemesterSwitcherVisible: Boolean = false,
    val hasAnySubjects: Boolean = false,
    val onboardingTooltipsEnabled: Boolean = true,
    val hasSeenOptimizerTooltip: Boolean = false,
    val showAutoChangeTooltip: Boolean = false,
    val dayStartTime: LocalTime = LocalTime.of(8, 30),
    val dayEndTime: LocalTime = LocalTime.of(21, 30)
)

private data class SchedulePeriodState(
    val selectedSemester: Int,
    val config: PeriodConfig,
    val wasAutoChanged: Boolean,
    val hasManuallyChanged: Boolean
)

private data class ScheduleControls(
    val selectedTab: ScheduleTab,
    val isSwitcherVisible: Boolean,
    val onboardingTooltipsEnabled: Boolean,
    val hasSeenOptimizerTooltip: Boolean
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context? = null
) : ViewModel() {

    // Opens on today. A "view in schedule" request no longer arrives here at all - it travels as
    // ScheduleFocus, applied to the pager by the screen, which then reports the day back through
    // changeTab. See ScheduleFocus for why it is not carried as navigation arguments.
    private val initialTab: ScheduleTab = run {
        val today = LocalDate.now().dayOfWeek
        ScheduleTab.entries.find { it.dayOfWeek == today } ?: ScheduleTab.MONDAY
    }

    private val _selectedTab = MutableStateFlow(initialTab)
    private val _isSemesterSwitcherVisible = MutableStateFlow(false)

    private val controls = combine(
        _selectedTab,
        _isSemesterSwitcherVisible,
        settingsRepository.onboardingTooltipsEnabledFlow,
        settingsRepository.hasSeenOptimizerTooltipFlow,
        ::ScheduleControls
    )

    private val period = combine(
        settingsRepository.currentSemesterFlow,
        settingsRepository.periodConfigFlow,
        settingsRepository.wasSemesterAutoChangedFlow,
        settingsRepository.hasManuallyChangedSemesterFlow,
        ::SchedulePeriodState
    )

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleDao.getAllSubjectsWithSlots(),
        controls,
        period,
        settingsRepository.scheduleDisplaySettings(),
        settingsRepository.scheduleHours()
    ) { allSubjects, controls, period, display, hours ->
        val effectiveSemester = period.selectedSemester.coerceIn(1, period.config.count)
        val semesterSubjects = allSubjects.filter { it.subject.semester == effectiveSemester && it.subject.isActive }
        val filteredSubjects = semesterSubjects.map { sWithSlots ->
            val filteredSlots = sWithSlots.slots.filter { slot ->
                slot.entryType == EntryType.THEORY || 
                sWithSlots.subject.selectedLabGroup == null || 
                slot.labGroupName == sWithSlots.subject.selectedLabGroup
            }
            sWithSlots.copy(slots = filteredSlots)
        }

        val hasOverlaps = detectAnyOverlap(filteredSubjects)
        
        val hasSubjectsInOtherSemesters = period.config.count > 1 && allSubjects.any {
            it.subject.semester in 1..period.config.count && it.subject.semester != effectiveSemester
        }
        val showAutoChangeTooltip = period.config.count > 1 && (
            period.wasAutoChanged || (hasSubjectsInOtherSemesters && !period.hasManuallyChanged)
        )

        ScheduleUiState(
            isLoading = false,
            selectedTab = controls.selectedTab,
            selectedSemester = effectiveSemester,
            periodConfig = period.config,
            subjectsWithSlots = filteredSubjects,
            scrollableTabs = display.scrollableTabs,
            showNowLine = display.showNowLine,
            nowLineStyle = display.nowLineStyle,
            highContrast = display.highContrast,
            hasOverlaps = hasOverlaps,
            isSemesterSwitcherVisible = controls.isSwitcherVisible && period.config.count > 1,
            hasAnySubjects = allSubjects.isNotEmpty(),
            onboardingTooltipsEnabled = controls.onboardingTooltipsEnabled,
            hasSeenOptimizerTooltip = controls.hasSeenOptimizerTooltip,
            showAutoChangeTooltip = showAutoChangeTooltip,
            dayStartTime = hours.start,
            dayEndTime = hours.end
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
        }
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
