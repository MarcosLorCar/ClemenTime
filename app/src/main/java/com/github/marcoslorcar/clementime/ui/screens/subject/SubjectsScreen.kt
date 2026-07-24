package com.github.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.data.cardColor
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.github.marcoslorcar.clementime.utils.fadingEdges
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SubjectsScreen(
    onNavigateToAddEditSubject: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit = { _, _ -> },
    onNavigateToImport: () -> Unit,
    viewModel: SubjectsViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SubjectsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onMenuClick = onMenuClick,
        onNavigateToAddEditSubject = onNavigateToAddEditSubject,
        onNavigateToSchedule = onNavigateToSchedule,
        onNavigateToImport = onNavigateToImport
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectsContent(
    uiState: SubjectsUiState,
    onEvent: (SubjectsUiEvent) -> Unit,
    onNavigateToAddEditSubject: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek, Long?) -> Unit = { _, _ -> },
    onNavigateToImport: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    var subjectToDelete by remember { mutableStateOf<Subject?>(null) }
    var showNukeDialog by remember { mutableStateOf(false) }

    subjectToDelete?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text(stringResource(R.string.delete_subject_dialog_title)) },
            text = { Text(stringResource(R.string.delete_subject_dialog_message, subject.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(SubjectsUiEvent.DeleteSubject(subject.id))
                        subjectToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_subject_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
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

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete_selected_dialog_title)) },
            text = { Text(pluralStringResource(R.plurals.delete_selected_dialog_message, uiState.selectedSubjectIds.size, uiState.selectedSubjectIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(SubjectsUiEvent.DeleteSelectedSubjects)
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_selected_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    var isSearchVisible by remember { mutableStateOf(false) }
    var isFilterVisible by remember { mutableStateOf(false) }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    val subjectsListState = rememberLazyListState()

    val availableFilters = remember(uiState.subjects) {
        val groups = uiState.subjects.mapNotNull { it.subject.courseGroup }.filter { it.isNotBlank() }.distinct().sorted()
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

    Scaffold(
        topBar = {
            if (uiState.isInSelectionMode)
                ClemenTimeTopBar(
                    title = pluralStringResource(R.plurals.selected_count_simple, uiState.selectedSubjectIds.size, uiState.selectedSubjectIds.size),
                    onNavigateBack = { onEvent(SubjectsUiEvent.ClearSelection) },
                    actions = {
                        if (uiState.selectedSubjectIds.isNotEmpty()) {
                            IconButton(onClick = { onEvent(SubjectsUiEvent.DisableSelectedSubjects) }) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Disable selected subjects",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                isFilterVisible = !isFilterVisible
                                if (!isFilterVisible) {
                                    selectedGroupFilter = null
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter subjects",
                                tint = if (isFilterVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    onEvent(SubjectsUiEvent.SearchQueryChanged(""))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search subjects"
                            )
                        }
                        if (uiState.selectedSubjectIds.isNotEmpty()) {
                            IconButton(onClick = { showDeleteSelectedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete selected subjects",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            else
                ClemenTimeTopBar(
                    title = stringResource(R.string.subjects_screen_title),
                    onMenuClick = onMenuClick,
                    actions = actions@{
                        if (uiState.subjects.isEmpty()) {
                            return@actions
                        }

                        IconButton(
                            onClick = {
                                onEvent(SubjectsUiEvent.EnterSelectionMode)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Enter selection mode"
                            )
                        }
                        IconButton(
                            onClick = {
                                isFilterVisible = !isFilterVisible
                                if (!isFilterVisible) {
                                    selectedGroupFilter = null
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter subjects",
                                tint = if (isFilterVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    onEvent(SubjectsUiEvent.SearchQueryChanged(""))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search subjects"
                            )
                        }
                        IconButton(onClick = { showNukeDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Nuke all subjects",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
        },
        floatingActionButton = floatingActionButton@{
            if (uiState.isInSelectionMode) {
                return@floatingActionButton
            }
            FloatingActionButton(onClick = { onNavigateToAddEditSubject(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Subject")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.subjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_subjects_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_subjects_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateToImport) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_schedule_title))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = isSearchVisible) {
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
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape
                    )
                }

                AnimatedVisibility(visible = isFilterVisible) {
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
                                    onClick = { selectedGroupFilter = if (selectedGroupFilter == filter) null else filter },
                                    label = { Text(filter) }
                                )
                            }
                        }
                    }
                }

                if (filteredSubjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_search_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = subjectsListState,
                        modifier = Modifier
                            .fillMaxSize()
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
                                val isSelected = uiState.selectedSubjectIds.contains(subjectWithSlots.subject.id)
                                SubjectItemCard(
                                    subjectWithSlots = subjectWithSlots,
                                    isInSelectionMode = uiState.isInSelectionMode,
                                    isSelected = isSelected,
                                    highContrastEnabled = uiState.highContrast,
                                    onToggleActive = { isActive ->
                                        onEvent(SubjectsUiEvent.ToggleSubjectActive(subjectWithSlots.subject.id, isActive))
                                    },
                                    onEditClick = {
                                        onNavigateToAddEditSubject(subjectWithSlots.subject.id)
                                    },
                                    onDeleteClick = {
                                        subjectToDelete = subjectWithSlots.subject
                                    },
                                    onNavigateToSchedule = onNavigateToSchedule,
                                    onToggleSelection = {
                                        onEvent(SubjectsUiEvent.ToggleSubjectSelection(subjectWithSlots.subject.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubjectItemCard(
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

    val cardBgColor = if (highContrastEnabled) {
        if (subject.isActive) subject.cardColor else MaterialTheme.colorScheme.surfaceVariant
    } else if (subject.isActive) {
        subject.cardColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (highContrastEnabled) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (highContrastEnabled) {
                            if (subject.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        } else if (subject.isActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
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
                            color = if (subject.isActive) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
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
private fun SlotDetailRow(
    slot: ClassSlot,
    onSlotClick: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val formattedTime = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}"
    val dayName = slot.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

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
                    slot.classroom?.takeIf { it.isNotBlank() }?.let { "Room: $it" },
                    slot.labGroupName?.takeIf { it.isNotBlank() }?.let { "Group: $it" },
                    slot.professor?.takeIf { it.isNotBlank() }?.let { "Prof: $it" }
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
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Ignored",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun Collection<SubjectWithSlots>.calculateToggleState(selected: Set<Long>): ToggleableState {
    val ids = map { it.subject.id }
    val selectedCount = ids.count { selected.contains(it) }
    return when {
        isEmpty() -> ToggleableState.Off
        selectedCount == size -> ToggleableState.On
        selectedCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}

private val previewMockSubjects = listOf(
    SubjectWithSlots(
        subject = Subject(
            id = 1L,
            name = "Sistemas Operativos",
            code = "SO",
            color = Subject.PRESET_COLORS[0],
            isActive = true
        ),
        slots = listOf(
            ClassSlot(
                id = 101L,
                subjectId = 1L,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0),
                classroom = "Aula 1.2",
                labGroupName = null,
                entryType = EntryType.THEORY,
                professor = "Dr. Smith",
            )
        )
    )
)

@Preview(name = "Subjects Screen - Light Mode", showBackground = true)
@Composable
private fun SubjectsContentPreviewLight() {
    ClemenTimeTheme {
        SubjectsContent(
            uiState = SubjectsUiState(subjects = previewMockSubjects),
            onEvent = {},
            onMenuClick = {},
            onNavigateToAddEditSubject = {},
            onNavigateToImport = {}
        )
    }
}