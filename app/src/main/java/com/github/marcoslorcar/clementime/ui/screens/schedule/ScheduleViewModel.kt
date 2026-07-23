package com.github.marcoslorcar.clementime.ui.screens.schedule

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.ScheduleDao
import com.github.marcoslorcar.clementime.data.SettingsRepository
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.github.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedTab: ScheduleTab = ScheduleTab.MONDAY,
    val subjectsWithSlots: List<SubjectWithSlots> = emptyList(),
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val hasOverlaps: Boolean = false,
    val highlightSlotId: Long? = null
)

private data class SettingsAndHighlight(
    val scrollable: Boolean,
    val showNowLine: Boolean,
    val nowLineStyle: String,
    val highContrast: Boolean,
    val highlightSlotId: Long?
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
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
        scheduleDao.getActiveSubjectsWithSlots(),
        _selectedTab,
        combine(
            settingsRepository.scrollableTabsFlow,
            settingsRepository.showNowLineFlow,
            settingsRepository.nowLineStyleFlow,
            settingsRepository.highContrastFlow,
            savedStateHandle.getStateFlow<Long?>("highlightSlotId", route?.highlightSlotId),
            ::SettingsAndHighlight
        )
    ) { rawSubjects, selectedTab, settings ->
        val filteredSubjects = rawSubjects.map { sWithSlots ->
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
            subjectsWithSlots = filteredSubjects,
            scrollableTabs = settings.scrollable,
            showNowLine = settings.showNowLine,
            nowLineStyle = settings.nowLineStyle,
            highContrast = settings.highContrast,
            hasOverlaps = hasOverlaps,
            highlightSlotId = settings.highlightSlotId ?: route?.highlightSlotId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(isLoading = true, selectedTab = initialTab)
    )

    fun changeTab(tab: ScheduleTab) {
        _selectedTab.value = tab
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch {
            scheduleDao.deleteSlotById(slotId)
            ScheduleWidgetUtils.updateWidget(context)
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
