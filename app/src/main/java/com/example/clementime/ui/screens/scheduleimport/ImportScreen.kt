package com.example.clementime.ui.screens.scheduleimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clementime.R
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.ui.navigation.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onMenuClick: () -> Unit,
    onNavigateBack: () -> Unit
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
        onToggleMatter = { viewModel.toggleMatterSelection(it) },
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
    onToggleMatter: (JsonMatter) -> Unit,
    onConfirmImport: () -> Unit,
    onResetState: () -> Unit
) {
    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(R.string.import_schedule_title),
                onMenuClick = onMenuClick
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
                        Text("Select Schedule JSON File")
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
                            Text("Try Again")
                        }
                    }
                }

                is ImportUiState.Selection -> {
                    // Extract all unique course groups present in the schema (e.g. "1º A", "2º B")
                    val availableGroups = remember(uiState.schema) {
                        uiState.schema.matters
                            .mapNotNull { it.courseGroup }
                            .distinct()
                            .sorted()
                    }

                    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }

                    val filteredMatters = remember(uiState.schema, selectedGroupFilter) {
                        if (selectedGroupFilter == null) {
                            uiState.schema.matters
                        } else {
                            uiState.schema.matters.filter { it.courseGroup == selectedGroupFilter }
                        }
                    }

                    val groupedMatters = remember(filteredMatters) {
                        filteredMatters.groupBy { it.courseGroup ?: "General" }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = uiState.schema.title ?: "Select Subjects to Import",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter Chips for Course Groups
                        if (availableGroups.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedGroupFilter == null,
                                    onClick = { selectedGroupFilter = null },
                                    label = { Text("All") }
                                )
                                availableGroups.forEach { group ->
                                    FilterChip(
                                        selected = selectedGroupFilter == group,
                                        onClick = {
                                            selectedGroupFilter = if (selectedGroupFilter == group) null else group
                                        },
                                        label = { Text(group) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            groupedMatters.forEach { (groupName, matters) ->
                                item(key = "header_$groupName") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = groupName,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                items(
                                    items = matters,
                                    key = { it.code }
                                ) { jsonMatter ->
                                    val isSelected = uiState.selectedMatters.contains(jsonMatter)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { onToggleMatter(jsonMatter) }
                                        )
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(text = jsonMatter.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = jsonMatter.code,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onConfirmImport,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.selectedMatters.isNotEmpty()
                        ) {
                            Text("Import Selected (${uiState.selectedMatters.size})")
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
    val sampleMatters = listOf(
        JsonMatter(code = "FP1", name = "Fundamentos de Programación 1", courseGroup = "1º A"),
        JsonMatter(code = "CALC", name = "Cálculo Infinitesimal", courseGroup = "1º A"),
        JsonMatter(code = "ALGB", name = "Álgebra Lineal", courseGroup = "1º B"),
        JsonMatter(code = "EDO", name = "Ecuaciones Diferenciales", courseGroup = "2º A"),
        JsonMatter(code = "AOC", name = "Arquitectura de Computadores", courseGroup = "2º A"),
    )

    val sampleSchema = ScheduleJsonSchema(
        title = "Primer Cuatrimestre 2026/2027",
        matters = sampleMatters
    )

    ClemenTimeTheme {
        ImportContent(
            uiState = ImportUiState.Selection(
                schema = sampleSchema,
                selectedMatters = sampleMatters.take(2).toSet()
            ),
            onMenuClick = {},
            onSelectFileClick = {},
            onToggleMatter = {},
            onConfirmImport = {},
            onResetState = {}
        )
    }
}