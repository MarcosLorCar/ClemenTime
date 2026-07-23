package com.example.clementime.ui.screens.conflictresolver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.SubjectWithSlots
import com.example.clementime.utils.ConflictSolver
import com.example.clementime.utils.ScheduleSolution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConflictResolverUiState(
    val isLoading: Boolean = true,
    val solutions: List<ScheduleSolution> = emptyList(),
    val subjects: List<SubjectWithSlots> = emptyList()
)

@HiltViewModel
class ConflictResolverViewModel @Inject constructor(
    private val dao: ScheduleDao
) : ViewModel() {

    val uiState: StateFlow<ConflictResolverUiState> = dao.getAllSubjectsWithSlots()
        .map { subjects ->
            try {
                val solutions = ConflictSolver.findSolutions(subjects)
                val mappedSolutions = solutions.map { solution ->
                    val isCurrent = solution.labSelections.isNotEmpty() && solution.labSelections.all { (subId, groups) ->
                        val selected = subjects.find { it.subject.id == subId }?.subject?.selectedLabGroup
                        selected != null && groups.contains(selected)
                    }
                    solution.copy(isCurrent = isCurrent)
                }.sortedByDescending { it.isCurrent }

                ConflictResolverUiState(
                    isLoading = false,
                    solutions = mappedSolutions,
                    subjects = subjects
                )
            } catch (e: Exception) {
                ConflictResolverUiState(isLoading = false, subjects = subjects)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConflictResolverUiState()
        )

    fun applySolution(solution: ScheduleSolution) {
        viewModelScope.launch {
            val selections = solution.labSelections.mapValues { it.value.first() }
            dao.updateSelectedLabGroups(selections)
        }
    }
    
    fun toggleSlotIgnored(slotId: Long, isIgnored: Boolean) {
        viewModelScope.launch {
            dao.updateSlotIgnoredStatus(slotId, isIgnored)
        }
    }

    fun selectLabGroup(subjectId: Long, labGroupName: String?) {
        viewModelScope.launch {
            dao.updateSelectedLabGroup(subjectId, labGroupName)
        }
    }
}
