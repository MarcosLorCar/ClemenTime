package com.example.clementime.ui.screens.matter

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clementime.R
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.cardColor
import com.example.clementime.ui.components.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun MattersScreen(
    onMenuClick: () -> Unit,
    onNavigateToAddEditMatter: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek) -> Unit = {},
    viewModel: MattersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MattersContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onMenuClick = onMenuClick,
        onNavigateToAddEditMatter = onNavigateToAddEditMatter,
        onNavigateToSchedule = onNavigateToSchedule
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MattersContent(
    uiState: MattersUiState,
    onEvent: (MattersUiEvent) -> Unit,
    onMenuClick: () -> Unit,
    onNavigateToAddEditMatter: (Long?) -> Unit,
    onNavigateToSchedule: (DayOfWeek) -> Unit = {}
) {
    var matterToDelete by remember { mutableStateOf<Matter?>(null) }
    var showNukeDialog by remember { mutableStateOf(false) }

    matterToDelete?.let { matter ->
        AlertDialog(
            onDismissRequest = { matterToDelete = null },
            title = { Text(stringResource(R.string.delete_matter_dialog_title)) },
            text = { Text(stringResource(R.string.delete_matter_dialog_message, matter.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MattersUiEvent.DeleteMatter(matter.id))
                        matterToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete_matter_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { matterToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showNukeDialog) {
        AlertDialog(
            onDismissRequest = { showNukeDialog = false },
            title = { Text(stringResource(R.string.nuke_all_matters_title)) },
            text = { Text(stringResource(R.string.nuke_all_matters_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MattersUiEvent.NukeAllMatters)
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
            text = { Text(stringResource(R.string.delete_selected_dialog_message, uiState.selectedMatterIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MattersUiEvent.DeleteSelectedMatters)
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
    var selectedGroupFilter by remember { mutableStateOf("All") }

    val availableFilters = remember(uiState.matters) {
        val groups = uiState.matters.mapNotNull { it.matter.courseGroup }.filter { it.isNotBlank() }.distinct().sorted()
        val filters = mutableListOf("All")
        filters.addAll(groups)
        filters.add("Inactive")
        filters
    }

    val filteredMatters = remember(uiState.filteredMatters, selectedGroupFilter) {
        when (selectedGroupFilter) {
            "All", "" -> uiState.filteredMatters
            "Inactive" -> uiState.filteredMatters.filter { !it.matter.isActive }
            else -> uiState.filteredMatters.filter { it.matter.courseGroup == selectedGroupFilter }
        }
    }

    val groupedMatters = remember(filteredMatters) {
        filteredMatters.groupBy { it.matter.courseGroup?.takeIf { g -> g.isNotBlank() } ?: "General" }
    }

    Scaffold(
        topBar = {
            if (uiState.isInSelectionMode) {
                ClemenTimeTopBar(
                    title = stringResource(R.string.selected_count_simple, uiState.selectedMatterIds.size),
                    onNavigateBack = { onEvent(MattersUiEvent.ClearSelection) },
                    actions = {
                        if (uiState.selectedMatterIds.isNotEmpty()) {
                            IconButton(onClick = { onEvent(MattersUiEvent.DisableSelectedMatters) }) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Disable selected matters",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showDeleteSelectedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete selected matters",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                isFilterVisible = !isFilterVisible
                                if (!isFilterVisible) {
                                    selectedGroupFilter = "All"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter matters",
                                tint = if (isFilterVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    onEvent(MattersUiEvent.SearchQueryChanged(""))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search matters"
                            )
                        }
                    }
                )
            } else {
                ClemenTimeTopBar(
                    title = stringResource(R.string.matters_screen_title),
                    onMenuClick = onMenuClick,
                    actions = {
                        if (uiState.matters.isNotEmpty()) {
                            IconButton(onClick = { showNukeDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Nuke all matters",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onEvent(MattersUiEvent.EnterSelectionMode)
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
                                    selectedGroupFilter = "All"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter matters",
                                tint = if (isFilterVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    onEvent(MattersUiEvent.SearchQueryChanged(""))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search matters"
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isInSelectionMode) {
                FloatingActionButton(
                    onClick = { onNavigateToAddEditMatter(null) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Matter")
                }
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
        } else if (uiState.matters.isEmpty()) {
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
                        text = stringResource(R.string.no_matters_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_matters_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
                        onValueChange = { onEvent(MattersUiEvent.SearchQueryChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_matters_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onEvent(MattersUiEvent.SearchQueryChanged("")) }) {
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
                                    onClick = { selectedGroupFilter = filter },
                                    label = { Text(filter) }
                                )
                            }
                        }
                    }
                }

                if (filteredMatters.isEmpty()) {
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedMatters.forEach { (groupName, mattersInGroup) ->
                            item(key = "header_$groupName") {
                                if (uiState.isInSelectionMode) {
                                    val sectionToggleState = mattersInGroup.calculateToggleState(uiState.selectedMatterIds)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 6.dp)
                                            .clickable {
                                                onEvent(MattersUiEvent.ToggleGroupSelection(mattersInGroup.map { it.matter.id }))
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
                                                    onEvent(MattersUiEvent.ToggleGroupSelection(mattersInGroup.map { it.matter.id }))
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
                                            val selectedInGroup = mattersInGroup.count { uiState.selectedMatterIds.contains(it.matter.id) }
                                            Text(
                                                text = stringResource(
                                                    R.string.section_selected_count,
                                                    selectedInGroup,
                                                    mattersInGroup.size
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
                                items = mattersInGroup,
                                key = { it.matter.id }
                            ) { matterWithSlots ->
                                val isSelected = uiState.selectedMatterIds.contains(matterWithSlots.matter.id)
                                MatterItemCard(
                                    matterWithSlots = matterWithSlots,
                                    isInSelectionMode = uiState.isInSelectionMode,
                                    isSelected = isSelected,
                                    onToggleActive = { isActive ->
                                        onEvent(MattersUiEvent.ToggleMatterActive(matterWithSlots.matter.id, isActive))
                                    },
                                    onEditClick = {
                                        onNavigateToAddEditMatter(matterWithSlots.matter.id)
                                    },
                                    onDeleteClick = {
                                        matterToDelete = matterWithSlots.matter
                                    },
                                    onNavigateToSchedule = onNavigateToSchedule,
                                    onToggleSelection = {
                                        onEvent(MattersUiEvent.ToggleMatterSelection(matterWithSlots.matter.id))
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
private fun MatterItemCard(
    matterWithSlots: MatterWithSlots,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleActive: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNavigateToSchedule: (DayOfWeek) -> Unit,
    onToggleSelection: () -> Unit
) {
    val matter = matterWithSlots.matter
    var isExpanded by remember { mutableStateOf(false) }

    val cardBgColor = if (matter.isActive) {
        matter.cardColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        text = matter.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (matter.isActive) {
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
                        val slotCountText = stringResource(R.string.slots_count, matterWithSlots.slots.size)
                        Text(
                            text = "${matter.code} • $slotCountText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (matter.isActive) {
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
                                    modifier = Modifier.clickable { onToggleActive(!matter.isActive) }
                                ) {
                                    Text(
                                        text = stringResource(if (matter.isActive) R.string.active else R.string.inactive),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (matter.isActive) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = matter.isActive,
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
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.edit_matter))
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
                                        Text(stringResource(R.string.delete_matter))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (matterWithSlots.slots.isEmpty()) {
                                Text(
                                    text = "No schedule slots assigned.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                matterWithSlots.slots.forEach { slot ->
                                    SlotDetailRow(
                                        slot = slot,
                                        onSlotClick = { onNavigateToSchedule(slot.dayOfWeek) }
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
        }
    }
}

private fun Collection<MatterWithSlots>.calculateToggleState(selected: Set<Long>): ToggleableState {
    val ids = map { it.matter.id }
    val selectedCount = ids.count { selected.contains(it) }
    return when {
        isEmpty() -> ToggleableState.Off
        selectedCount == size -> ToggleableState.On
        selectedCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}

private val previewMockMatters = listOf(
    MatterWithSlots(
        matter = Matter(
            id = 1L,
            name = "Sistemas Operativos",
            code = "SO",
            color = Matter.PRESET_COLORS[0],
            isActive = true
        ),
        slots = listOf(
            ClassSlot(
                id = 101L,
                matterId = 1L,
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

@Preview(name = "Matters Screen - Light Mode", showBackground = true)
@Composable
private fun MattersContentPreviewLight() {
    ClemenTimeTheme {
        MattersContent(
            uiState = MattersUiState(matters = previewMockMatters),
            onEvent = {},
            onMenuClick = {},
            onNavigateToAddEditMatter = {}
        )
    }
}