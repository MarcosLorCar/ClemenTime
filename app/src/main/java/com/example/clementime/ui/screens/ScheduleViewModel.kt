package com.example.clementime.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.clementime.data.SubjectWithSlots
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.SettingsRepository
import com.example.clementime.ui.navigation.ScheduleListRoute
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
    val scrollableTabs: Boolean = false
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
    ) { subjectsWithSlots, selectedTab, scrollable ->
        ScheduleUiState(
            isLoading = false,
            selectedTab = selectedTab,
            subjectsWithSlots = subjectsWithSlots,
            scrollableTabs = scrollable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(isLoading = true)
    )

    fun changeTab(tab: ScheduleTab) {
        _selectedTab.value = tab
    }

}