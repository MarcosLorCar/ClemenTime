package com.example.clementime.ui.screens.matter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.clementime.data.AttachedFileItem
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.ScheduleDao
import com.example.clementime.ui.navigation.AddEditMatterRoute
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
    val matterId: Long = 0L,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val classroom: String? = null,
    val labGroupName: String? = null,
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null
)

fun ClassSlot.toUiModel(): ClassSlotUiModel = ClassSlotUiModel(
    id = id,
    matterId = matterId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    classroom = classroom,
    labGroupName = labGroupName,
    entryType = entryType,
    professor = professor
)

fun ClassSlotUiModel.toEntity(matterId: Long): ClassSlot? {
    val start = startTime ?: return null
    val end = endTime ?: return null
    return ClassSlot(
        id = id,
        matterId = matterId,
        dayOfWeek = dayOfWeek,
        startTime = start,
        endTime = end,
        classroom = classroom,
        labGroupName = labGroupName,
        entryType = entryType,
        professor = professor
    )
}

data class AddEditMatterUiState(
    val matterId: Long? = null,
    val highlightSlotId: Long? = null,
    val code: String = "",
    val name: String = "",
    val color: Int = Matter.PRESET_COLORS.first(),
    val defaultDurationMinutes: Int = 90,
    val notesText: String = "",
    val attachedFiles: List<AttachedFileItem> = emptyList(),
    val slots: List<ClassSlotUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AddEditMatterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditMatterUiState())
    val uiState: StateFlow<AddEditMatterUiState> = _uiState.asStateFlow()

    init {
        val route = runCatching { savedStateHandle.toRoute<AddEditMatterRoute>() }.getOrNull()
        val routeMatterId = route?.matterId
        val highlightSlotId = route?.highlightSlotId

        if (routeMatterId != null && routeMatterId > 0) {
            loadMatter(routeMatterId, highlightSlotId)
        }
    }

    private fun loadMatter(matterId: Long, highlightSlotId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, matterId = matterId, highlightSlotId = highlightSlotId) }
            val matterWithSlots = scheduleDao.getMatterWithSlotsById(matterId).firstOrNull()
            if (matterWithSlots != null) {
                val matter = matterWithSlots.matter
                _uiState.update {
                    it.copy(
                        matterId = matter.id,
                        code = matter.code,
                        name = matter.name,
                        color = matter.color,
                        defaultDurationMinutes = matter.defaultDurationMinutes ?: 90,
                        notesText = matter.notes,
                        attachedFiles = matter.attachedFiles,
                        slots = matterWithSlots.slots.map { slot -> slot.toUiModel() },
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
            matterId = _uiState.value.matterId ?: 0L,
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

    fun saveMatter() {
        val state = _uiState.value
        if (state.code.isBlank() || state.name.isBlank()) return

        viewModelScope.launch {
            val existingMatter = state.matterId?.let { id ->
                scheduleDao.getMatterWithSlotsById(id).firstOrNull()?.matter
            }

            val matterToSave = Matter(
                id = state.matterId ?: 0L,
                code = state.code.trim(),
                name = state.name.trim(),
                color = state.color,
                courseGroup = existingMatter?.courseGroup,
                isActive = existingMatter?.isActive ?: true,
                defaultDurationMinutes = state.defaultDurationMinutes,
                notes = state.notesText,
                attachedFiles = state.attachedFiles
            )

            val validEntities = state.slots.mapNotNull { it.toEntity(matterToSave.id) }

            scheduleDao.upsertMatterWithSlots(matterToSave, validEntities)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
