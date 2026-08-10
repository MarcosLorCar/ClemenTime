package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.AttachedFileItem
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.ui.components.SemesterSwitcher
import com.marcoslorcar.clementime.utils.fadingEdges
import com.marcoslorcar.clementime.utils.isImageFile
import com.marcoslorcar.clementime.utils.isUriAccessible
import com.marcoslorcar.clementime.utils.openFile

@Composable
fun SubjectBasicDetailsCard(
    code: String,
    name: String,
    selectedColor: Int,
    selectedSemester: Int,
    isActive: Boolean,
    isEditMode: Boolean,
    onUpdateCode: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateColor: (Int) -> Unit,
    onUpdateSemester: (Int) -> Unit,
    onUpdateActive: (Boolean) -> Unit = {},
    showActiveToggle: Boolean = true
) {
    if (!isEditMode) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = name.ifBlank { stringResource(R.string.name_placeholder) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (code.isNotBlank()) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 1f else 0.6f)
                            )
                        }
                        Text(
                            text = stringResource(if (selectedSemester == 1) R.string.semester_1_label else R.string.semester_2_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 1f else 0.6f)
                        )
                    }

                    if (showActiveToggle) {
                        Switch(
                            checked = isActive,
                            onCheckedChange = onUpdateActive
                        )
                    }
                }
            }
        }
        return
    }

    var localCode by remember { mutableStateOf(code) }
    var localName by remember { mutableStateOf(name) }
    var showColorPicker by remember { mutableStateOf(false) }

    val colorsListState = rememberLazyListState()

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

    LaunchedEffect(selectedColor) {
        if (selectedColor != 0) {
            val presetIndex = Subject.PRESET_COLORS.indexOf(selectedColor)
            if (presetIndex != -1) {
                val targetIndex = if (!Subject.PRESET_COLORS.contains(selectedColor)) presetIndex + 2 else presetIndex + 1
                colorsListState.animateScrollToItem((targetIndex - 2).coerceAtLeast(0))
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = localName.isNotBlank() || localCode.isNotBlank(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                    modifier = Modifier.weight(0.35f)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = localName,
                    onValueChange = {
                        localName = it
                        onUpdateName(it)
                    },
                    label = { Text(stringResource(R.string.name_label)) },
                    placeholder = { Text(stringResource(R.string.name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(if (localName.isNotBlank() || localCode.isNotBlank()) 0.65f else 1f)
                )
            }

            SemesterSwitcher(
                selectedSemester = selectedSemester,
                onSemesterSelected = onUpdateSemester
            )

            if (showActiveToggle) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(if (isActive) R.string.active else R.string.inactive),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isActive) stringResource(R.string.visible_in_schedule) else stringResource(R.string.hidden_from_schedule),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = onUpdateActive
                    )
                }
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
            }

            LazyRow(
                state = colorsListState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges(colorsListState, horizontal = true)
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
                            contentDescription = stringResource(R.string.custom_color_picker_title),
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
                                contentDescription = stringResource(R.string.custom_color_picker_title),
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
            onColorSelected = { color: Color ->
                onUpdateColor(color.toArgb())
                showColorPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectNotesAndFilesSummary(
    notesText: String,
    attachedFiles: List<AttachedFileItem>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notes_and_files_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (notesText.isBlank() && attachedFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notes_and_files_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (notesText.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.notes_and_files_summary_notes)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        if (attachedFiles.isNotEmpty()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(pluralStringResource(R.plurals.notes_and_files_summary_files, attachedFiles.size, attachedFiles.size)) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesAndFilesBottomSheet(
    notesText: String,
    attachedFiles: List<AttachedFileItem>,
    onUpdateNotesText: (String) -> Unit,
    onRemoveAttachedFile: (String) -> Unit,
    onAddFileClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets.union(WindowInsets.ime) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 48.dp)
        ) {
            SubjectNotesAndLinksCard(
                notesText = notesText,
                attachedFiles = attachedFiles,
                onUpdateNotesText = onUpdateNotesText,
                onRemoveAttachedFile = onRemoveAttachedFile,
                onAddFileClick = onAddFileClick
            )
        }
    }
}

@Composable
fun SubjectNotesAndLinksCard(
    notesText: String,
    attachedFiles: List<AttachedFileItem>,
    onUpdateNotesText: (String) -> Unit,
    onRemoveAttachedFile: (String) -> Unit,
    onAddFileClick: () -> Unit
) {
    val context = LocalContext.current
    var notesList by remember(notesText) {
        mutableStateOf(if (notesText.isBlank()) listOf("") else notesText.split("\n\n"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.notes_and_attachments_header),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                notesList.forEachIndexed { index, note ->
                    OutlinedTextField(
                        value = note,
                        onValueChange = { newValue ->
                            val newList = notesList.toMutableList()
                            newList[index] = newValue
                            notesList = newList
                            onUpdateNotesText(newList.joinToString("\n\n"))
                        },
                        label = { Text(stringResource(R.string.subject_notes_label) + if (notesList.size > 1) " ${index + 1}" else "") },
                        placeholder = { Text(stringResource(R.string.subject_notes_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        trailingIcon = {
                            if (notesList.size > 1 || note.isNotBlank()) {
                                IconButton(onClick = {
                                    val newList = notesList.toMutableList()
                                    newList.removeAt(index)
                                    if (newList.isEmpty()) newList.add("")
                                    notesList = newList
                                    onUpdateNotesText(newList.joinToString("\n\n"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete_selected_confirm),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                }

                TextButton(
                    onClick = {
                        val newList = notesList.toMutableList()
                        newList.add("")
                        notesList = newList
                        onUpdateNotesText(newList.joinToString("\n\n"))
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add_note_button))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.linked_files_label, attachedFiles.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddFileClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.link_file_button))
                }
            }

            if (attachedFiles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachedFiles.forEach { file ->
                        val isImage = remember(file.uriString) { isImageFile(context, file.uriString) }
                        val isAccessible = remember(file.uriString) { isUriAccessible(context, file.uriString) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { openFile(context, file) }
                                ) {
                                    if (isImage && isAccessible) {
                                        AsyncImage(
                                            model = file.uriString,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isAccessible) Icons.Default.AttachFile else Icons.Default.FilePresent,
                                            contentDescription = null,
                                            tint = if (isAccessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isAccessible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = if (isAccessible) file.fileType else stringResource(R.string.file_not_found_message),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isAccessible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveAttachedFile(file.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete_subject_confirm),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.notes_and_files_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
