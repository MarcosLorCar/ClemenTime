package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
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
import com.marcoslorcar.clementime.ui.screens.schedule.ScheduleTab
import com.marcoslorcar.clementime.utils.shortName
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditBottomSheet(
    initialSlot: ClassSlotUiModel,
    onDismiss: () -> Unit,
    onSaveSlot: (ClassSlotUiModel) -> Unit
) {
    var editedSlot by remember(initialSlot) { mutableStateOf(initialSlot) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                        } else "Start: --:--"
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
                        } else "End: --:--"
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

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
}
