package com.example.clementime.ui.screens.scheduleimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.clementime.R
import com.example.clementime.data.importing.model.JsonGroup
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.JsonYear
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.model.SelectedMatter
import com.example.clementime.ui.components.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme

@Composable
fun ImportScreen(
    onMenuClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadJsonFromUri(context, it) }
    }

    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Success) {
            onNavigateBack()
        }
    }

    ImportContent(
        uiState = uiState,
        onMenuClick = onMenuClick,
        onSelectFileClick = { filePickerLauncher.launch("application/json") },
        onToggleMatter = { matter, group -> viewModel.toggleMatterSelection(matter, group) },
        onToggleAll = { viewModel.toggleAllMatters(it) },
        onToggleSection = { viewModel.toggleSectionMatters(it) },
        onUpdateSearchQuery = viewModel::updateSearchQuery,
        onConfirmImport = { viewModel.confirmImport() },
        onResetState = { viewModel.resetState() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportContent(
    uiState: ImportUiState,
    onMenuClick: () -> Unit,
    onSelectFileClick: () -> Unit,
    onToggleMatter: (JsonMatter, String) -> Unit,
    onToggleAll: (Collection<SelectedMatter>) -> Unit,
    onToggleSection: (Collection<SelectedMatter>) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onResetState: () -> Unit
) {
    var isSearchVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(R.string.import_schedule_title),
                onMenuClick = onMenuClick,
                actions = {
                    if (uiState is ImportUiState.Selection) {
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
                                contentDescription = if (isSearchVisible) "Close search" else "Search subjects"
                            )
                        }
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
            when (uiState) {
                is ImportUiState.Idle -> {
                    Button(onClick = onSelectFileClick) {
                        Text(stringResource(R.string.select_file_button))
                    }
                }

                is ImportUiState.Parsing, ImportUiState.Importing -> {
                    CircularProgressIndicator()
                }

                is ImportUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onResetState) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }

                is ImportUiState.Selection -> {
                    val availableYears = remember(uiState.schema) {
                        val years = uiState.schema.years.map { it.name }.sorted()
                        if (uiState.schema.matters.isNotEmpty()) {
                            (listOf("General") + years).distinct()
                        } else {
                            years.distinct()
                        }
                    }

                    var selectedYearFilter by remember { mutableStateOf<String?>(availableYears.firstOrNull()) }

                    // All matters in the schema flattened with their group/year info
                    val allFlattenedMatters = remember(uiState.schema) {
                        val fromRoot = uiState.schema.matters.map { SelectedMatter(it, "General") }
                        val fromYears = uiState.schema.years.flatMap { year ->
                            val yearCommon = year.matters.map { SelectedMatter(it, "${year.name} Common") }
                            val fromGroups = year.groups.flatMap { group ->
                                group.matters.map { SelectedMatter(it, "${year.name} ${group.name}") }
                            }
                            yearCommon + fromGroups
                        }
                        fromRoot + fromYears
                    }

                    val filteredMatters = remember(allFlattenedMatters, selectedYearFilter, uiState.searchQuery) {
                        allFlattenedMatters.filter { selected ->
                            val yearMatch = selectedYearFilter == null || selected.courseGroup.startsWith(selectedYearFilter!!)
                            val queryMatch = uiState.searchQuery.isBlank() ||
                                    selected.matter.name.contains(uiState.searchQuery, ignoreCase = true) ||
                                    selected.matter.code.contains(uiState.searchQuery, ignoreCase = true)
                            yearMatch && queryMatch
                        }
                    }

                    val groupedMatters = remember(filteredMatters) {
                        filteredMatters.groupBy { it.courseGroup }
                    }

                    val globalToggleState = remember(filteredMatters, uiState.selectedMatters) {
                        filteredMatters.calculateToggleState(uiState.selectedMatters)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = uiState.schema.title ?: "Select Subjects to Import",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        AnimatedVisibility(visible = isSearchVisible) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onUpdateSearchQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                placeholder = { Text("Search subjects...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onUpdateSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
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
                                            selectedYearFilter = year
                                        },
                                        label = { Text(year) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAll(filteredMatters) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TriStateCheckbox(
                                    state = globalToggleState,
                                    onClick = { onToggleAll(filteredMatters) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (globalToggleState == ToggleableState.On) {
                                            stringResource(R.string.deselect_all)
                                        } else {
                                            stringResource(R.string.select_all)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.selected_count,
                                            filteredMatters.count { uiState.selectedMatters.contains(it) },
                                            filteredMatters.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            groupedMatters.forEach { (fullGroupName, selectedMatters) ->
                                val sectionToggleState = selectedMatters.calculateToggleState(uiState.selectedMatters)

                                item(key = "header_$fullGroupName") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 6.dp)
                                            .clickable { onToggleSection(selectedMatters) },
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
                                                onClick = { onToggleSection(selectedMatters) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
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
                                                    selectedMatters.count { uiState.selectedMatters.contains(it) },
                                                    selectedMatters.size
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = selectedMatters,
                                    key = { "${fullGroupName}_${it.matter.code}" }
                                ) { selected ->
                                    val isSelected = uiState.selectedMatters.contains(selected)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleMatter(selected.matter, fullGroupName) }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { onToggleMatter(selected.matter, fullGroupName) }
                                        )
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(text = selected.matter.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = selected.matter.code,
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
                            enabled = uiState.selectedMatters.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.import_selected_button, uiState.selectedMatters.size))
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ImportContentPreview() {
    val year1 = JsonYear(
        name = "1º",
        matters = listOf(
            JsonMatter(code = "COMMON1", name = "Year 1 Common Subject")
        ),
        groups = listOf(
            JsonGroup(
                name = "A",
                matters = listOf(
                    JsonMatter(code = "FP1", name = "Fundamentos de Programación 1"),
                    JsonMatter(code = "CALC", name = "Cálculo Infinitesimal"),
                )
            ),
            JsonGroup(
                name = "B",
                matters = listOf(
                    JsonMatter(code = "ALGB", name = "Álgebra Lineal"),
                )
            )
        )
    )

    val sampleSchema = ScheduleJsonSchema(
        title = "Primer Cuatrimestre 2026/2027",
        matters = listOf(
            JsonMatter(code = "GEN1", name = "General Seminar")
        ),
        years = listOf(year1)
    )

    val selectedMatters = setOf(
        SelectedMatter(sampleSchema.matters[0], "General"),
        SelectedMatter(year1.matters[0], "1º Common")
    )

    ClemenTimeTheme {
        ImportContent(
            uiState = ImportUiState.Selection(
                schema = sampleSchema,
                selectedMatters = selectedMatters
            ),
            onMenuClick = {},
            onSelectFileClick = {},
            onToggleMatter = { _, _ -> },
            onToggleAll = {},
            onToggleSection = {},
            onUpdateSearchQuery = {},
            onConfirmImport = {},
            onResetState = {}
        )
    }
}

private fun Collection<SelectedMatter>.calculateToggleState(selected: Set<SelectedMatter>): ToggleableState {
    val selectedCount = count { selected.contains(it) }
    return when {
        isEmpty() -> ToggleableState.Off
        selectedCount == size -> ToggleableState.On
        selectedCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
}