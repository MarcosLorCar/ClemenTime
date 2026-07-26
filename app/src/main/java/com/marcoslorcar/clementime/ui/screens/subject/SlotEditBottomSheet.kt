package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.screens.schedule.ScheduleTab
import com.marcoslorcar.clementime.utils.shortName
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditBottomSheet(
    initialSlot: ClassSlotUiModel,
    onDismiss: () -> Unit,
    onSaveSlot: (ClassSlotUiModel) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var editedSlot by remember(initialSlot) { mutableStateOf(initialSlot) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showIgnoreHelp by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (editedSlot.id == 0L) R.string.slot_editor_title_add else R.string.slot_editor_title_edit
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Entry Type Chips (Theory vs Lab)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = editedSlot.entryType == EntryType.THEORY,
                    onClick = { editedSlot = editedSlot.copy(entryType = EntryType.THEORY) },
                    label = { Text(stringResource(R.string.theory_label)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = editedSlot.entryType == EntryType.LAB,
                    onClick = { editedSlot = editedSlot.copy(entryType = EntryType.LAB) },
                    label = { Text(stringResource(R.string.lab_label)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Day of Week Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.day_of_week_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ScheduleTab.entries) { tab ->
                        FilterChip(
                            selected = editedSlot.dayOfWeek == tab.dayOfWeek,
                            onClick = { editedSlot = editedSlot.copy(dayOfWeek = tab.dayOfWeek) },
                            label = { Text(tab.dayOfWeek.shortName(locale), fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Time Pickers (Start & End)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (editedSlot.startTime != null) {
                            stringResource(R.string.start_time_label, editedSlot.startTime!!.format(timeFormatter))
                        } else stringResource(R.string.start_time_empty)
                    )
                }

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (editedSlot.endTime != null) {
                            stringResource(R.string.end_time_label, editedSlot.endTime!!.format(timeFormatter))
                        } else stringResource(R.string.end_time_empty)
                    )
                }
            }

            // Classroom & Professor TextFields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editedSlot.classroom ?: "",
                    onValueChange = { editedSlot = editedSlot.copy(classroom = it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.room_label)) },
                    placeholder = { Text(stringResource(R.string.room_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editedSlot.professor ?: "",
                    onValueChange = { editedSlot = editedSlot.copy(professor = it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.professor_label)) },
                    placeholder = { Text(stringResource(R.string.professor_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Lab Group TextField (if LAB)
            if (editedSlot.entryType == EntryType.LAB) {
                OutlinedTextField(
                    value = editedSlot.labGroupName ?: "",
                    onValueChange = { editedSlot = editedSlot.copy(labGroupName = it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.lab_group_label)) },
                    placeholder = { Text(stringResource(R.string.lab_group_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (editedSlot.id != 0L) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = if (editedSlot.isIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.ignore_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { showIgnoreHelp = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.content_description_help),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Switch(
                        checked = editedSlot.isIgnored,
                        onCheckedChange = { editedSlot = editedSlot.copy(isIgnored = it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null && editedSlot.id != 0L) {
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_slot_label),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            onSaveSlot(editedSlot)
                        }
                    ) {
                        Text(stringResource(R.string.save_button))
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_slot_label)) },
            text = { Text(stringResource(R.string.delete_slot_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_subject_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showStartPicker) {
        RadialTimePickerDialog(
            initialTime = editedSlot.startTime ?: LocalTime.of(9, 0),
            onDismiss = { showStartPicker = false },
            onTimeConfirm = { selectedTime ->
                val end = editedSlot.endTime
                val newEnd = if (end == null || selectedTime.isAfter(end) || selectedTime == end) {
                    selectedTime.plusMinutes(90)
                } else end
                editedSlot = editedSlot.copy(startTime = selectedTime, endTime = newEnd)
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        RadialTimePickerDialog(
            initialTime = editedSlot.endTime ?: (editedSlot.startTime?.plusMinutes(90) ?: LocalTime.of(10, 30)),
            onDismiss = { showEndPicker = false },
            onTimeConfirm = { selectedTime ->
                val start = editedSlot.startTime
                val newStart = if (start == null || selectedTime.isBefore(start) || selectedTime == start) {
                    selectedTime.minusMinutes(90)
                } else start
                editedSlot = editedSlot.copy(startTime = newStart, endTime = selectedTime)
                showEndPicker = false
            }
        )
    }

    if (showIgnoreHelp) {
        AlertDialog(
            onDismissRequest = { showIgnoreHelp = false },
            title = { Text(stringResource(R.string.ignore_help_title)) },
            text = { Text(stringResource(R.string.ignore_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showIgnoreHelp = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}
