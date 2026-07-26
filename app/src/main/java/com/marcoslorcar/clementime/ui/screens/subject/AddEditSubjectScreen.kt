package com.marcoslorcar.clementime.ui.screens.subject

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.ui.components.ClassSlotItemCard
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.model.toEntity
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.marcoslorcar.clementime.utils.resolveFileName
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AddEditSubjectScreen(
    onBack: () -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit,
    viewModel: AddEditSubjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    AddEditSubjectContent(
        uiState = uiState,
        onBack = onBack,
        onNavigateToSchedule = onNavigateToSchedule,
        onUpdateCode = viewModel::updateCode,
        onUpdateName = viewModel::updateName,
        onUpdateColor = viewModel::updateColor,
        onUpdateSemester = viewModel::updateSemester,
        onUpdateNotesText = viewModel::updateNotesText,
        onUpdateActive = viewModel::updateActive,
        onAddAttachedFile = viewModel::addAttachedFile,
        onRemoveAttachedFile = viewModel::removeAttachedFile,
        onDeleteSlot = viewModel::deleteSlot,
        onAddSlot = viewModel::addSlot,
        onDuplicateSlot = viewModel::duplicateSlot,
        onSaveSubject = viewModel::saveSubject,
        onToggleEditMode = viewModel::toggleEditMode,
        onOpenSlotEditor = viewModel::openSlotEditor,
        onCloseSlotEditor = viewModel::closeSlotEditor,
        onSaveSlotFromEditor = viewModel::saveSlotFromEditor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubjectContent(
    uiState: AddEditSubjectUiState,
    onBack: () -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit,
    onUpdateCode: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateColor: (Int) -> Unit,
    onUpdateSemester: (Int) -> Unit,
    onUpdateNotesText: (String) -> Unit,
    onUpdateActive: (Boolean) -> Unit,
    onAddAttachedFile: (String, String, String) -> Unit,
    onRemoveAttachedFile: (String) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onAddSlot: () -> Unit,
    onDuplicateSlot: (Int) -> Unit,
    onSaveSubject: () -> Unit,
    onToggleEditMode: () -> Unit,
    onOpenSlotEditor: (Int?) -> Unit,
    onCloseSlotEditor: () -> Unit,
    onSaveSlotFromEditor: (ClassSlotUiModel) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showNotesSheet by remember { mutableStateOf(false) }

    val previewSlots = remember(uiState.slots, uiState.subjectId, uiState.code, uiState.name, uiState.color, uiState.isActive, uiState.semester) {
        val subject = Subject(
            id = uiState.subjectId ?: 0L,
            code = uiState.code,
            name = uiState.name,
            color = uiState.color,
            isActive = uiState.isActive,
            semester = uiState.semester
        )
        uiState.slots.mapNotNull { it.toEntity(subject.id)?.let { entity -> subject to entity } }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val fileName = resolveFileName(context, it)
            val mimeType = context.contentResolver.getType(it) ?: "File"
            onAddAttachedFile(fileName, mimeType, it.toString())
        }
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(if (uiState.isEditMode) R.string.edit_subject_title else R.string.add_subject_title),
                onNavigateBack = onBack,
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(onClick = onSaveSubject) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_button))
                        }
                    } else {
                        IconButton(onClick = onToggleEditMode) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_subject))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isEditMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddSlot,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_slot_button)) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SubjectBasicDetailsCard(
                code = uiState.code,
                name = uiState.name,
                selectedColor = uiState.color,
                selectedSemester = uiState.semester,
                isActive = uiState.isActive,
                isEditMode = uiState.isEditMode,
                onUpdateCode = onUpdateCode,
                onUpdateName = onUpdateName,
                onUpdateColor = onUpdateColor,
                onUpdateSemester = onUpdateSemester,
                onUpdateActive = onUpdateActive
            )

            ScheduleMiniPreview(
                slots = previewSlots,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            SubjectNotesAndFilesSummary(
                notesText = uiState.notesText,
                attachedFiles = uiState.attachedFiles,
                onClick = { showNotesSheet = true }
            )

            Text(
                text = stringResource(R.string.class_slots_header, uiState.slots.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (uiState.slots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_slots_assigned),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                uiState.slots.forEachIndexed { index, slot ->
                    ClassSlotItemCard(
                        slot = slot,
                        isHighlighted = slot.id == uiState.highlightSlotId,
                        onEditClick = { onOpenSlotEditor(index) },
                        onGoToSchedule = { day, id -> onNavigateToSchedule(day, id) },
                        onDuplicate = { onDuplicateSlot(index) },
                        onDelete = { onDeleteSlot(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (uiState.isSlotEditorOpen) {
        val editingSlot = uiState.editingSlotIndex?.let { uiState.slots.getOrNull(it) }
            ?: ClassSlotUiModel(
                subjectId = uiState.subjectId ?: 0L,
                dayOfWeek = uiState.slots.lastOrNull()?.dayOfWeek ?: DayOfWeek.MONDAY,
                classroom = uiState.slots.lastOrNull()?.classroom,
                labGroupName = uiState.slots.lastOrNull()?.labGroupName,
                entryType = uiState.slots.lastOrNull()?.entryType ?: com.marcoslorcar.clementime.data.EntryType.THEORY,
                professor = uiState.slots.lastOrNull()?.professor
            )

        SlotEditBottomSheet(
            initialSlot = editingSlot,
            onDismiss = onCloseSlotEditor,
            onSaveSlot = onSaveSlotFromEditor,
            onDelete = uiState.editingSlotIndex?.let { index -> { onDeleteSlot(index); onCloseSlotEditor() } }
        )
    }

    if (showNotesSheet) {
        NotesAndFilesBottomSheet(
            notesText = uiState.notesText,
            attachedFiles = uiState.attachedFiles,
            onUpdateNotesText = onUpdateNotesText,
            onRemoveAttachedFile = onRemoveAttachedFile,
            onAddFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showNotesSheet = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditSubjectContentPreview() {
    ClemenTimeTheme {
        AddEditSubjectContent(
            uiState = AddEditSubjectUiState(
                name = "Mathematics",
                code = "MATH101",
                color = Color.Blue.toArgb(),
                slots = listOf(
                    ClassSlotUiModel(dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0))
                )
            ),
            onBack = {},
            onNavigateToSchedule = { _, _ -> },
            onUpdateCode = {},
            onUpdateName = {},
            onUpdateColor = {},
            onUpdateSemester = {},
            onUpdateNotesText = {},
            onUpdateActive = {},
            onAddAttachedFile = { _, _, _ -> },
            onRemoveAttachedFile = {},
            onDeleteSlot = {},
            onAddSlot = {},
            onDuplicateSlot = {},
            onSaveSubject = {},
            onToggleEditMode = {},
            onOpenSlotEditor = {},
            onCloseSlotEditor = {},
            onSaveSlotFromEditor = {}
        )
    }
}
