package com.example.clementime.ui.screens.matter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.ScheduleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MattersUiState(
    val matters: List<MatterWithSlots> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedMatterIds: Set<Long> = emptySet(),
    val isSelectionModeForced: Boolean = false
) {
    val isInSelectionMode: Boolean
        get() = isSelectionModeForced || selectedMatterIds.isNotEmpty()

    val filteredMatters: List<MatterWithSlots>
        get() = if (searchQuery.isBlank()) {
            matters
        } else {
            matters.filter {
                it.matter.name.contains(searchQuery, ignoreCase = true) ||
                        it.matter.code.contains(searchQuery, ignoreCase = true)
            }
        }
}

sealed interface MattersUiEvent {
    data class ToggleMatterActive(val matterId: Long, val isActive: Boolean) : MattersUiEvent
    data class DeleteMatter(val matterId: Long) : MattersUiEvent
    data class SearchQueryChanged(val query: String) : MattersUiEvent
    object NukeAllMatters : MattersUiEvent
    data class ToggleMatterSelection(val matterId: Long) : MattersUiEvent
    object ClearSelection : MattersUiEvent
    object DeleteSelectedMatters : MattersUiEvent
    object EnterSelectionMode : MattersUiEvent
    data class ToggleGroupSelection(val matterIds: List<Long>) : MattersUiEvent
    object DisableSelectedMatters : MattersUiEvent
}

@HiltViewModel
class MattersViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MattersUiState())
    val uiState: StateFlow<MattersUiState> = _uiState.asStateFlow()

    init {
        loadMatters()
    }

    private fun loadMatters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scheduleDao.getAllMattersWithSlots()
                .collect { list ->
                    _uiState.update { it.copy(matters = list, isLoading = false) }
                }
        }
    }

    fun onEvent(event: MattersUiEvent) {
        when (event) {
            is MattersUiEvent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is MattersUiEvent.ToggleMatterActive -> {
                viewModelScope.launch {
                    scheduleDao.updateMatterActiveStatus(event.matterId, event.isActive)
                }
            }
            is MattersUiEvent.DeleteMatter -> {
                viewModelScope.launch {
                    scheduleDao.deleteMatterById(event.matterId)
                }
            }
            is MattersUiEvent.NukeAllMatters -> {
                viewModelScope.launch {
                    scheduleDao.deleteAllMatters()
                }
            }
            is MattersUiEvent.ToggleMatterSelection -> {
                _uiState.update { state ->
                    val updated = state.selectedMatterIds.toMutableSet()
                    if (updated.contains(event.matterId)) {
                        updated.remove(event.matterId)
                    } else {
                        updated.add(event.matterId)
                    }
                    state.copy(selectedMatterIds = updated)
                }
            }
            is MattersUiEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedMatterIds = emptySet(), isSelectionModeForced = false) }
            }
            is MattersUiEvent.DeleteSelectedMatters -> {
                viewModelScope.launch {
                    val idsToDelete = _uiState.value.selectedMatterIds.toList()
                    scheduleDao.deleteMattersByIds(idsToDelete)
                    _uiState.update { it.copy(selectedMatterIds = emptySet(), isSelectionModeForced = false) }
                }
            }
            is MattersUiEvent.EnterSelectionMode -> {
                _uiState.update { it.copy(isSelectionModeForced = true) }
            }
            is MattersUiEvent.ToggleGroupSelection -> {
                _uiState.update { state ->
                    val updated = state.selectedMatterIds.toMutableSet()
                    val allSelected = event.matterIds.isNotEmpty() && event.matterIds.all { updated.contains(it) }
                    if (allSelected) {
                        updated.removeAll(event.matterIds.toSet())
                    } else {
                        updated.addAll(event.matterIds)
                    }
                    state.copy(selectedMatterIds = updated)
                }
            }
            is MattersUiEvent.DisableSelectedMatters -> {
                viewModelScope.launch {
                    val idsToDisable = _uiState.value.selectedMatterIds.toList()
                    scheduleDao.updateMattersActiveStatus(idsToDisable, false)
                    _uiState.update { it.copy(selectedMatterIds = emptySet(), isSelectionModeForced = false) }
                }
            }
        }
    }
}