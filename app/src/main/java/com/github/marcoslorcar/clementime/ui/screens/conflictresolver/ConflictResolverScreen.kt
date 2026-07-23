package com.github.marcoslorcar.clementime.ui.screens.conflictresolver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.data.uiColor
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.github.marcoslorcar.clementime.utils.ScheduleSolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolverScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConflictResolverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    title = stringResource(R.string.conflict_resolver_title),
                    onNavigateBack = onNavigateBack
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.conflict_resolver_details_tab)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.conflict_resolver_solutions_tab)) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    text = { Text(stringResource(R.string.conflict_resolver_continue)) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (selectedTab) {
                    0 -> ConflictsDetail(
                        subjects = uiState.subjects,
                        onToggleIgnored = viewModel::toggleSlotIgnored,
                        onSelectLabGroup = viewModel::selectLabGroup
                    )
                    1 -> SolutionsList(
                        solutions = uiState.solutions,
                        subjects = uiState.subjects,
                        onApply = { 
                            viewModel.applySolution(it)
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SolutionsList(
    solutions: List<ScheduleSolution>,
    subjects: List<SubjectWithSlots>,
    onApply: (ScheduleSolution) -> Unit
) {
    if (solutions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.conflict_resolver_no_variants))
        }
        return
    }

    val currentSolution = remember(solutions) { solutions.find { it.isCurrent } }
    val perfectSolutions = remember(solutions) { solutions.filter { it.overlapsCount == 0 && !it.isCurrent } }
    val otherSolutions = remember(solutions) { solutions.filter { it.overlapsCount > 0 && !it.isCurrent } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (currentSolution != null) {
            item(key = "header_current") {
                Text(
                    text = stringResource(R.string.conflict_resolver_current_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item(
                key = "current_${currentSolution.labSelections.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.joinToString(",")}" }}_${currentSolution.totalSlots.size}"
            ) {
                SolutionCard(
                    solution = currentSolution,
                    allSubjects = subjects,
                    onApply = { onApply(currentSolution) }
                )
            }
            if (perfectSolutions.isNotEmpty() || otherSolutions.isNotEmpty()) {
                item(key = "current_divider") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        if (perfectSolutions.isNotEmpty()) {
            item(key = "header_optimal") {
                Text(
                    text = stringResource(R.string.conflict_resolver_optimal_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(
                items = perfectSolutions,
                key = { solution -> 
                    "perfect_${solution.labSelections.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.joinToString(",")}" }}_${solution.totalSlots.size}"
                }
            ) { solution ->
                SolutionCard(
                    solution = solution,
                    allSubjects = subjects,
                    onApply = { onApply(solution) }
                )
            }
        }

        if (otherSolutions.isNotEmpty()) {
            item(key = "header_others") {
                val title = if (perfectSolutions.isEmpty()) {
                    stringResource(R.string.conflict_resolver_possible_header)
                } else {
                    stringResource(R.string.conflict_resolver_other_header)
                }
                Column {
                    if (perfectSolutions.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (perfectSolutions.isEmpty()) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(
                items = otherSolutions,
                key = { solution -> 
                    "other_${solution.labSelections.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.joinToString(",")}" }}_${solution.totalSlots.size}"
                }
            ) { solution ->
                SolutionCard(
                    solution = solution,
                    allSubjects = subjects,
                    onApply = { onApply(solution) }
                )
            }
        }
    }
}

@Composable
fun SolutionCard(
    solution: ScheduleSolution,
    allSubjects: List<SubjectWithSlots>,
    onApply: () -> Unit
) {
    val isCurrent = solution.isCurrent

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (isCurrent) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.conflict_resolver_current_badge),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = if (solution.overlapsCount == 0) {
                            stringResource(R.string.conflict_resolver_optimal_label)
                        } else {
                            stringResource(R.string.conflict_resolver_possible_label)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (solution.overlapsCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )

                    val subtitle = buildString {
                        if (solution.overlapsCount > 0) {
                            append(pluralStringResource(R.plurals.conflict_resolver_overlaps, solution.overlapsCount, solution.overlapsCount))
                            append(" • ")
                        }
                        append(pluralStringResource(R.plurals.conflict_resolver_free_days, solution.freeDaysCount, solution.freeDaysCount))
                        if (solution.overlapsCount > 0) {
                            append(" • ")
                            append(stringResource(R.string.conflict_resolver_score, solution.compactnessScore.toInt()))
                        }
                    }
                    
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ScheduleMiniPreview(
                modifier = Modifier.fillMaxWidth(),
                slots = solution.totalSlots,
                overlappingSlotIds = solution.overlappingSlotIds
            )
            
            if (solution.labSelections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    solution.labSelections.forEach { (subjectId, labGroupNames) ->
                        val subject = allSubjects.find { it.subject.id == subjectId }?.subject
                        val subjectName = subject?.name ?: "Unknown"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(subject?.uiColor ?: Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${subject?.code ?: "???"} - $subjectName",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = labGroupNames.joinToString(" / "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConflictsDetail(
    subjects: List<SubjectWithSlots>,
    onToggleIgnored: (Long, Boolean) -> Unit,
    onSelectLabGroup: (Long, String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.conflict_resolver_manual_config_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.conflict_resolver_radio_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.conflict_resolver_checkbox_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        subjects.forEach { subjectWithSlots ->
            item(key = "subject_${subjectWithSlots.subject.id}") {
                Text(
                    text = "${subjectWithSlots.subject.name} (${subjectWithSlots.subject.code})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(subjectWithSlots.slots, key = { "slot_${it.id}" }) { slot ->
                val isLab = slot.entryType == com.github.marcoslorcar.clementime.data.EntryType.LAB
                val isSelected = isLab && subjectWithSlots.subject.selectedLabGroup == slot.labGroupName

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(if (slot.isIgnored) 0.6f else 1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleIgnored(slot.id, !slot.isIgnored) }
                    ) {
                        Icon(
                            imageVector = if (slot.isIgnored) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (slot.isIgnored) "Unignore slot" else "Ignore slot",
                            tint = if (slot.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) 
                                   else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (slot.labGroupName ?: stringResource(R.string.theory_label)) + " - " + 
                                when(slot.dayOfWeek) {
                                    java.time.DayOfWeek.MONDAY -> stringResource(R.string.mon_label)
                                    java.time.DayOfWeek.TUESDAY -> stringResource(R.string.tue_label)
                                    java.time.DayOfWeek.WEDNESDAY -> stringResource(R.string.wed_label)
                                    java.time.DayOfWeek.THURSDAY -> stringResource(R.string.thu_label)
                                    java.time.DayOfWeek.FRIDAY -> stringResource(R.string.fri_label)
                                    else -> slot.dayOfWeek.name
                                },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${slot.startTime} - ${slot.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    if (isLab) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectLabGroup(
                                    subjectWithSlots.subject.id,
                                    if (isSelected) null else slot.labGroupName
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            }
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
