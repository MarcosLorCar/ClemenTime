package com.example.clementime.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.ScheduleDao
import com.example.clementime.ui.navigation.ScheduleListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

import com.example.clementime.data.SettingsRepository

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val selectedTab: ScheduleTab = ScheduleTab.MONDAY,
    val mattersWithSlots: List<MatterWithSlots> = emptyList(),
    val scrollableTabs: Boolean = false
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val settingsRepository: SettingsRepository,
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
        scheduleDao.getActiveMattersWithSlots(),
        _selectedTab,
        settingsRepository.scrollableTabsFlow
    ) { mattersWithSlots, selectedTab, scrollable ->
        ScheduleUiState(
            isLoading = false,
            selectedTab = selectedTab,
            mattersWithSlots = mattersWithSlots,
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

    fun addSlot(
        matterId: Long,
        startTime: LocalTime,
        endTime: LocalTime,
        classroom: String? = null,
        labGroupName: String? = null
    ) {
        viewModelScope.launch {
            scheduleDao.insertSlot(
                ClassSlot(
                    matterId = matterId,
                    dayOfWeek = _selectedTab.value.dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    classroom = classroom,
                    labGroupName = labGroupName,
                )
            )
        }
    }

    fun deleteSlot(slot: ClassSlot) {
        viewModelScope.launch {
            scheduleDao.deleteSlot(slot)
        }
    }
}