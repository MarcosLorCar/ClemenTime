package com.marcoslorcar.clementime.ui.screens.subject

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcoslorcar.clementime.data.AttachedFileItem
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.model.toEntity
import com.marcoslorcar.clementime.ui.model.toUiModel
import com.marcoslorcar.clementime.ui.navigation.AddEditSubjectRoute
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

data class AddEditSubjectUiState(
    val subjectId: Long? = null,
    val highlightSlotId: Long? = null,
    val isEditMode: Boolean = true,
    val editingSlotIndex: Int? = null,
    val isSlotEditorOpen: Boolean = false,
    val isNewSubject: Boolean = false,
    val canSave: Boolean = false,
    val code: String = "",
    val name: String = "",
    val color: Int = Subject.PRESET_COLORS.first(),
    val semester: Int = 1,
    val isActive: Boolean = true,
    val defaultDurationMinutes: Int = 90,
    val notesText: String = "",
    val attachedFiles: List<AttachedFileItem> = emptyList(),
    val slots: List<ClassSlotUiModel> = emptyList(),
    val selectedLabGroup: String? = null,
    val isCodeManuallyEdited: Boolean = false,
    val onboardingTooltipsEnabled: Boolean = true,
    val hasSeenAddSlotTooltip: Boolean = false,
    val hasSeenLabSelectionTooltip: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val dayStartTime: LocalTime = LocalTime.of(8, 30),
    val dayEndTime: LocalTime = LocalTime.of(21, 30)
)

