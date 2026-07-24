package com.marcoslorcar.clementime.ui.screens.conflictresolver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import com.marcoslorcar.clementime.utils.ConflictSolver
import com.marcoslorcar.clementime.utils.ScheduleSolution
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PreferenceMode {
    FREE_DAYS,
    COMPACTNESS
}

data class ConflictResolverUiState(
    val isLoading: Boolean = true,
    val solutions: List<ScheduleSolution> = emptyList(),
    val subjects: List<SubjectWithSlots> = emptyList(),
    val preferenceMode: PreferenceMode = PreferenceMode.FREE_DAYS,
    val canUndo: Boolean = false,
    val onboardingTooltipsEnabled: Boolean = true,
    val hasSeenPrioritiesTooltip: Boolean = false,
    val hasSeenApplyTooltip: Boolean = false
)

@HiltViewModel
class ConflictResolverViewModel @Inject constructor(
    private val dao: ScheduleDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context? = null
) : ViewModel() {

    private val _preferenceMode = MutableStateFlow(PreferenceMode.FREE_DAYS)
    private var lastAppliedLabSelections: Map<Long, String?>? = null

    val uiState: StateFlow<ConflictResolverUiState> = combine(
        dao.getAllSubjectsWithSlots(),
        _preferenceMode,
        settingsRepository.onboardingTooltipsEnabledFlow,
        settingsRepository.hasSeenResolverPrioritiesTooltipFlow,
        settingsRepository.hasSeenResolverApplyTooltipFlow
    ) { subjects, prefMode, onboardingEnabled, seenPriorities, seenApply ->
        try {
            val solutions = ConflictSolver.findSolutions(subjects)
            val mappedSolutions = solutions.map { solution ->
                val isCurrent = solution.labSelections.isNotEmpty() && solution.labSelections.all { (subId, groups) ->
                    val selected = subjects.find { it.subject.id == subId }?.subject?.selectedLabGroup
                    selected != null && groups.contains(selected)
                }
                solution.copy(isCurrent = isCurrent)
            }

            val sortedSolutions = when (prefMode) {
                PreferenceMode.FREE_DAYS -> mappedSolutions.sortedWith(
                    compareByDescending<ScheduleSolution> { it.isCurrent }
                        .thenBy { it.overlapsCount }
                        .thenByDescending { it.freeDaysCount }
                        .thenByDescending { it.compactnessScore }
                )
                PreferenceMode.COMPACTNESS -> mappedSolutions.sortedWith(
                    compareByDescending<ScheduleSolution> { it.isCurrent }
                        .thenBy { it.overlapsCount }
                        .thenByDescending { it.compactnessScore }
                        .thenByDescending { it.freeDaysCount }
                )
            }

            ConflictResolverUiState(
                isLoading = false,
                solutions = sortedSolutions,
                subjects = subjects,
                preferenceMode = prefMode,
                canUndo = lastAppliedLabSelections != null,
                onboardingTooltipsEnabled = onboardingEnabled,
                hasSeenPrioritiesTooltip = seenPriorities,
                hasSeenApplyTooltip = seenApply
            )
        } catch (_: Exception) {
            ConflictResolverUiState(
                isLoading = false,
                subjects = subjects,
                preferenceMode = prefMode,
                canUndo = lastAppliedLabSelections != null,
                onboardingTooltipsEnabled = onboardingEnabled,
                hasSeenPrioritiesTooltip = seenPriorities,
                hasSeenApplyTooltip = seenApply
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConflictResolverUiState()
    )

    fun setPreferenceMode(mode: PreferenceMode) {
        _preferenceMode.value = mode
    }

    fun applySolution(solution: ScheduleSolution) {
        val currentSubjects = uiState.value.subjects
        lastAppliedLabSelections = currentSubjects.associate { 
            it.subject.id to it.subject.selectedLabGroup 
        }

        viewModelScope.launch {
            val selections = solution.labSelections.mapValues { it.value.first() }
            dao.updateSelectedLabGroups(selections)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun undoLastApply() {
        val previous = lastAppliedLabSelections ?: return
        viewModelScope.launch {
            dao.updateSelectedLabGroups(previous)
            ScheduleWidgetUtils.updateWidget(context)
            lastAppliedLabSelections = null
        }
    }

    fun toggleSlotIgnored(slotId: Long, isIgnored: Boolean) {
        viewModelScope.launch {
            dao.updateSlotIgnoredStatus(slotId, isIgnored)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun selectLabGroup(subjectId: Long, labGroupName: String?) {
        viewModelScope.launch {
            dao.updateSelectedLabGroup(subjectId, labGroupName)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun markPrioritiesTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenResolverPrioritiesTooltip(true)
        }
    }

    fun markApplyTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenResolverApplyTooltip(true)
        }
    }
}

