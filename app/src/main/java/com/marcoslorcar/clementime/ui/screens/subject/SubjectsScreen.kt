package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.EmptyStateContent
import com.marcoslorcar.clementime.ui.components.SemesterSwitcher
import com.marcoslorcar.clementime.utils.fadingEdges
import java.time.DayOfWeek

@Composable
fun SubjectsScreen(
    onNavigateToSubject: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: SubjectsViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    SubjectsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToAddEditSubject = onNavigateToSubject,
        onNavigateToSchedule = onNavigateToSchedule,
        onNavigateToImport = onNavigateToImport,
        onMenuClick = onMenuClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectsContent(
    uiState: SubjectsUiState,
    onEvent: (SubjectsUiEvent) -> Unit,
    onNavigateToAddEditSubject: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit,
    onNavigateToImport: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showNukeDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    
    val subjectsListState = rememberLazyListState()

    val availableFilters = remember(uiState.subjectsInSelectedSemester) {
        val groups = uiState.subjectsInSelectedSemester.mapNotNull { it.subject.courseGroup }.filter { it.isNotBlank() }.distinct().sorted()
        val filters = mutableListOf<String>()
        filters.addAll(groups)
        filters.add("Inactive")
        filters
    }

    val filteredSubjects = remember(uiState.filteredSubjects, selectedGroupFilter) {
        when (selectedGroupFilter) {
            null -> uiState.filteredSubjects
            "Inactive" -> uiState.filteredSubjects.filter { !it.subject.isActive }
            else -> uiState.filteredSubjects.filter { it.subject.courseGroup == selectedGroupFilter }
        }
    }

    val groupedSubjects = remember(filteredSubjects) {
        filteredSubjects.groupBy { it.subject.courseGroup?.takeIf { g -> g.isNotBlank() } ?: "General" }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_selected_dialog_title)) },
            text = { 
                val count = uiState.selectedSubjectIds.size
                Text(pluralStringResource(R.plurals.delete_selected_dialog_message, count, count))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(SubjectsUiEvent.DeleteSelectedSubjects)
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.delete_selected_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showNukeDialog) {
        AlertDialog(
            onDismissRequest = { showNukeDialog = false },
            title = { Text(stringResource(R.string.nuke_all_subjects_title)) },
            text = { Text(stringResource(R.string.nuke_all_subjects_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(SubjectsUiEvent.NukeAllSubjects)
                        showNukeDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.nuke_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNukeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    title = if (uiState.isInSelectionMode) 
                        pluralStringResource(R.plurals.selected_count_simple, uiState.selectedSubjectIds.size, uiState.selectedSubjectIds.size)
                        else stringResource(R.string.subjects_screen_title),
                    onMenuClick = if (uiState.isInSelectionMode) null else onMenuClick,
                    onNavigateBack = if (uiState.isInSelectionMode) { { onEvent(SubjectsUiEvent.ClearSelection) } } else null,
                    actions = {
                        if (uiState.isInSelectionMode) {
                            if (uiState.selectedSubjectIds.isNotEmpty()) {
                                IconButton(onClick = { onEvent(SubjectsUiEvent.ToggleSelectedSubjectsActive) }) {
                                    val anyActive = uiState.subjects.filter { it.subject.id in uiState.selectedSubjectIds }.any { it.subject.isActive }
                                    Icon(
                                        imageVector = if (anyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(R.string.content_description_toggle_active)
                                    )
                                }
                                IconButton(onClick = { showDeleteConfirmation = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_description_delete_selected), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = { onEvent(SubjectsUiEvent.ToggleTools) }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.content_description_tools),
                                    tint = if (uiState.isToolsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            if (uiState.subjects.isNotEmpty()) {
                                IconButton(onClick = { onEvent(SubjectsUiEvent.ToggleSemesterSwitcher) }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = stringResource(R.string.content_description_toggle_semester_switcher),
                                        tint = if (uiState.isSemesterSwitcherVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { onEvent(SubjectsUiEvent.ToggleTools) }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.content_description_tools),
                                        tint = if (uiState.isToolsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = { showNukeDialog = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_description_nuke_all), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )
                
                AnimatedVisibility(
                    visible = uiState.isSemesterSwitcherVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    SemesterSwitcher(
                        selectedSemester = uiState.selectedSemester,
                        onSemesterSelected = { onEvent(SubjectsUiEvent.SemesterChanged(it)) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.isInSelectionMode) return@Scaffold
            
            if (uiState.subjects.size >= 2) {
                FloatingActionButtonMenu(
                    expanded = isFabExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = isFabExpanded,
                            onCheckedChange = { isFabExpanded = it }
                        ) {
                            val progress by animateFloatAsState(if (isFabExpanded) 1f else 0f, label = "fabProgress")
                            Icon(
                                imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = if (isFabExpanded) stringResource(R.string.content_description_close_actions) else stringResource(R.string.content_description_open_actions),
                                modifier = Modifier.graphicsLayer { rotationZ = progress * 90f }
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabExpanded = false
                            onNavigateToAddEditSubject(null)
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.add_subject_title)) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabExpanded = false
                            onEvent(SubjectsUiEvent.EnterSelectionMode)
                        },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.edit_subject)) }
                    )
                }
            } else {
                FloatingActionButton(onClick = { onNavigateToAddEditSubject(null) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_description_add_subject))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = uiState.isToolsVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { onEvent(SubjectsUiEvent.SearchQueryChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_subjects_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onEvent(SubjectsUiEvent.SearchQueryChanged("")) }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.content_description_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape
                    )

                    if (availableFilters.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableFilters.forEach { filter ->
                                FilterChip(
                                    selected = selectedGroupFilter == filter,
                                    onClick = {
                                        selectedGroupFilter =
                                            if (selectedGroupFilter == filter) null else filter
                                    },
                                    label = { Text(filter) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (filteredSubjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.searchQuery.isNotEmpty() || selectedGroupFilter != null) {
                        Text(
                            text = stringResource(R.string.no_search_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    } else if (uiState.subjectsInSelectedSemester.isEmpty()) {
                        EmptyStateContent(
                            title = stringResource(R.string.no_subjects_title),
                            subtitle = stringResource(R.string.no_subjects_subtitle),
                            icon = Icons.Filled.School,
                            onImportClick = onNavigateToImport,
                            onAddManuallyClick = { onNavigateToAddEditSubject(null) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = subjectsListState,
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(subjectsListState),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedSubjects.forEach { (groupName, subjectsInGroup) ->
                        item(key = "header_$groupName") {
                            if (uiState.isInSelectionMode) {
                                val sectionToggleState = subjectsInGroup.calculateToggleState(uiState.selectedSubjectIds)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 6.dp)
                                        .clickable {
                                            onEvent(SubjectsUiEvent.ToggleGroupSelection(subjectsInGroup.map { it.subject.id }))
                                        },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TriStateCheckbox(
                                            state = sectionToggleState,
                                            onClick = {
                                                onEvent(SubjectsUiEvent.ToggleGroupSelection(subjectsInGroup.map { it.subject.id }))
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = groupName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val selectedInGroup = subjectsInGroup.count { uiState.selectedSubjectIds.contains(it.subject.id) }
                                        Text(
                                            text = stringResource(
                                                R.string.section_selected_count,
                                                selectedInGroup,
                                                subjectsInGroup.size
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        items(
                            items = subjectsInGroup,
                            key = { it.subject.id }
                        ) { subjectWithSlots ->
                            SubjectItemCard(
                                subjectWithSlots = subjectWithSlots,
                                isInSelectionMode = uiState.isInSelectionMode,
                                isSelected = uiState.selectedSubjectIds.contains(subjectWithSlots.subject.id),
                                highContrastEnabled = uiState.highContrast,
                                onToggleActive = { onEvent(SubjectsUiEvent.ToggleSubjectActive(subjectWithSlots.subject.id, it)) },
                                onEditClick = { onNavigateToAddEditSubject(subjectWithSlots.subject.id) },
                                onDeleteClick = { onEvent(SubjectsUiEvent.DeleteSubject(subjectWithSlots.subject.id)) },
                                onNavigateToSchedule = onNavigateToSchedule,
                                onToggleSelection = { onEvent(SubjectsUiEvent.ToggleSubjectSelection(subjectWithSlots.subject.id)) },
                                dayStartTime = uiState.dayStartTime,
                                dayEndTime = uiState.dayEndTime
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Collection<SubjectWithSlots>.calculateToggleState(selectedIds: Set<Long>): ToggleableState {
    if (selectedIds.isEmpty()) return ToggleableState.Off
    if (selectedIds.size == size) return ToggleableState.On
    return ToggleableState.Indeterminate
}
