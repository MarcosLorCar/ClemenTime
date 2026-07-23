package com.github.marcoslorcar.clementime.ui.screens.subject

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.AttachedFileItem
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun AddEditSubjectScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSchedule: (DayOfWeek) -> Unit,
    viewModel: AddEditSubjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    AddEditSubjectContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToSchedule = onNavigateToSchedule,
        onUpdateCode = viewModel::updateCode,
        onUpdateName = viewModel::updateName,
        onUpdateColor = viewModel::updateColor,
        onUpdateNotesText = viewModel::updateNotesText,
        onAddAttachedFile = viewModel::addAttachedFile,
        onRemoveAttachedFile = viewModel::removeAttachedFile,
        onAddSlot = viewModel::addSlot,
        onDuplicateSlot = viewModel::duplicateSlot,
        onUpdateSlot = viewModel::updateSlot,
        onUpdateSelectedLabGroup = viewModel::updateSelectedLabGroup,
        onDeleteSlot = viewModel::deleteSlot,
        onStartTimeSelected = viewModel::onStartTimeSelected,
        onEndTimeSelected = viewModel::onEndTimeSelected,
        onSave = viewModel::saveSubject
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubjectContent(
    uiState: AddEditSubjectUiState,
    onNavigateBack: () -> Unit,
    onNavigateToSchedule: (DayOfWeek) -> Unit,
    onUpdateCode: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateColor: (Int) -> Unit,
    onUpdateNotesText: (String) -> Unit,
    onAddAttachedFile: (String, String, String) -> Unit,
    onRemoveAttachedFile: (String) -> Unit,
    onAddSlot: () -> Unit,
    onDuplicateSlot: (Int) -> Unit,
    onUpdateSlot: (Int, ClassSlotUiModel) -> Unit,
    onUpdateSelectedLabGroup: (String?) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onStartTimeSelected: (Int, LocalTime) -> Unit,
    onEndTimeSelected: (Int, LocalTime) -> Unit,
    onSave: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    var hasScrolled by remember { mutableStateOf(false) }
    var expandedSlotIndices by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(uiState.slots, uiState.highlightSlotId) {
        if (!hasScrolled && uiState.slots.isNotEmpty() && uiState.highlightSlotId != null) {
            val targetIndex = uiState.slots.indexOfFirst { it.id == uiState.highlightSlotId }
            if (targetIndex != -1) {
                hasScrolled = true
                lazyListState.animateScrollToItem(3 + targetIndex)
            }
        }
    }

    val context = LocalContext.current
    val attachmentFailedMessageTemplate = stringResource(R.string.attachment_failed_message)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolvedName = resolveFileName(context, uri)
            val fileType = context.contentResolver.getType(uri) ?: "Document"
            val fileTypeName = when {
                fileType.contains("pdf", ignoreCase = true) -> "PDF Document"
                fileType.contains("word", ignoreCase = true) || fileType.contains("officedocument", ignoreCase = true) -> "Word Document"
                fileType.contains("image", ignoreCase = true) -> "Image File"
                else -> "Document"
            }
            val uniqueId = UUID.randomUUID().toString()
            val savedFileName = "${uniqueId}_${resolvedName}"
            try {
                val targetFile = copyFileToInternalStorage(context, uri, savedFileName)
                onAddAttachedFile(resolvedName, fileTypeName, targetFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    attachmentFailedMessageTemplate.format(e.localizedMessage ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val defaultTitle = stringResource(
        if (uiState.subjectId == null || uiState.subjectId == 0L) R.string.add_subject_title else R.string.edit_subject_title
    )

    val topBarTitle = remember(uiState.subjectId, uiState.name, uiState.code, defaultTitle) {
        if (uiState.subjectId == null || uiState.subjectId == 0L) {
            defaultTitle
        } else {
            uiState.name.takeIf { it.isNotBlank() }
                ?: uiState.code.takeIf { it.isNotBlank() }
                ?: defaultTitle
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.ime,
        topBar = {
            ClemenTimeTopBar(
                title = topBarTitle,
                onNavigateBack = onNavigateBack,
                actions = {
                    Button(
                        enabled = uiState.code.isNotBlank() && uiState.name.isNotBlank(),
                        onClick = onSave,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save_button))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SubjectBasicDetailsCard(
                        code = uiState.code,
                        name = uiState.name,
                        selectedColor = uiState.color,
                        onUpdateCode = onUpdateCode,
                        onUpdateName = onUpdateName,
                        onUpdateColor = onUpdateColor
                    )
                }

                item {
                    SubjectNotesAndLinksCard(
                        notesText = uiState.notesText,
                        attachedFiles = uiState.attachedFiles,
                        onUpdateNotesText = onUpdateNotesText,
                        onRemoveAttachedFile = onRemoveAttachedFile,
                        onAddFileClick = { filePickerLauncher.launch("*/*") }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.class_slots_header, uiState.slots.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = onAddSlot) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_slot_button))
                        }
                    }
                }

                if (uiState.slots.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_slots_message),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(uiState.slots) { index, slot ->
                        ClassSlotItemCard(
                            slot = slot,
                            isHighlighted = slot.id == uiState.highlightSlotId,
                            isExpanded = expandedSlotIndices.contains(index),
                            selectedLabGroup = uiState.selectedLabGroup,
                            onToggleExpand = {
                                expandedSlotIndices = if (expandedSlotIndices.contains(index)) {
                                    expandedSlotIndices - index
                                } else {
                                    expandedSlotIndices + index
                                }
                            },
                            onGoToSchedule = onNavigateToSchedule,
                            onUpdateSlot = { updated -> onUpdateSlot(index, updated) },
                            onSelectLabGroup = onUpdateSelectedLabGroup,
                            onDuplicate = { onDuplicateSlot(index) },
                            onDelete = { onDeleteSlot(index) },
                            onStartTimeSelected = { time -> onStartTimeSelected(index, time) },
                            onEndTimeSelected = { time -> onEndTimeSelected(index, time) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }


}

@Composable
private fun SubjectBasicDetailsCard(
    code: String,
    name: String,
    selectedColor: Int,
    onUpdateCode: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateColor: (Int) -> Unit
) {
    var localCode by remember { mutableStateOf(code) }
    var localName by remember { mutableStateOf(name) }
    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        if (localCode != code) {
            localCode = code
        }
    }
    LaunchedEffect(name) {
        if (localName != name) {
            localName = name
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.edit_subject_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = localCode,
                    onValueChange = {
                        localCode = it
                        onUpdateCode(it)
                    },
                    label = { Text(stringResource(R.string.code_label)) },
                    placeholder = { Text(stringResource(R.string.code_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.35f)
                )
                OutlinedTextField(
                    value = localName,
                    onValueChange = {
                        localName = it
                        onUpdateName(it)
                    },
                    label = { Text(stringResource(R.string.name_label)) },
                    placeholder = { Text(stringResource(R.string.name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.65f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.color_badge_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.swipe_colors_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { showColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Custom Color Picker",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!Subject.PRESET_COLORS.contains(selectedColor)) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(selectedColor))
                                .border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { showColorPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected Custom Color",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                items(Subject.PRESET_COLORS) { colorInt ->
                    val isSelected = selectedColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                            .clickable { onUpdateColor(colorInt) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(selectedColor),
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onUpdateColor(color.toArgb())
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun SubjectNotesAndLinksCard(
    notesText: String,
    attachedFiles: List<AttachedFileItem>,
    onUpdateNotesText: (String) -> Unit,
    onRemoveAttachedFile: (String) -> Unit,
    onAddFileClick: () -> Unit
) {
    val context = LocalContext.current
    var localNotesText by remember { mutableStateOf(notesText) }

    LaunchedEffect(notesText) {
        if (localNotesText != notesText) {
            localNotesText = notesText
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.notes_and_attachments_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = localNotesText,
                onValueChange = {
                    localNotesText = it
                    onUpdateNotesText(it)
                },
                label = { Text(stringResource(R.string.subject_notes_label)) },
                placeholder = { Text(stringResource(R.string.subject_notes_placeholder)) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.linked_files_label, attachedFiles.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onAddFileClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.link_file_button))
                }
            }

            if (attachedFiles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachedFiles.forEach { file ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { openFile(context, file) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.fileType,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveAttachedFile(file.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove file",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassSlotItemCard(
    slot: ClassSlotUiModel,
    isHighlighted: Boolean = false,
    isExpanded: Boolean = false,
    selectedLabGroup: String? = null,
    onToggleExpand: () -> Unit,
    onGoToSchedule: (DayOfWeek) -> Unit,
    onUpdateSlot: (ClassSlotUiModel) -> Unit,
    onSelectLabGroup: (String?) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onStartTimeSelected: (LocalTime) -> Unit,
    onEndTimeSelected: (LocalTime) -> Unit
) {
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    var localClassroom by remember { mutableStateOf(slot.classroom ?: "") }
    var localProfessor by remember { mutableStateOf(slot.professor ?: "") }
    var localLabGroupName by remember { mutableStateOf(slot.labGroupName ?: "") }

    LaunchedEffect(slot.classroom) {
        val external = slot.classroom ?: ""
        if (localClassroom != external) {
            localClassroom = external
        }
    }
    LaunchedEffect(slot.professor) {
        val external = slot.professor ?: ""
        if (localProfessor != external) {
            localProfessor = external
        }
    }
    LaunchedEffect(slot.labGroupName) {
        val external = slot.labGroupName ?: ""
        if (localLabGroupName != external) {
            localLabGroupName = external
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .alpha(if (slot.isIgnored) 0.6f else 1f),
        shape = RoundedCornerShape(12.dp),
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors = if (isHighlighted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (slot.entryType == EntryType.LAB) MaterialTheme.colorScheme.secondaryContainer 
                                    else MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (slot.entryType == EntryType.LAB) stringResource(R.string.lab_label) else stringResource(R.string.theory_label),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (slot.entryType == EntryType.LAB) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (slot.labGroupName != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = slot.labGroupName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${
                            when (slot.dayOfWeek) {
                                DayOfWeek.MONDAY -> stringResource(R.string.mon_label)
                                DayOfWeek.TUESDAY -> stringResource(R.string.tue_label)
                                DayOfWeek.WEDNESDAY -> stringResource(R.string.wed_label)
                                DayOfWeek.THURSDAY -> stringResource(R.string.thu_label)
                                DayOfWeek.FRIDAY -> stringResource(R.string.fri_label)
                                else -> slot.dayOfWeek.name
                            }
                        } • " +
                                (if (slot.startTime != null && slot.endTime != null) 
                                    "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}"
                                else "--:-- - --:--"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (slot.entryType == EntryType.LAB) {
                        val isSelected = selectedLabGroup == slot.labGroupName && slot.labGroupName != null
                        RadioButton(
                            selected = isSelected,
                            onClick = { 
                                if (isSelected) onSelectLabGroup(null) 
                                else onSelectLabGroup(slot.labGroupName)
                            }
                        )
                    }

                    if (slot.isIgnored && !isExpanded) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Ignored",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp).size(20.dp)
                        )
                    }
                    
                    if (!isExpanded) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_slot_label),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = slot.entryType == EntryType.THEORY,
                            onClick = { onUpdateSlot(slot.copy(entryType = EntryType.THEORY)) },
                            label = { Text(stringResource(R.string.theory_label)) }
                        )
                        FilterChip(
                            selected = slot.entryType == EntryType.LAB,
                            onClick = { onUpdateSlot(slot.copy(entryType = EntryType.LAB)) },
                            label = { Text(stringResource(R.string.lab_label)) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { onUpdateSlot(slot.copy(isIgnored = !slot.isIgnored)) }) {
                            Icon(
                                imageVector = if (slot.isIgnored) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (slot.isIgnored) "Unignore slot" else "Ignore slot",
                                tint = if (slot.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) 
                                       else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (slot.id > 0L) {
                            IconButton(onClick = { onGoToSchedule(slot.dayOfWeek) }) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = stringResource(R.string.show_in_schedule_label),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = onDuplicate) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.duplicate_slot_label),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_slot_label),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                val days = remember {
                    listOf(
                        DayOfWeek.MONDAY to R.string.mon_label,
                        DayOfWeek.TUESDAY to R.string.tue_label,
                        DayOfWeek.WEDNESDAY to R.string.wed_label,
                        DayOfWeek.THURSDAY to R.string.thu_label,
                        DayOfWeek.FRIDAY to R.string.fri_label
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(days) { (day, labelRes) ->
                        FilterChip(
                            selected = slot.dayOfWeek == day,
                            onClick = { onUpdateSlot(slot.copy(dayOfWeek = day)) },
                            label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (slot.startTime != null) {
                                stringResource(R.string.start_time_label, slot.startTime.format(timeFormatter))
                            } else "Start: --:--"
                        )
                    }

                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (slot.endTime != null) {
                                stringResource(R.string.end_time_label, slot.endTime.format(timeFormatter))
                            } else "End: --:--"
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = localClassroom,
                        onValueChange = { text ->
                            localClassroom = text
                            onUpdateSlot(slot.copy(classroom = text.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.room_label)) },
                        placeholder = { Text(stringResource(R.string.room_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = localProfessor,
                        onValueChange = { text ->
                            localProfessor = text
                            onUpdateSlot(slot.copy(professor = text.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.professor_label)) },
                        placeholder = { Text(stringResource(R.string.professor_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (slot.entryType == EntryType.LAB) {
                    OutlinedTextField(
                        value = localLabGroupName,
                        onValueChange = { text ->
                            localLabGroupName = text
                            onUpdateSlot(slot.copy(labGroupName = text.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.lab_group_label)) },
                        placeholder = { Text(stringResource(R.string.lab_group_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showStartTimePicker) {
        RadialTimePickerDialog(
            initialTime = slot.startTime ?: LocalTime.of(9, 0),
            onDismiss = { showStartTimePicker = false },
            onTimeConfirm = { selectedTime ->
                onStartTimeSelected(selectedTime)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        RadialTimePickerDialog(
            initialTime = slot.endTime ?: (slot.startTime?.plusMinutes(90) ?: LocalTime.of(10, 30)),
            onDismiss = { showEndTimePicker = false },
            onTimeConfirm = { selectedTime ->
                onEndTimeSelected(selectedTime)
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadialTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onTimeConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onTimeConfirm(time)
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableIntStateOf((initialColor.red * 255).toInt()) }
    var green by remember { mutableIntStateOf((initialColor.green * 255).toInt()) }
    var blue by remember { mutableIntStateOf((initialColor.blue * 255).toInt()) }

    var hexText by remember {
        mutableStateOf(
            String.format("%02X%02X%02X", red, green, blue)
        )
    }

    val currentColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val sanitized = input.uppercase().take(6)
                        hexText = sanitized
                        if (sanitized.length == 6) {
                            runCatching {
                                val r = sanitized.substring(0, 2).toInt(16)
                                val g = sanitized.substring(2, 4).toInt(16)
                                val b = sanitized.substring(4, 6).toInt(16)
                                red = r
                                green = g
                                blue = b
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.hex_code_label)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("Red: $red", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = red.toFloat(),
                        onValueChange = {
                            red = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }

                Column {
                    Text("Green: $green", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = green.toFloat(),
                        onValueChange = {
                            green = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }

                Column {
                    Text("Blue: $blue", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = blue.toFloat(),
                        onValueChange = {
                            blue = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor) }) {
                Text(stringResource(R.string.select_color_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}



@Preview(showBackground = true)
@Composable
private fun AddEditSubjectContentPreview() {
    ClemenTimeTheme {
        AddEditSubjectContent(
            uiState = AddEditSubjectUiState(
                code = "SO",
                name = "Sistemas Operativos",
                slots = listOf(
                    ClassSlotUiModel(
                        id = 1L,
                        subjectId = 1L,
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = null,
                        endTime = null,
                        classroom = "Aula 1.2",
                        professor = "Dr. Smith"
                    )
                )
            ),
            onNavigateBack = {},
            onNavigateToSchedule = {},
            onUpdateCode = {},
            onUpdateName = {},
            onUpdateColor = {},
            onUpdateNotesText = {},
            onAddAttachedFile = { _, _, _ -> },
            onRemoveAttachedFile = {},
            onAddSlot = {},
            onDuplicateSlot = {},
            onUpdateSlot = { _, _ -> },
            onUpdateSelectedLabGroup = {},
            onDeleteSlot = {},
            onStartTimeSelected = { _, _ -> },
            onEndTimeSelected = { _, _ -> },
            onSave = {}
        )
    }
}

private fun resolveFileName(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name.isEmpty()) {
        name = uri.path?.substringAfterLast('/') ?: "file"
    }
    return name
}

private fun copyFileToInternalStorage(context: Context, sourceUri: Uri, targetFileName: String): File {
    val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
    val targetFile = File(attachmentsDir, targetFileName)
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(targetFile).use { output ->
            input.copyTo(output)
        }
    }
    return targetFile
}

private fun openFile(context: Context, fileItem: AttachedFileItem) {
    val file = File(fileItem.uriString)
    if (!file.exists()) {
        Toast.makeText(context, context.getString(R.string.file_not_found_message), Toast.LENGTH_SHORT).show()
        return
    }
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: getMimeType(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open File"))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.no_apps_for_file_message), Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeType(file: File): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

