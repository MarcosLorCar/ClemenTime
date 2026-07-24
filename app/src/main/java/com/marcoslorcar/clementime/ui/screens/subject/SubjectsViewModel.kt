package com.marcoslorcar.clementime.ui.screens.subject

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectsUiState(
    val subjects: List<SubjectWithSlots> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedSubjectIds: Set<Long> = emptySet(),
    val isSelectionModeForced: Boolean = false,
    val highContrast: Boolean = false
) {
    val isInSelectionMode: Boolean
        get() = isSelectionModeForced || selectedSubjectIds.isNotEmpty()

    val filteredSubjects: List<SubjectWithSlots>
        get() = if (searchQuery.isBlank()) {
            subjects
        } else {
            subjects.filter {
                it.subject.name.contains(searchQuery, ignoreCase = true) ||
                        it.subject.code.contains(searchQuery, ignoreCase = true)
            }
        }
}

sealed interface SubjectsUiEvent {
    data class ToggleSubjectActive(val subjectId: Long, val isActive: Boolean) : SubjectsUiEvent
    data class DeleteSubject(val subjectId: Long) : SubjectsUiEvent
    data class SearchQueryChanged(val query: String) : SubjectsUiEvent
    object NukeAllSubjects : SubjectsUiEvent
    data class ToggleSubjectSelection(val subjectId: Long) : SubjectsUiEvent
    object ClearSelection : SubjectsUiEvent
    object DeleteSelectedSubjects : SubjectsUiEvent
    object EnterSelectionMode : SubjectsUiEvent
    data class ToggleGroupSelection(val subjectIds: List<Long>) : SubjectsUiEvent
    object DisableSelectedSubjects : SubjectsUiEvent

}

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val settingsRepository: com.marcoslorcar.clementime.data.SettingsRepository,
    @ApplicationContext private val context: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectsUiState())
    val uiState: StateFlow<SubjectsUiState> = _uiState.asStateFlow()

    init {
        loadSubjects()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.highContrastFlow.collect { hc ->
                _uiState.update { it.copy(highContrast = hc) }
            }
        }
    }

    private fun loadSubjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scheduleDao.getAllSubjectsWithSlots()
                .collect { list ->
                    _uiState.update { it.copy(subjects = list, isLoading = false) }
                }
        }
    }

    fun onEvent(event: SubjectsUiEvent) {
        when (event) {
            is SubjectsUiEvent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is SubjectsUiEvent.ToggleSubjectActive -> {
                viewModelScope.launch {
                    scheduleDao.updateSubjectActiveStatus(event.subjectId, event.isActive)
                    ScheduleWidgetUtils.updateWidget(context)
                }
            }
            is SubjectsUiEvent.DeleteSubject -> {
                viewModelScope.launch {
                    scheduleDao.deleteSubjectById(event.subjectId)
                    ScheduleWidgetUtils.updateWidget(context)
                }
            }
            is SubjectsUiEvent.NukeAllSubjects -> {
                viewModelScope.launch {
                    scheduleDao.deleteAllSubjects()
                    ScheduleWidgetUtils.updateWidget(context)
                }
            }
            is SubjectsUiEvent.ToggleSubjectSelection -> {
                _uiState.update { state ->
                    val updated = state.selectedSubjectIds.toMutableSet()
                    if (updated.contains(event.subjectId)) {
                        updated.remove(event.subjectId)
                    } else {
                        updated.add(event.subjectId)
                    }
                    state.copy(selectedSubjectIds = updated)
                }
            }
            is SubjectsUiEvent.ClearSelection -> {
                _uiState.update { it.copy(selectedSubjectIds = emptySet(), isSelectionModeForced = false) }
            }
            is SubjectsUiEvent.DeleteSelectedSubjects -> {
                viewModelScope.launch {
                    val idsToDelete = _uiState.value.selectedSubjectIds.toList()
                    scheduleDao.deleteSubjectsByIds(idsToDelete)
                    ScheduleWidgetUtils.updateWidget(context)
                    _uiState.update { it.copy(selectedSubjectIds = emptySet(), isSelectionModeForced = false) }
                }
            }
            is SubjectsUiEvent.EnterSelectionMode -> {
                _uiState.update { it.copy(isSelectionModeForced = true) }
            }
            is SubjectsUiEvent.ToggleGroupSelection -> {
                _uiState.update { state ->
                    val updated = state.selectedSubjectIds.toMutableSet()
                    val allSelected = event.subjectIds.isNotEmpty() && event.subjectIds.all { updated.contains(it) }
                    if (allSelected) {
                        updated.removeAll(event.subjectIds.toSet())
                    } else {
                        updated.addAll(event.subjectIds)
                    }
                    state.copy(selectedSubjectIds = updated)
                }
            }
            is SubjectsUiEvent.DisableSelectedSubjects -> {
                viewModelScope.launch {
                    val idsToDisable = _uiState.value.selectedSubjectIds.toList()
                    scheduleDao.updateSubjectsActiveStatus(idsToDisable, false)
                    ScheduleWidgetUtils.updateWidget(context)
                    _uiState.update { it.copy(selectedSubjectIds = emptySet(), isSelectionModeForced = false) }
                }
            }

        }
    }
}
