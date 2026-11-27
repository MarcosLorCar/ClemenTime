@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.scheduleimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import com.marcoslorcar.clementime.utils.fadingEdges

@Composable
fun ImportContent(
    uiState: ImportUiState.Selection,
    onToggleSubject: (JsonSubject, String) -> Unit,
    onToggleSection: (Collection<SelectedSubject>) -> Unit,
    onDeselectAll: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onResetState: () -> Unit,
    onMarkConflictTooltipSeen: () -> Unit = {},
    onMarkPreviewTooltipSeen: () -> Unit = {}
) {
    var isSearchVisible by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showManualTooltip by remember { mutableStateOf(false) }
    var longPressedSubject by remember { mutableStateOf<SelectedSubject?>(null) }
    val subjectsListState = rememberLazyListState()

    if (showConflictDialog && uiState.conflictStatus is ConflictStatus.Conflict) {
        ConflictDetailsDialog(
            detail = uiState.conflictStatus.detail,
            onDismiss = { showConflictDialog = false }
        )
    }

    if (longPressedSubject != null) {
        ImportPreviewDialog(
            previewSubject = longPressedSubject!!,
            selectedSubjects = uiState.selectedSubjects,
            existingSubjects = uiState.existingSubjects,
            onDismiss = { longPressedSubject = null }
        )
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = uiState.schema.title ?: stringResource(R.string.import_schedule_title),
                onNavigateBack = onResetState,
                actions = {
                    // Conflict Status Icon
                    when (uiState.conflictStatus) {
                        is ConflictStatus.Valid -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.content_description_no_conflicts),
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
                        }
                        is ConflictStatus.Conflict -> {
                            OnboardingTooltip(
                                text = stringResource(R.string.tooltip_import_conflict_desc),
                                title = stringResource(R.string.tooltip_import_conflict_title),
                                show = uiState.onboardingEnabled && !uiState.hasSeenConflictTooltip,
                                onDismiss = onMarkConflictTooltipSeen
                            ) {
                                IconButton(onClick = { showConflictDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = stringResource(R.string.content_description_conflicts_detected),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        ConflictStatus.None -> {}
                    }

                    if (uiState.selectedSubjects.isNotEmpty()) {
                        TextButton(onClick = onDeselectAll) {
                            Text(stringResource(R.string.deselect_all))
                        }
                    }
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) {
                                onUpdateSearchQuery("")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchVisible) stringResource(R.string.content_description_close_search) else stringResource(R.string.content_description_search_subjects)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val availableYears = remember(uiState.schema) {
                val years = uiState.schema.years.map { it.name }.sorted()
                if (uiState.schema.subjects.isNotEmpty()) {
                    (listOf("General") + years).distinct()
                } else {
                    years.distinct()
                }
            }

            var selectedYearFilter by remember { mutableStateOf<String?>(null) }

            // All subjects in the schema flattened with their group/year info
            val allFlattenedSubjects = remember(uiState.schema) {
                val fromRoot = uiState.schema.subjects.map { SelectedSubject(it, "General") }
                val fromYears = uiState.schema.years.flatMap { year ->
                    val yearCommon = year.subjects.map { SelectedSubject(it, "${year.name} Common") }
                    val fromGroups = year.groups.flatMap { group ->
                        group.subjects.map { SelectedSubject(it, "${year.name} ${group.name}") }
                    }
                    yearCommon + fromGroups
                }
                fromRoot + fromYears
            }

            val filteredSubjects = remember(allFlattenedSubjects, selectedYearFilter, uiState.searchQuery) {
                allFlattenedSubjects.filter { selected ->
                    val yearMatch = selectedYearFilter == null || selected.courseGroup.startsWith(selectedYearFilter!!)
                    val queryMatch = uiState.searchQuery.isBlank() ||
                            selected.subject.name.contains(uiState.searchQuery, ignoreCase = true) ||
                            selected.subject.code.contains(uiState.searchQuery, ignoreCase = true)
                    yearMatch && queryMatch
                }
            }

            val groupedSubjects = remember(filteredSubjects) {
                filteredSubjects.groupBy { it.courseGroup }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.import_screen_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    OnboardingTooltip(
                        text = stringResource(R.string.import_preview_tooltip_desc),
                        title = stringResource(R.string.import_preview_tooltip_title),
                        show = (uiState.onboardingEnabled && !uiState.hasSeenPreviewTooltip) || showManualTooltip,
                        onDismiss = {
                            onMarkPreviewTooltipSeen()
                            showManualTooltip = false
                        }
                    ) {
                        IconButton(
                            onClick = {
                                showManualTooltip = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Help,
                                contentDescription = stringResource(R.string.content_description_help),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSearchVisible) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onUpdateSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_subjects_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onUpdateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.content_description_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (availableYears.size > 1) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableYears.forEach { year ->
                            FilterChip(
                                selected = selectedYearFilter == year,
                                onClick = {
                                    selectedYearFilter = if (selectedYearFilter == year) null else year
                                },
                                label = { Text(year) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    state = subjectsListState,
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(subjectsListState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedSubjects.forEach { (fullGroupName, groupSubjects) ->
                        item(key = "header_$fullGroupName") {
                            val selectedCount = groupSubjects.count { uiState.selectedSubjects.contains(it) }
                            val groupState = when (selectedCount) {
                                0 -> ToggleableState.Off
                                groupSubjects.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TriStateCheckbox(
                                        state = groupState,
                                        onClick = {
                                            onToggleSection(groupSubjects)
                                        }
                                    )
                                    Text(
                                        text = fullGroupName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.section_selected_count,
                                            selectedCount,
                                            groupSubjects.size
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }

                        items(
                            items = groupSubjects,
                            key = { "${fullGroupName}_${it.subject.code}" }
                        ) { selected ->
                            val isSelected = uiState.selectedSubjects.contains(selected)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onToggleSubject(selected.subject, fullGroupName) },
                                        onLongClick = { longPressedSubject = selected }
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSubject(selected.subject, fullGroupName) }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(text = selected.subject.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = selected.subject.code,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(start = 48.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConfirmImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedSubjects.isNotEmpty()
                ) {
                    Text(stringResource(R.string.import_selected_button, uiState.selectedSubjects.size))
                }
            }
        }
    }
}
