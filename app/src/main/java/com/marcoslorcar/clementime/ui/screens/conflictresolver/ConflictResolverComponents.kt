package com.marcoslorcar.clementime.ui.screens.conflictresolver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.uiColor
import com.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.utils.ScheduleSolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedWorkspace(
    subjects: List<SubjectWithSlots>,
    preferenceMode: PreferenceMode,
    onSelectPreferenceMode: (PreferenceMode) -> Unit,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onSelectLabGroup: (Long, String?) -> Unit,
    onShowIgnoreHelp: () -> Unit,
    onShowLabHelp: () -> Unit,
    onboardingTooltipsEnabled: Boolean = true,
    hasSeenPrioritiesTooltip: Boolean = false,
    onMarkPrioritiesTooltipSeen: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Optimization Priorities Button Group (Overlaps are ALWAYS #1)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.conflict_resolver_filter_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OnboardingTooltip(
                    text = stringResource(R.string.tooltip_resolver_priorities_desc),
                    title = stringResource(R.string.tooltip_resolver_priorities_title),
                    show = onboardingTooltipsEnabled && !hasSeenPrioritiesTooltip,
                    onDismiss = onMarkPrioritiesTooltipSeen
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = preferenceMode == PreferenceMode.FREE_DAYS,
                            onClick = { onSelectPreferenceMode(PreferenceMode.FREE_DAYS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.conflict_resolver_pref_free_days)) }
                        )
                        SegmentedButton(
                            selected = preferenceMode == PreferenceMode.COMPACTNESS,
                            onClick = { onSelectPreferenceMode(PreferenceMode.COMPACTNESS) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    Icons.Default.Compress,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.conflict_resolver_pref_compact)) }
                        )
                    }
                }
            }
        }

        // 2. Subjects & Lab Groups Section
        item {
            Text(
                text = stringResource(R.string.conflict_resolver_subjects_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(subjects, key = { "subject_${it.subject.id}" }) { subjectWithSlots ->
            SubjectConfigCard(
                subjectWithSlots = subjectWithSlots,
                onToggleIgnored = onToggleIgnored,
                onSelectLabGroup = onSelectLabGroup,
                onShowIgnoreHelp = onShowIgnoreHelp,
                onShowLabHelp = onShowLabHelp
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectConfigCard(
    subjectWithSlots: SubjectWithSlots,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onSelectLabGroup: (Long, String?) -> Unit,
    onShowIgnoreHelp: () -> Unit,
    onShowLabHelp: () -> Unit
) {
    val subject = subjectWithSlots.subject
    val slots = subjectWithSlots.slots
    val labGroups = remember(slots) {
        slots.filter { it.entryType == EntryType.LAB }
            .mapNotNull { it.labGroupName }
            .distinct()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Subject Title Header with Color Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(subject.uiColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${subject.name} (${subject.code})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Lab Group Choice Chips (Horizontal)
            if (labGroups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.lab_group_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onShowLabHelp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    labGroups.forEach { groupName ->
                        val isPinned = subject.selectedLabGroup == groupName
                        val isOnlyOption = labGroups.size == 1
                        FilterChip(
                            selected = isPinned || isOnlyOption,
                            onClick = {
                                if (!isOnlyOption) {
                                    onSelectLabGroup(subject.id, if (isPinned) null else groupName)
                                } else if (!isPinned) {
                                    onSelectLabGroup(subject.id, groupName)
                                }
                            },
                            label = { Text(groupName) },
                            enabled = !isOnlyOption,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isPinned || isOnlyOption) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.alpha(if (isOnlyOption) 0.6f else 1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Slots List with De-cluttered Ignore Switch
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.class_slots_header, slots.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.conflict_resolver_ignore_section_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onShowIgnoreHelp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(if (slot.isIgnored) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (slot.isIgnored) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        val typeLabel = if (slot.entryType == EntryType.LAB) {
                            slot.labGroupName ?: stringResource(R.string.lab_label)
                        } else {
                            stringResource(R.string.theory_label)
                        }
                        val dayName = when (slot.dayOfWeek) {
                            java.time.DayOfWeek.MONDAY -> stringResource(R.string.mon_label)
                            java.time.DayOfWeek.TUESDAY -> stringResource(R.string.tue_label)
                            java.time.DayOfWeek.WEDNESDAY -> stringResource(R.string.wed_label)
                            java.time.DayOfWeek.THURSDAY -> stringResource(R.string.thu_label)
                            java.time.DayOfWeek.FRIDAY -> stringResource(R.string.fri_label)
                            else -> slot.dayOfWeek.name
                        }

                        Text(
                            text = "$typeLabel • $dayName (${slot.startTime} - ${slot.endTime})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (slot.isIgnored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Switch(
                        checked = slot.isIgnored,
                        onCheckedChange = { isChecked ->
                            onToggleIgnored(slot.id, isChecked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SolutionsSheetContent(
    solutions: List<ScheduleSolution>,
    subjects: List<SubjectWithSlots>,
    onSelectSolution: (ScheduleSolution) -> Unit,
    ambiguousSubjectIds: Set<Long> = emptySet(),
    onboardingTooltipsEnabled: Boolean = true,
    hasSeenApplyTooltip: Boolean = false,
    onMarkApplyTooltipSeen: () -> Unit = {}
) {
    if (solutions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.conflict_resolver_no_variants))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.conflict_resolver_solutions_tab),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(
            items = solutions,
            key = { solution ->
                "solution_${solution.labSelections.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.joinToString(",")}" }}_${solution.totalSlots.size}"
            }
        ) { solution ->
            // Show tooltip only for the first non-current solution
            val isFirstNonCurrent = remember(solutions, solution) {
                solutions.firstOrNull { !it.isCurrent } == solution
            }

            SolutionCard(
                solution = solution,
                allSubjects = subjects,
                ambiguousSubjectIds = ambiguousSubjectIds,
                onApply = { onSelectSolution(solution) },
                showOnboardingTooltip = isFirstNonCurrent && onboardingTooltipsEnabled && !hasSeenApplyTooltip,
                onMarkApplyTooltipSeen = onMarkApplyTooltipSeen
            )
        }
    }
}

@Composable
fun SolutionCard(
    solution: ScheduleSolution,
    allSubjects: List<SubjectWithSlots>,
    ambiguousSubjectIds: Set<Long> = emptySet(),
    onApply: () -> Unit,
    showOnboardingTooltip: Boolean = false,
    onMarkApplyTooltipSeen: () -> Unit = {}
) {
    val isCurrent = solution.isCurrent
    val conflictingClassesCount = solution.overlappingSlotIds.size
    val hasOverlaps = conflictingClassesCount > 0

    // Solution Badge Determination
    val primaryBadgeText = when {
        isCurrent -> stringResource(R.string.conflict_resolver_current_badge)
        hasOverlaps -> stringResource(R.string.conflict_resolver_badge_conflict)
        solution.freeDaysCount >= 2 -> stringResource(R.string.conflict_resolver_badge_free_days)
        solution.compactnessScore >= 85.0 -> stringResource(R.string.conflict_resolver_badge_compact)
        else -> stringResource(R.string.conflict_resolver_badge_zero_overlaps)
    }

    val badgeContainerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        hasOverlaps -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val badgeContentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        hasOverlaps -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (hasOverlaps) 0.7f else 1.0f),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasOverlaps) 0.dp else 2.dp),
        border = when {
            isCurrent -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            hasOverlaps -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Surface(
                        color = badgeContainerColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = primaryBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = badgeContentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val overlapsText = pluralStringResource(
                        R.plurals.conflict_resolver_overlaps,
                        conflictingClassesCount,
                        conflictingClassesCount
                    )
                    val freeDaysText = pluralStringResource(
                        R.plurals.conflict_resolver_free_days,
                        solution.freeDaysCount,
                        solution.freeDaysCount
                    )
                    val subtitle = "$overlapsText • $freeDaysText"

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasOverlaps) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OnboardingTooltip(
                    text = stringResource(R.string.tooltip_resolver_apply_desc),
                    title = stringResource(R.string.tooltip_resolver_apply_title),
                    show = showOnboardingTooltip,
                    onDismiss = onMarkApplyTooltipSeen
                ) {
                    Button(
                        onClick = onApply,
                        enabled = !isCurrent
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCurrent) {
                                stringResource(R.string.conflict_resolver_applied)
                            } else {
                                stringResource(R.string.conflict_resolver_apply)
                            }
                        )
                    }
                }
            }

            // One line per lab choice, only for subjects where a real choice existed
            // (unpinned, and offering more than one distinct lab schedule).
            val filteredSelections = remember(solution.labSelections, ambiguousSubjectIds) {
                solution.labSelections.filterKeys { it in ambiguousSubjectIds }
            }

            if (filteredSelections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.conflict_resolver_choices_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredSelections.forEach { (subjectId, labGroupNames) ->
                        val subject = allSubjects.find { it.subject.id == subjectId }?.subject
                        val codeStr = subject?.code ?: "SUB"
                        val groupText = labGroupNames.joinToString("/")
                        val subjectColor = subject?.uiColor ?: MaterialTheme.colorScheme.primary

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = subjectColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = subject?.name ?: codeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = true)
                                )
                                Text(
                                    text = groupText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ScheduleMiniPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                slots = solution.totalSlots,
                overlappingSlotIds = solution.overlappingSlotIds
            )
        }
    }
}

@Composable
fun ConfirmationSheetContent(
    solution: ScheduleSolution,
    subjects: List<SubjectWithSlots>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val summaryList = solution.labSelections.map { (subjectId, groups) ->
        val sub = subjects.find { it.subject.id == subjectId }?.subject
        "• ${sub?.name ?: "Subject"} (${sub?.code}): ${groups.joinToString("/")}"
    }.joinToString("\n")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.conflict_resolver_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.conflict_resolver_confirm_message, summaryList),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.conflict_resolver_confirm_save))
            }
        }
    }
}
