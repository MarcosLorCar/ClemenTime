package com.marcoslorcar.clementime.ui.screens.scheduleimport

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.marcoslorcar.clementime.utils.DiffSlotSnapshot
import com.marcoslorcar.clementime.utils.DiffType
import com.marcoslorcar.clementime.utils.SlotDiff
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun ScheduleDiffBottomSheet(
    diffs: List<SlotDiff>,
    onApply: (Set<String>) -> Unit,
    onIgnore: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    beforeSlots: List<Pair<Subject, ClassSlot>> = emptyList(),
    afterSlots: List<Pair<Subject, ClassSlot>> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        ScheduleDiffSheetContent(
            diffs = diffs,
            onApply = onApply,
            onIgnore = onIgnore,
            beforeSlots = beforeSlots,
            afterSlots = afterSlots
        )
    }
}

private fun SlotDiff.groupKey(): String = subjectKey.ifBlank { subjectCode.ifBlank { subjectName } }

@Composable
fun ScheduleDiffSheetContent(
    diffs: List<SlotDiff>,
    onApply: (Set<String>) -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    beforeSlots: List<Pair<Subject, ClassSlot>> = emptyList(),
    afterSlots: List<Pair<Subject, ClassSlot>> = emptyList()
) {
    val groupedBySubject = remember(diffs) { diffs.groupBy { it.groupKey() } }
    val hasMultipleSubjects = groupedBySubject.size > 1
    var selectedSubjectKeys by remember(diffs) { mutableStateOf(groupedBySubject.keys) }

    val selectedDiffs = remember(diffs, selectedSubjectKeys) {
        diffs.filter { it.groupKey() in selectedSubjectKeys }
    }

    // Compute synthetic before/after/changed slots if not explicitly passed
    val parsedData = remember(diffs, selectedDiffs, beforeSlots, afterSlots) {
        if (beforeSlots.isNotEmpty() || afterSlots.isNotEmpty()) {
            Triple(beforeSlots, afterSlots, afterSlots.filter { pair ->
                selectedDiffs.any { diff -> diff.subjectCode == pair.first.code || diff.subjectName == pair.first.name }
            }.toSet())
        } else {
            val previewDiffs = if (selectedDiffs.isNotEmpty()) selectedDiffs else diffs
            buildSlotsFromDiffs(previewDiffs)
        }
    }

    val displayBeforeSlots = parsedData.first
    val displayAfterSlots = parsedData.second
    val highlightedSlots = parsedData.third

    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Before, 1 = After (default to After to highlight changes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header Title
        Text(
            text = stringResource(R.string.schedule_changes_detected_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle Description
        Text(
            text = stringResource(R.string.schedule_changes_detected_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Split Button Toggle (Before / After)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(
                    text = stringResource(R.string.diff_previous_label),
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                )
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(
                    text = stringResource(R.string.diff_new_label),
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mini Schedule Preview Visualization
        val currentPreviewSlots = if (selectedTab == 0) displayBeforeSlots else displayAfterSlots
        val currentHighlightedSlots = if (selectedTab == 1) highlightedSlots else emptySet()

        ScheduleMiniPreview(
            slots = currentPreviewSlots,
            highlightedSlots = currentHighlightedSlots,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Footer List of Changes
        Text(
            text = stringResource(R.string.diff_list_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            if (hasMultipleSubjects) {
                groupedBySubject.forEach { (key, subjectDiffs) ->
                    item(key = key) {
                        val isSelected = key in selectedSubjectKeys
                        val firstDiff = subjectDiffs.first()
                        SubjectDiffGroupCard(
                            subjectCode = firstDiff.subjectCode,
                            subjectName = firstDiff.subjectName,
                            diffs = subjectDiffs,
                            isSelected = isSelected,
                            onToggle = { checked ->
                                selectedSubjectKeys = if (checked) {
                                    selectedSubjectKeys + key
                                } else {
                                    selectedSubjectKeys - key
                                }
                            }
                        )
                    }
                }
            } else {
                items(diffs) { diff ->
                    SlotDiffFooterRow(diff = diff)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onIgnore,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.dismiss))
            }

            Button(
                onClick = { onApply(selectedSubjectKeys) },
                enabled = selectedSubjectKeys.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.apply_updates))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SubjectDiffGroupCard(
    subjectCode: String,
    subjectName: String,
    diffs: List<SlotDiff>,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val subjectTitle = if (subjectCode.isNotBlank() && subjectCode != subjectName) {
        "$subjectCode - $subjectName"
    } else {
        subjectName
    }
    Surface(
        onClick = { onToggle(!isSelected) },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
        },
        tonalElevation = if (isSelected) 1.dp else 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggle
                )
                Text(
                    text = subjectTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            diffs.forEach { diff ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        diff.oldSlot?.let { old ->
                            Text(
                                text = stringResource(R.string.diff_previous_detail, formatDiffSlotDetail(old)),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = TextDecoration.LineThrough
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        diff.newSlot?.let { new ->
                            Text(
                                text = stringResource(R.string.diff_new_detail, formatDiffSlotDetail(new)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    DiffTypeBadge(changeType = diff.changeType)
                }
            }
        }
    }
}

@Composable
private fun SlotDiffFooterRow(
    diff: SlotDiff,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject Code & Name
                val subjectTitle = if (diff.subjectCode.isNotBlank() && diff.subjectCode != diff.subjectName) {
                    "${diff.subjectCode} - ${diff.subjectName}"
                } else {
                    diff.subjectName
                }

                Text(
                    text = subjectTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Change Type Badge
                DiffTypeBadge(changeType = diff.changeType)
            }

            // Old Detail Text
            diff.oldSlot?.let { old ->
                Text(
                    text = stringResource(R.string.diff_previous_detail, formatDiffSlotDetail(old)),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // New Detail Text
            diff.newSlot?.let { new ->
                Text(
                    text = stringResource(R.string.diff_new_detail, formatDiffSlotDetail(new)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DiffTypeBadge(
    changeType: DiffType,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, labelRes) = when (changeType) {
        DiffType.MODIFIED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            R.string.diff_badge_modified
        )
        DiffType.ADDED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            R.string.diff_badge_added
        )
        DiffType.REMOVED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            R.string.diff_badge_removed
        )
    }

    Box(
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun formatDiffSlotDetail(snapshot: DiffSlotSnapshot): String {
    val locale = LocalLocale.current.platformLocale
    val roomFormatted = snapshot.classroom?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.room_prefix, it) }
    val profFormatted = snapshot.professor?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.prof_prefix, it) }
    return formatDiffSlotDetail(
        snapshot = snapshot,
        locale = locale,
        roomFormatted = roomFormatted,
        profFormatted = profFormatted
    )
}

internal fun formatDiffSlotDetail(
    snapshot: DiffSlotSnapshot,
    locale: Locale,
    roomFormatted: String?,
    profFormatted: String?
): String {
    val dayStr = snapshot.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val timeStr = String.format(
        Locale.US,
        "%02d:%02d - %02d:%02d",
        snapshot.startTime.hour,
        snapshot.startTime.minute,
        snapshot.endTime.hour,
        snapshot.endTime.minute
    )
    val extra = listOfNotNull(roomFormatted, profFormatted).joinToString(", ")
    return if (extra.isNotBlank()) "$dayStr $timeStr ($extra)" else "$dayStr $timeStr"
}

/**
 * Builds preview Subject/ClassSlot pairs directly from structured snapshots on [SlotDiff]
 * when no explicit full-schedule before/after lists were provided by the caller.
 */
internal fun buildSlotsFromDiffs(
    diffs: List<SlotDiff>
): Triple<List<Pair<Subject, ClassSlot>>, List<Pair<Subject, ClassSlot>>, Set<Pair<Subject, ClassSlot>>> {
    val beforeSlots = mutableListOf<Pair<Subject, ClassSlot>>()
    val afterSlots = mutableListOf<Pair<Subject, ClassSlot>>()
    val changedSlots = mutableSetOf<Pair<Subject, ClassSlot>>()
    val subjectsByKey = linkedMapOf<String, Subject>()
    var nextSlotId = 1L

    diffs.forEach { diff ->
        val key = diff.groupKey()
        val subject = subjectsByKey.getOrPut(key) {
            val subjectIndex = subjectsByKey.size
            Subject(
                id = (subjectIndex + 1).toLong(),
                code = diff.subjectCode.ifBlank { "SUBJ" },
                name = diff.subjectName.ifBlank { "Subject" },
                color = diff.subjectColor ?: getSubjectColor(subjectIndex),
                isActive = true
            )
        }

        diff.oldSlot?.let { snapshot ->
            val slot = ClassSlot(
                id = nextSlotId++,
                subjectId = subject.id,
                dayOfWeek = snapshot.dayOfWeek,
                startTime = snapshot.startTime,
                endTime = snapshot.endTime,
                classroom = snapshot.classroom,
                labGroupName = snapshot.labGroupName,
                entryType = snapshot.entryType,
                professor = snapshot.professor
            )
            beforeSlots.add(Pair(subject, slot))
        }

        diff.newSlot?.let { snapshot ->
            val slot = ClassSlot(
                id = nextSlotId++,
                subjectId = subject.id,
                dayOfWeek = snapshot.dayOfWeek,
                startTime = snapshot.startTime,
                endTime = snapshot.endTime,
                classroom = snapshot.classroom,
                labGroupName = snapshot.labGroupName,
                entryType = snapshot.entryType,
                professor = snapshot.professor
            )
            val pair = Pair(subject, slot)
            afterSlots.add(pair)
            changedSlots.add(pair)
        }
    }

    return Triple(beforeSlots, afterSlots, changedSlots)
}

private fun getSubjectColor(index: Int): Int {
    val colors = listOf(
        0xFF2196F3.toInt(), // Blue
        0xFF4CAF50.toInt(), // Green
        0xFFFF9800.toInt(), // Orange
        0xFF9C27B0.toInt(), // Purple
        0xFFE91E63.toInt()  // Pink
    )
    return colors[index % colors.size]
}

// ==========================================
// PREVIEWS
// ==========================================

private val sampleDiffs = listOf(
    SlotDiff(
        subjectCode = "PROG",
        subjectName = "Programación I",
        changeType = DiffType.MODIFIED,
        oldSlot = DiffSlotSnapshot(
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            entryType = EntryType.THEORY,
            classroom = "1.1"
        ),
        newSlot = DiffSlotSnapshot(
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(13, 0),
            entryType = EntryType.THEORY,
            classroom = "1.2"
        )
    ),
    SlotDiff(
        subjectCode = "ED",
        subjectName = "Estructuras de Datos",
        changeType = DiffType.ADDED,
        newSlot = DiffSlotSnapshot(
            dayOfWeek = DayOfWeek.THURSDAY,
            startTime = LocalTime.of(15, 30),
            endTime = LocalTime.of(17, 30),
            entryType = EntryType.LAB,
            classroom = "Lab 3"
        )
    ),
    SlotDiff(
        subjectCode = "FDD",
        subjectName = "Fundamentos del Diseño",
        changeType = DiffType.REMOVED,
        oldSlot = DiffSlotSnapshot(
            dayOfWeek = DayOfWeek.FRIDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            entryType = EntryType.THEORY,
            classroom = "2.1"
        )
    )
)

@Preview(showBackground = true, name = "Schedule Diff Content - Light Mode")
@Composable
fun ScheduleDiffSheetContentPreview() {
    ClemenTimeTheme(dynamicColor = false) {
        Surface {
            ScheduleDiffSheetContent(
                diffs = sampleDiffs,
                onApply = { _ -> },
                onIgnore = {}
            )
        }
    }
}

@Preview(
    showBackground = true,
    name = "Schedule Diff Content - Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ScheduleDiffSheetContentDarkPreview() {
    ClemenTimeTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            ScheduleDiffSheetContent(
                diffs = sampleDiffs,
                onApply = { _ -> },
                onIgnore = {}
            )
        }
    }
}
