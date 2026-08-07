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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.marcoslorcar.clementime.utils.DiffType
import com.marcoslorcar.clementime.utils.SlotDiff
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun ScheduleDiffBottomSheet(
    diffs: List<SlotDiff>,
    onApply: () -> Unit,
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

@Composable
fun ScheduleDiffSheetContent(
    diffs: List<SlotDiff>,
    onApply: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    beforeSlots: List<Pair<Subject, ClassSlot>> = emptyList(),
    afterSlots: List<Pair<Subject, ClassSlot>> = emptyList()
) {
    // Compute synthetic before/after/changed slots if not explicitly passed
    val parsedData = remember(diffs, beforeSlots, afterSlots) {
        if (beforeSlots.isNotEmpty() || afterSlots.isNotEmpty()) {
            Triple(beforeSlots, afterSlots, afterSlots.filter { pair ->
                diffs.any { diff -> diff.subjectCode == pair.first.code || diff.subjectName == pair.first.name }
            }.toSet())
        } else {
            buildSlotsFromDiffs(diffs)
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
            items(diffs) { diff ->
                SlotDiffFooterRow(diff = diff)
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
                onClick = onApply,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.apply_updates))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
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
            diff.oldDetail?.let { old ->
                Text(
                    text = stringResource(R.string.diff_previous_detail, old),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // New Detail Text
            diff.newDetail?.let { new ->
                Text(
                    text = stringResource(R.string.diff_new_detail, new),
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

/**
 * Utility to construct synthetic Subject/ClassSlot pairs from SlotDiff items if no
 * explicit full-schedule slots were provided by the caller.
 */
private fun buildSlotsFromDiffs(
    diffs: List<SlotDiff>
): Triple<List<Pair<Subject, ClassSlot>>, List<Pair<Subject, ClassSlot>>, Set<Pair<Subject, ClassSlot>>> {
    val beforeSlots = mutableListOf<Pair<Subject, ClassSlot>>()
    val afterSlots = mutableListOf<Pair<Subject, ClassSlot>>()
    val changedSlots = mutableSetOf<Pair<Subject, ClassSlot>>()

    diffs.forEachIndexed { index, diff ->
        val subject = Subject(
            id = (index + 1).toLong(),
            code = diff.subjectCode.ifBlank { "SUBJ" },
            name = diff.subjectName.ifBlank { "Subject" },
            color = getSubjectColor(index),
            isActive = true
        )

        diff.oldDetail?.let { detail ->
            parseDetailToSlot(detail, subject.id)?.let { slot ->
                beforeSlots.add(Pair(subject, slot))
            }
        }

        diff.newDetail?.let { detail ->
            parseDetailToSlot(detail, subject.id)?.let { slot ->
                val pair = Pair(subject, slot)
                afterSlots.add(pair)
                changedSlots.add(pair)
            }
        }
    }

    return Triple(beforeSlots, afterSlots, changedSlots)
}

private fun parseDetailToSlot(detail: String, subjectId: Long): ClassSlot? {
    val clean = detail.trim()
    if (clean.isBlank()) return null

    val day = when {
        clean.contains("Lunes", ignoreCase = true) -> DayOfWeek.MONDAY
        clean.contains("Martes", ignoreCase = true) -> DayOfWeek.TUESDAY
        clean.contains("Miércoles", ignoreCase = true) || clean.contains("Miercoles", ignoreCase = true) -> DayOfWeek.WEDNESDAY
        clean.contains("Jueves", ignoreCase = true) -> DayOfWeek.THURSDAY
        clean.contains("Viernes", ignoreCase = true) -> DayOfWeek.FRIDAY
        else -> DayOfWeek.MONDAY
    }

    val timeRegex = Regex("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})")
    val timeMatch = timeRegex.find(clean)

    val (start, end) = if (timeMatch != null) {
        val s = runCatching { LocalTime.parse(timeMatch.groupValues[1].padStart(5, '0')) }.getOrDefault(LocalTime.of(9, 0))
        val e = runCatching { LocalTime.parse(timeMatch.groupValues[2].padStart(5, '0')) }.getOrDefault(LocalTime.of(10, 30))
        Pair(s, e)
    } else {
        Pair(LocalTime.of(9, 0), LocalTime.of(10, 30))
    }

    val isLab = clean.contains("Lab", ignoreCase = true) || clean.contains("Aula", ignoreCase = true) && clean.contains("L", ignoreCase = true)

    return ClassSlot(
        id = (subjectId * 100 + day.value),
        subjectId = subjectId,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        entryType = if (isLab) EntryType.LAB else EntryType.THEORY
    )
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
        oldDetail = "Lunes 09:00 - 11:00 (Aula: 1.1)",
        newDetail = "Martes 11:00 - 13:00 (Aula: 1.2)"
    ),
    SlotDiff(
        subjectCode = "ED",
        subjectName = "Estructuras de Datos",
        changeType = DiffType.ADDED,
        oldDetail = null,
        newDetail = "Jueves 15:30 - 17:30 (Aula: Lab 3)"
    ),
    SlotDiff(
        subjectCode = "FDD",
        subjectName = "Fundamentos del Diseño",
        changeType = DiffType.REMOVED,
        oldDetail = "Viernes 09:00 - 11:00 (Aula: 2.1)",
        newDetail = null
    )
)

@Preview(showBackground = true, name = "Schedule Diff Content - Light Mode")
@Composable
fun ScheduleDiffSheetContentPreview() {
    ClemenTimeTheme(dynamicColor = false) {
        Surface {
            ScheduleDiffSheetContent(
                diffs = sampleDiffs,
                onApply = {},
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
                onApply = {},
                onIgnore = {}
            )
        }
    }
}