@HiltViewModel
class AddEditSubjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleDao: ScheduleDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditSubjectUiState())
    val uiState: StateFlow<AddEditSubjectUiState> = _uiState.asStateFlow()

    init {
        val route = runCatching { savedStateHandle.toRoute<AddEditSubjectRoute>() }.getOrNull()
        val routeSubjectId = route?.subjectId
        val highlightSlotId = route?.highlightSlotId

        if (routeSubjectId != null && routeSubjectId > 0) {
            _uiState.update { it.copy(isEditMode = false, isNewSubject = false) }
            loadSubject(routeSubjectId, highlightSlotId)
        } else {
            _uiState.update { it.copy(isEditMode = true, isNewSubject = true) }
        }

        viewModelScope.launch {
            settingsRepository.onboardingTooltipsEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(onboardingTooltipsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hasSeenAddSlotTooltipFlow.collect { seen ->
                _uiState.update { it.copy(hasSeenAddSlotTooltip = seen) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hasSeenLabSelectionTooltipFlow.collect { seen ->
                _uiState.update { it.copy(hasSeenLabSelectionTooltip = seen) }
            }
        }
        viewModelScope.launch {
            settingsRepository.currentSemesterFlow.firstOrNull()?.let { semester ->
                _uiState.update { it.copy(semester = semester) }
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.dayStartHourFlow,
                settingsRepository.dayStartMinuteFlow,
                settingsRepository.dayEndHourFlow,
                settingsRepository.dayEndMinuteFlow
            ) { sh, sm, eh, em ->
                LocalTime.of(sh, sm) to LocalTime.of(eh, em)
            }.collect { (start, end) ->
                _uiState.update { it.copy(dayStartTime = start, dayEndTime = end) }
            }
        }
    }

    private fun loadSubject(subjectId: Long, highlightSlotId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, subjectId = subjectId, highlightSlotId = highlightSlotId) }
            val subjectWithSlots = scheduleDao.getSubjectWithSlotsById(subjectId).firstOrNull()
            if (subjectWithSlots != null) {
                val subject = subjectWithSlots.subject
                _uiState.update {
                    it.copy(
                        subjectId = subject.id,
                        code = subject.code,
                        name = subject.name,
                        color = subject.color,
                        semester = subject.semester,
                        isActive = subject.isActive,
                        defaultDurationMinutes = subject.defaultDurationMinutes ?: 90,
                        notesText = subject.notes,
                        attachedFiles = subject.attachedFiles,
                        slots = subjectWithSlots.slots.map { slot -> slot.toUiModel() },
                        selectedLabGroup = subject.selectedLabGroup,
                        canSave = true,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun openSlotEditor(index: Int?) {
        _uiState.update { it.copy(editingSlotIndex = index, isSlotEditorOpen = true) }
    }

    fun closeSlotEditor() {
        _uiState.update { it.copy(editingSlotIndex = null, isSlotEditorOpen = false) }
    }

    fun saveSlotFromEditor(slot: ClassSlotUiModel) {
        val index = _uiState.value.editingSlotIndex
        if (index != null && index in _uiState.value.slots.indices) {
            updateSlot(index, slot)
        } else {
            _uiState.update { it.copy(slots = it.slots + slot) }
        }
        closeSlotEditor()
    }

    fun markLabSelectionTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenLabSelectionTooltip(true)
        }
    }

    fun selectLabGroup(groupName: String?) {
        _uiState.update { it.copy(selectedLabGroup = groupName) }
        // Auto-save when selection changes in view mode
        if (!_uiState.value.isEditMode) {
            saveSubject(shouldExit = false)
        }
    }

    fun updateCode(code: String) {
        _uiState.update { it.copy(code = code, isCodeManuallyEdited = code.isNotBlank()) }
    }

    fun updateName(name: String) {
        _uiState.update { state ->
            val newCode = if (!state.isCodeManuallyEdited) {
                name.split(" ")
                    .filter { it.length > 2 }
                    .joinToString("") { it.take(3).uppercase() }
            } else {
                state.code
            }
            state.copy(
                name = name,
                code = newCode,
                canSave = name.isNotBlank()
            )
        }
    }

    fun updateColor(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    fun updateSemester(semester: Int) {
        _uiState.update { it.copy(semester = semester) }
    }

    fun updateActive(isActive: Boolean) {
        _uiState.update { it.copy(isActive = isActive) }
    }

    fun updateDefaultDuration(durationMinutes: Int) {
        if (durationMinutes > 0) {
            _uiState.update { it.copy(defaultDurationMinutes = durationMinutes) }
        }
    }

    fun updateNotesText(notes: String) {
        _uiState.update { it.copy(notesText = notes) }
    }

    fun addAttachedFile(name: String, fileType: String, uriString: String) {
        if (name.isNotBlank()) {
            val fileItem = AttachedFileItem(name = name.trim(), fileType = fileType, uriString = uriString)
            _uiState.update { it.copy(attachedFiles = it.attachedFiles + fileItem) }
        }
    }

    fun removeAttachedFile(id: String) {
        _uiState.update { state ->
            state.copy(attachedFiles = state.attachedFiles.filter { it.id != id })
        }
    }

    fun addSlot() {
        val lastSlot = _uiState.value.slots.lastOrNull()

        val newSlot = ClassSlotUiModel(
            id = 0L,
            subjectId = _uiState.value.subjectId ?: 0L,
            dayOfWeek = lastSlot?.dayOfWeek ?: DayOfWeek.MONDAY,
            startTime = null,
            endTime = null,
            classroom = lastSlot?.classroom,
            labGroupName = lastSlot?.labGroupName,
            entryType = lastSlot?.entryType ?: EntryType.THEORY,
            professor = lastSlot?.professor
        )

        _uiState.update { it.copy(slots = it.slots + newSlot) }
    }

    fun duplicateSlot(index: Int) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            val slotToCopy = currentSlots[index]
            val duplicatedSlot = slotToCopy.copy(id = 0L)
            _uiState.update {
                val updatedList = it.slots.toMutableList()
                updatedList.add(index + 1, duplicatedSlot)
                it.copy(slots = updatedList)
            }
        }
    }

    fun updateSlot(index: Int, updatedSlot: ClassSlotUiModel) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            _uiState.update { state ->
                val updatedList = state.slots.toMutableList()
                updatedList[index] = updatedSlot
                state.copy(slots = updatedList)
            }
        }
    }

    fun deleteSlot(index: Int) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            _uiState.update { state ->
                val updatedList = state.slots.toMutableList()
                updatedList.removeAt(index)
                state.copy(slots = updatedList)
            }
        }
    }

    fun onStartTimeSelected(index: Int, newStartTime: LocalTime) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            val slot = currentSlots[index]
            val duration = _uiState.value.defaultDurationMinutes.toLong()

            val calculatedEndTime = if (slot.endTime == null) {
                newStartTime.plusMinutes(duration)
            } else if (newStartTime.isBefore(slot.endTime)) {
                val newDuration = Duration.between(newStartTime, slot.endTime).toMinutes().toInt()
                if (newDuration > 0) {
                    _uiState.update { it.copy(defaultDurationMinutes = newDuration) }
                }
                slot.endTime
            } else {
                newStartTime.plusMinutes(duration)
            }

            val updatedSlot = slot.copy(startTime = newStartTime, endTime = calculatedEndTime)
            updateSlot(index, updatedSlot)
        }
    }

    fun onEndTimeSelected(index: Int, newEndTime: LocalTime) {
        val currentSlots = _uiState.value.slots
        if (index in currentSlots.indices) {
            val slot = currentSlots[index]
            val duration = _uiState.value.defaultDurationMinutes.toLong()

            val calculatedStartTime = if (slot.startTime == null) {
                newEndTime.minusMinutes(duration)
            } else if (newEndTime.isAfter(slot.startTime)) {
                val newDuration = Duration.between(slot.startTime, newEndTime).toMinutes().toInt()
                if (newDuration > 0) {
                    _uiState.update { it.copy(defaultDurationMinutes = newDuration) }
                }
                slot.startTime
            } else {
                newEndTime.minusMinutes(duration)
            }

            val updatedSlot = slot.copy(startTime = calculatedStartTime, endTime = newEndTime)
            updateSlot(index, updatedSlot)
        }
    }

    fun saveSubject(shouldExit: Boolean = true) {
        val state = _uiState.value
        if (state.code.isBlank() || state.name.isBlank()) return

        viewModelScope.launch {
            val existingSubject = state.subjectId?.let { id ->
                scheduleDao.getSubjectWithSlotsById(id).firstOrNull()?.subject
            }

            val subjectToSave = Subject(
                id = state.subjectId ?: 0L,
                code = state.code.trim(),
                name = state.name.trim(),
                color = state.color,
                courseGroup = existingSubject?.courseGroup,
                isActive = state.isActive,
                defaultDurationMinutes = state.defaultDurationMinutes,
                notes = state.notesText,
                attachedFiles = state.attachedFiles,
                selectedLabGroup = state.selectedLabGroup,
                semester = state.semester
            )

            val validEntities = state.slots.mapNotNull { it.toEntity(subjectToSave.id) }

            scheduleDao.upsertSubjectWithSlots(subjectToSave, validEntities)
            ScheduleWidgetUtils.updateWidget(context)
            if (shouldExit) {
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }
}
