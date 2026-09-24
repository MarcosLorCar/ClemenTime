package com.marcoslorcar.clementime.ui.screens.subject

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.net.Uri
import android.widget.Toast
import com.marcoslorcar.clementime.R
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
import com.marcoslorcar.clementime.utils.copyUriToInternalAttachments
import com.marcoslorcar.clementime.utils.deleteInternalAttachment
import com.marcoslorcar.clementime.utils.deleteOriginalDocument
import com.marcoslorcar.clementime.utils.getFriendlyFileType
import com.marcoslorcar.clementime.utils.resolveFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

data class DeleteOriginalPrompt(
    val uri: Uri,
    val fileName: String
)

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
    val dayEndTime: LocalTime = LocalTime.of(21, 30),
    val deleteOriginalPrompt: DeleteOriginalPrompt? = null
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
        val routeSubjectId = route?.subjectId ?: savedStateHandle.get<Long>("subjectId")
        val highlightSlotId = route?.highlightSlotId ?: savedStateHandle.get<Long>("highlightSlotId")

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
        if (!_uiState.value.isEditMode) {
            saveSubject(shouldExit = false)
        }
    }

    fun updateNotesText(notes: String) {
        _uiState.update { it.copy(notesText = notes) }
    }

    fun addAttachedFile(sourceUri: Uri) {
        val ctx = context
        if (ctx == null) {
            val fileName = sourceUri.lastPathSegment ?: "file"
            addAttachedFile(name = fileName, fileType = "File", uriString = sourceUri.toString())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val originalName = resolveFileName(ctx, sourceUri)
            val copyResult = copyUriToInternalAttachments(ctx, sourceUri, originalName)
            val fileItem = if (copyResult != null) {
                val (file, sizeBytes) = copyResult
                val friendlyType = getFriendlyFileType(originalName, ctx.contentResolver.getType(sourceUri) ?: "")
                AttachedFileItem(
                    name = originalName.trim(),
                    fileType = friendlyType,
                    uriString = file.absolutePath,
                    fileSizeBytes = sizeBytes
                )
            } else {
                val mimeType = ctx.contentResolver.getType(sourceUri) ?: "File"
                val friendlyType = getFriendlyFileType(originalName, mimeType)
                AttachedFileItem(
                    name = originalName.trim(),
                    fileType = friendlyType,
                    uriString = sourceUri.toString(),
                    fileSizeBytes = null
                )
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        attachedFiles = it.attachedFiles + fileItem,
                        deleteOriginalPrompt = if (copyResult != null) DeleteOriginalPrompt(sourceUri, originalName) else null
                    )
                }
                if (!_uiState.value.isEditMode) {
                    saveSubject(shouldExit = false)
                }
            }
        }
    }

    fun confirmDeleteOriginal() {
        val prompt = _uiState.value.deleteOriginalPrompt ?: return
        val ctx = context
        _uiState.update { it.copy(deleteOriginalPrompt = null) }
        if (ctx != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val deleted = deleteOriginalDocument(ctx, prompt.uri)
                withContext(Dispatchers.Main) {
                    val msgRes = if (deleted) {
                        R.string.original_file_deleted_message
                    } else {
                        R.string.original_file_delete_failed_message
                    }
                    Toast.makeText(ctx, ctx.getString(msgRes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun dismissDeleteOriginalPrompt() {
        _uiState.update { it.copy(deleteOriginalPrompt = null) }
    }

    fun addAttachedFile(name: String, fileType: String, uriString: String, fileSizeBytes: Long? = null) {
        if (name.isNotBlank()) {
            val fileItem = AttachedFileItem(
                name = name.trim(),
                fileType = fileType,
                uriString = uriString,
                fileSizeBytes = fileSizeBytes
            )
            _uiState.update { it.copy(attachedFiles = it.attachedFiles + fileItem) }
            if (!_uiState.value.isEditMode) {
                saveSubject(shouldExit = false)
            }
        }
    }

    fun removeAttachedFile(id: String) {
        val itemToRemove = _uiState.value.attachedFiles.find { it.id == id }
        if (itemToRemove != null && context != null) {
            val ctx = context
            viewModelScope.launch(Dispatchers.IO) {
                deleteInternalAttachment(ctx, itemToRemove)
            }
        }
        _uiState.update { state ->
            state.copy(attachedFiles = state.attachedFiles.filter { it.id != id })
        }
        if (!_uiState.value.isEditMode) {
            saveSubject(shouldExit = false)
        }
    }

    fun renameAttachedFile(id: String, newName: String) {
        if (newName.isBlank()) return
        _uiState.update { state ->
            state.copy(
                attachedFiles = state.attachedFiles.map {
                    if (it.id == id) it.copy(name = newName.trim()) else it
                }
            )
        }
        if (!_uiState.value.isEditMode) {
            saveSubject(shouldExit = false)
        }
    }

    fun saveSubjectWithoutExit() {
        saveSubject(shouldExit = false)
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
