package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.cardColor
import com.marcoslorcar.clementime.data.uiColor
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectItemCard(
    subjectWithSlots: SubjectWithSlots,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    highContrastEnabled: Boolean,
    onToggleActive: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit,
    onToggleSelection: () -> Unit
) {
    val subject = subjectWithSlots.subject
    var isExpanded by remember { mutableStateOf(false) }

    val baseColor = subject.uiColor
    val cardBgColor = if (highContrastEnabled) {
        if (subject.isActive) baseColor.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceVariant
    } else if (subject.isActive) {
        subject.cardColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    val luminance = 0.299 * baseColor.red + 0.587 * baseColor.green + 0.114 * baseColor.blue
    val contentColor = if (highContrastEnabled && subject.isActive) {
        if (luminance > 0.5f) Color.Black else Color.White
    } else if (subject.isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    val secondaryContentColor = if (highContrastEnabled && subject.isActive) {
        contentColor.copy(alpha = 0.75f)
    } else if (subject.isActive) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isInSelectionMode) {
                            onToggleSelection()
                        } else {
                            isExpanded = !isExpanded
                        }
                    },
                    onLongClick = onToggleSelection
                ),
            color = cardBgColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isInSelectionMode,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (highContrastEnabled) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val slotCountText = pluralStringResource(R.plurals.slots_count, subjectWithSlots.slots.size, subjectWithSlots.slots.size)
                        Text(
                            text = "${subject.code} • $slotCountText",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = secondaryContentColor
                        )
                    }

                    AnimatedVisibility(visible = isExpanded && !isInSelectionMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onToggleActive(!subject.isActive) }
                                ) {
                                    Text(
                                        text = stringResource(if (subject.isActive) R.string.active else R.string.inactive),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (subject.isActive) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = subject.isActive,
                                        onCheckedChange = onToggleActive
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onEditClick,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.view_subject_button))
                                    }

                                    OutlinedButton(
                                        onClick = onDeleteClick,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.delete_subject))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val previewSlots = remember(subjectWithSlots) {
                                subjectWithSlots.slots.map { subjectWithSlots.subject to it }
                            }

                            if (previewSlots.isNotEmpty()) {
                                ScheduleMiniPreview(
                                    slots = previewSlots,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .padding(bottom = 12.dp)
                                )
                            }

                            if (subjectWithSlots.slots.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_slots_assigned),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                subjectWithSlots.slots.forEach { slot ->
                                    SlotDetailRow(
                                        slot = slot,
                                        onSlotClick = { onNavigateToSchedule(slot.dayOfWeek, slot.id) }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
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
fun SlotDetailRow(
    slot: ClassSlot,
    onSlotClick: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val formattedTime = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}"
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dayName = remember(slot.dayOfWeek, locale) {
        slot.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSlotClick)
            .alpha(if (slot.isIgnored) 0.6f else 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$dayName, $formattedTime",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (slot.entryType) {
                            EntryType.THEORY -> MaterialTheme.colorScheme.secondaryContainer
                            EntryType.LAB -> MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ) {
                        Text(
                            text = slot.entryType.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = when (slot.entryType) {
                                EntryType.THEORY -> MaterialTheme.colorScheme.onSecondaryContainer
                                EntryType.LAB -> MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }

                val details = listOfNotNull(
                    slot.classroom?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.room_prefix, it) },
                    slot.labGroupName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.group_prefix, it) },
                    slot.professor?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.prof_prefix, it) }
                ).joinToString(" • ")

                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (slot.isIgnored) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = stringResource(R.string.content_description_ignored),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SubjectItemCardPreview() {
    val sampleSubject = Subject(
        id = 1,
        code = "CS101",
        name = "Introduction to Computer Science",
        color = 0xFF2196F3.toInt(),
        isActive = true,
        semester = 1
    )
    val sampleSlots = listOf(
        ClassSlot(
            id = 1,
            subjectId = 1,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            classroom = "Room 101",
            entryType = EntryType.THEORY
        ),
        ClassSlot(
            id = 2,
            subjectId = 1,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(16, 0),
            classroom = "Lab 2",
            entryType = EntryType.LAB
        )
    )
    val subjectWithSlots = SubjectWithSlots(sampleSubject, sampleSlots)

    ClemenTimeTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SubjectItemCard(
                subjectWithSlots = subjectWithSlots,
                isInSelectionMode = false,
                isSelected = false,
                highContrastEnabled = false,
                onToggleActive = {},
                onEditClick = {},
                onDeleteClick = {},
                onNavigateToSchedule = { _, _ -> },
                onToggleSelection = {}
            )
        }
    }
}
