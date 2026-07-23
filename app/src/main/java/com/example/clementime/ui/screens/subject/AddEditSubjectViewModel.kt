package com.example.clementime.ui.screens.subject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.clementime.data.AttachedFileItem
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Subject
import com.example.clementime.data.ScheduleDao
import com.example.clementime.ui.navigation.AddEditSubjectRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

data class ClassSlotUiModel(
    val id: Long = 0L,
    val subjectId: Long = 0L,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val classroom: String? = null,
    val labGroupName: String? = null,
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null,
    val isIgnored: Boolean = false
)

fun ClassSlot.toUiModel(): ClassSlotUiModel = ClassSlotUiModel(
    id = id,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    classroom = classroom,
    labGroupName = labGroupName,
    entryType = entryType,
    professor = professor,
    isIgnored = isIgnored
)

fun ClassSlotUiModel.toEntity(subjectId: Long): ClassSlot? {
    val start = startTime ?: return null
    val end = endTime ?: return null
    return ClassSlot(
        id = id,
        subjectId = subjectId,
        dayOfWeek = dayOfWeek,
        startTime = start,
        endTime = end,
        classroom = classroom,
        labGroupName = labGroupName,
        entryType = entryType,
        professor = professor,
        isIgnored = isIgnored
    )
}

data class AddEditSubjectUiState(
    val subjectId: Long? = null,
    val highlightSlotId: Long? = null,
    val code: String = "",
    val name: String = "",
    val color: Int = Subject.PRESET_COLORS.first(),
    val defaultDurationMinutes: Int = 90,
    val notesText: String = "",
    val attachedFiles: List<AttachedFileItem> = emptyList(),
    val slots: List<ClassSlotUiModel> = emptyList(),
    val selectedLabGroup: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AddEditSubjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditSubjectUiState())
    val uiState: StateFlow<AddEditSubjectUiState> = _uiState.asStateFlow()

    init {
        val route = runCatching { savedStateHandle.toRoute<AddEditSubjectRoute>() }.getOrNull()
        val routeSubjectId = route?.subjectId
        val highlightSlotId = route?.highlightSlotId

        if (routeSubjectId != null && routeSubjectId > 0) {
            loadSubject(routeSubjectId, highlightSlotId)
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
                        defaultDurationMinutes = subject.defaultDurationMinutes ?: 90,
                        notesText = subject.notes,
                        attachedFiles = subject.attachedFiles,
                        slots = subjectWithSlots.slots.map { slot -> slot.toUiModel() },
                        selectedLabGroup = subject.selectedLabGroup,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateSelectedLabGroup(groupName: String?) {
        _uiState.update { it.copy(selectedLabGroup = groupName) }
    }

    fun updateCode(code: String) {
        _uiState.update { it.copy(code = code) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateColor(color: Int) {
        _uiState.update { it.copy(color = color) }
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

    fun saveSubject() {
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
                isActive = existingSubject?.isActive ?: true,
                defaultDurationMinutes = state.defaultDurationMinutes,
                notes = state.notesText,
                attachedFiles = state.attachedFiles,
                selectedLabGroup = state.selectedLabGroup
            )

            val validEntities = state.slots.mapNotNull { it.toEntity(subjectToSave.id) }

            scheduleDao.upsertSubjectWithSlots(subjectToSave, validEntities)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
