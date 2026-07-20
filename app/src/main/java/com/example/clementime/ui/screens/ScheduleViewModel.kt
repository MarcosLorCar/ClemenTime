package com.example.clementime.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.ScheduleItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ScheduleTab.MONDAY)

    val selectedTab = _selectedTab.asStateFlow()

    val scheduleItems: StateFlow<List<ScheduleItem>> = scheduleDao.getAllItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun changeTab(tab: ScheduleTab) {
        _selectedTab.value = tab
    }

    fun addItem(title: String, description: String, startTime: Long, endTime: Long) {
        viewModelScope.launch {
            scheduleDao.insertItem(
                ScheduleItem(
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }

    fun toggleCompletion(item: ScheduleItem) {
        viewModelScope.launch {
            scheduleDao.updateItem(item.copy(isCompleted = !item.isCompleted))
        }
    }

    fun deleteItem(item: ScheduleItem) {
        viewModelScope.launch {
            scheduleDao.deleteItem(item)
        }
    }
}
