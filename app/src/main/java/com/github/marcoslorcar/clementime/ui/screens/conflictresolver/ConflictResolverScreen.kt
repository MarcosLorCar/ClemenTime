package com.github.marcoslorcar.clementime.ui.screens.conflictresolver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.data.uiColor
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.github.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.github.marcoslorcar.clementime.utils.ScheduleSolution
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolverScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConflictResolverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSolutionsSheet by remember { mutableStateOf(false) }
    var solutionToConfirm by remember { mutableStateOf<ScheduleSolution?>(null) }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(R.string.conflict_resolver_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.conflict_resolver_help_title)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.solutions.isNotEmpty()) {
                val hasNoOverlapSolution = remember(uiState.solutions) {
                    uiState.solutions.any { it.overlapsCount == 0 }
                }

                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { showSolutionsSheet = true },
                            icon = {
                                Icon(
                                    imageVector = if (hasNoOverlapSolution) Icons.Default.AutoAwesome else Icons.Default.Warning,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    stringResource(
                                        if (hasNoOverlapSolution) {
                                            R.string.conflict_resolver_generate_preview
                                        } else {
                                            R.string.conflict_resolver_generate_preview_warning
                                        }
                                    )
                                )
                            },
                            containerColor = if (hasNoOverlapSolution) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (hasNoOverlapSolution) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                UnifiedWorkspace(
                    subjects = uiState.subjects,
                    preferenceMode = uiState.preferenceMode,
                    onSelectPreferenceMode = viewModel::setPreferenceMode,
                    onToggleIgnored = viewModel::toggleSlotIgnored,
                    onSelectLabGroup = viewModel::selectLabGroup,
                    onboardingTooltipsEnabled = uiState.onboardingTooltipsEnabled,
                    hasSeenPrioritiesTooltip = uiState.hasSeenPrioritiesTooltip,
                    onMarkPrioritiesTooltipSeen = viewModel::markPrioritiesTooltipSeen
                )
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(stringResource(R.string.conflict_resolver_help_title)) },
            text = { Text(stringResource(R.string.conflict_resolver_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showSolutionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSolutionsSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded) // Skips PartiallyExpanded
            )
        ) {
            SolutionsSheetContent(
                solutions = uiState.solutions,
                subjects = uiState.subjects,
                onSelectSolution = { solution ->
                    solutionToConfirm = solution
                },
                onboardingTooltipsEnabled = uiState.onboardingTooltipsEnabled,
                hasSeenApplyTooltip = uiState.hasSeenApplyTooltip,
                onMarkApplyTooltipSeen = viewModel::markApplyTooltipSeen
            )
        }
    }

    solutionToConfirm?.let { solution ->
        val appliedSnackbarMessage = stringResource(R.string.conflict_resolver_applied_snackbar)
        val undoActionLabel = stringResource(R.string.conflict_resolver_undo)

        ModalBottomSheet(
            onDismissRequest = { solutionToConfirm = null },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded) // Skips PartiallyExpanded
            )
        ) {
            ConfirmationSheetContent(
                solution = solution,
                subjects = uiState.subjects,
                onConfirm = {
                    viewModel.applySolution(solution)
                    solutionToConfirm = null
                    showSolutionsSheet = false

                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = appliedSnackbarMessage,
                            actionLabel = undoActionLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoLastApply()
                        }
                    }
                },
                onCancel = { solutionToConfirm = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedWorkspace(
    subjects: List<SubjectWithSlots>,
    preferenceMode: PreferenceMode,
    onSelectPreferenceMode: (PreferenceMode) -> Unit,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onSelectLabGroup: (Long, String?) -> Unit,
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
                onSelectLabGroup = onSelectLabGroup
            )
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectConfigCard(
    subjectWithSlots: SubjectWithSlots,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onSelectLabGroup: (Long, String?) -> Unit
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
                Text(
                    text = stringResource(R.string.lab_group_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    labGroups.forEach { groupName ->
                        val isPinned = subject.selectedLabGroup == groupName
                        FilterChip(
                            selected = isPinned,
                            onClick = {
                                onSelectLabGroup(subject.id, if (isPinned) null else groupName)
                            },
                            label = { Text(groupName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isPinned) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
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
                Text(
                    text = stringResource(R.string.conflict_resolver_ignore_section_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            fontWeight = FontWeight.Medium
                        )

                        if (slot.isIgnored) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.conflict_resolver_ignored_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                onApply = { onSelectSolution(solution) },
                showOnboardingTooltip = isFirstNonCurrent && onboardingTooltipsEnabled && !hasSeenApplyTooltip,
                onMarkApplyTooltipSeen = onMarkApplyTooltipSeen
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SolutionCard(
    solution: ScheduleSolution,
    allSubjects: List<SubjectWithSlots>,
    onApply: () -> Unit,
    showOnboardingTooltip: Boolean = false,
    onMarkApplyTooltipSeen: () -> Unit = {}
) {
    val isCurrent = solution.isCurrent
    val hasOverlaps = solution.overlapsCount > 0

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

                    val subtitle = buildString {
                        if (solution.overlapsCount > 0) {
                            append(pluralStringResource(R.plurals.conflict_resolver_overlaps, solution.overlapsCount, solution.overlapsCount))
                            append(" • ")
                        } else {
                            append("0 Overlaps • ")
                        }
                        append(pluralStringResource(R.plurals.conflict_resolver_free_days, solution.freeDaysCount, solution.freeDaysCount))
                    }

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

            // Wrapping FlowRow of Borderless Lab Group Chips
            if (solution.labSelections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    solution.labSelections.forEach { (subjectId, labGroupNames) ->
                        val subject = allSubjects.find { it.subject.id == subjectId }?.subject
                        val codeStr = subject?.code ?: "SUB"
                        val groupText = labGroupNames.joinToString("/")
                        val subjectColor = subject?.uiColor ?: MaterialTheme.colorScheme.primary

                        Surface(
                            color = subjectColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(subjectColor)
                                )
                                Text(
                                    text = "$codeStr:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
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
                modifier = Modifier.fillMaxWidth(),
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


