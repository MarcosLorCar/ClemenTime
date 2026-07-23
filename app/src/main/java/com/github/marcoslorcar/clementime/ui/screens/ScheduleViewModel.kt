package com.github.marcoslorcar.clementime.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.ScheduleDao
import com.github.marcoslorcar.clementime.data.SettingsRepository
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedTab: ScheduleTab = ScheduleTab.MONDAY,
    val subjectsWithSlots: List<SubjectWithSlots> = emptyList(),
    val scrollableTabs: Boolean = false,
    val hasOverlaps: Boolean = false
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    scheduleDao: ScheduleDao,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialTab: ScheduleTab = run {
        val route = runCatching { savedStateHandle.toRoute<ScheduleListRoute>() }.getOrNull()
        val dayName = route?.dayOfWeek ?: savedStateHandle.get<String>("dayOfWeek")
        if (dayName != null) {
            ScheduleTab.entries.find { it.dayOfWeek.name.equals(dayName, ignoreCase = true) } ?: ScheduleTab.MONDAY
        } else {
            ScheduleTab.MONDAY
        }
    }

    private val _selectedTab = MutableStateFlow(initialTab)

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleDao.getActiveSubjectsWithSlots(),
        _selectedTab,
        settingsRepository.scrollableTabsFlow
    ) { rawSubjects, selectedTab, scrollable ->
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
            scrollableTabs = scrollable,
            hasOverlaps = hasOverlaps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(isLoading = true)
    )

    fun changeTab(tab: ScheduleTab) {
        _selectedTab.value = tab
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
