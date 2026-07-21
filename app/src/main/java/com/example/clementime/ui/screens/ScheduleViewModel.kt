package com.example.clementime.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.ScheduleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ScheduleTab.MONDAY)
    val selectedTab: StateFlow<ScheduleTab> = _selectedTab.asStateFlow()

    val scheduleItems: StateFlow<List<MatterWithSlots>> = scheduleDao.getActiveMattersWithSlots()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
                    isSelected = true
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