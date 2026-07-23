package com.github.marcoslorcar.clementime.ui.screens.scheduleimport

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.importing.model.ImportFile
import com.github.marcoslorcar.clementime.data.importing.model.JsonGroup
import com.github.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.github.marcoslorcar.clementime.data.importing.model.JsonYear
import com.github.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictDetail
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.github.marcoslorcar.clementime.utils.fadingEdges

@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLibrary(context)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectAndSaveNewFile(context, it) }
    }

    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Success) {
            onNavigateBack()
        }
    }

    if (uiState is ImportUiState.Selection) {
        BackHandler {
            viewModel.resetToLibrary(context)
        }
    }

    when (val state = uiState) {
        is ImportUiState.LoadingLibrary, ImportUiState.Parsing, ImportUiState.Importing -> {
            Scaffold(
                topBar = {
                    ClemenTimeTopBar(
                        title = stringResource(R.string.import_schedule_title),
                        onNavigateBack = onNavigateBack
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        is ImportUiState.Library -> {
            ImportLibraryContent(
                files = state.files,
                onNavigateBack = onNavigateBack,
                onFileClick = { file -> viewModel.loadFile(context, file) },
                onDeleteFileClick = { file -> viewModel.deleteFile(context, file) },
                onSelectNewFileClick = { filePickerLauncher.launch("application/json") }
            )
        }
        is ImportUiState.Selection -> {
            ImportContent(
                uiState = state,
                onToggleSubject = { subject, group -> viewModel.toggleSubjectSelection(subject, group) },
                onToggleSection = { subjects -> viewModel.toggleSectionSubjects(subjects) },
                onDeselectAll = { viewModel.deselectAll() },
                onUpdateSearchQuery = viewModel::updateSearchQuery,
                onConfirmImport = { viewModel.confirmImport() },
                onResetState = { viewModel.resetToLibrary(context) }
            )
        }
        is ImportUiState.Error -> {
            Scaffold(
                topBar = {
                    ClemenTimeTopBar(
                        title = stringResource(R.string.import_schedule_title),
                        onNavigateBack = onNavigateBack
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadLibrary(context) }) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
            }
        }
        is ImportUiState.Success -> {
            // Handled by LaunchedEffect
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportLibraryContent(
    files: List<ImportFile>,
    onNavigateBack: () -> Unit,
    onFileClick: (ImportFile) -> Unit,
    onDeleteFileClick: (ImportFile) -> Unit,
    onSelectNewFileClick: () -> Unit
) {
    var fileToDelete by remember { mutableStateOf<ImportFile?>(null) }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.import_delete_confirm_title)) },
            text = { Text(stringResource(R.string.import_delete_confirm_message, file.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFileClick(file)
                        fileToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete_subject_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(R.string.import_schedule_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Select a schedule template from the library or import a custom schedule JSON file to pick subjects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val libraryListState = rememberLazyListState()

            LazyColumn(
                state = libraryListState,
                modifier = Modifier
                    .weight(1f)
                    .fadingEdges(libraryListState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileClick(file) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (file.isBundled) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (file.isBundled) R.string.import_bundled_label else R.string.import_custom_label
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = if (file.isBundled) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }
                            }
                            if (!file.isBundled) {
                                IconButton(onClick = { fileToDelete = file }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete custom file",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSelectNewFileClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.import_select_file_device))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportContent(
    uiState: ImportUiState.Selection,
    onToggleSubject: (JsonSubject, String) -> Unit,
    onToggleSection: (Collection<SelectedSubject>) -> Unit,
    onDeselectAll: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onResetState: () -> Unit
) {
    var isSearchVisible by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    val subjectsListState = rememberLazyListState()

    if (showConflictDialog && uiState.conflictStatus is ConflictStatus.Conflict) {
        ConflictDetailsDialog(
            detail = uiState.conflictStatus.detail,
            onDismiss = { showConflictDialog = false }
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
                                contentDescription = "No conflicts",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
                        }
                        is ConflictStatus.Conflict -> {
                            IconButton(onClick = { showConflictDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflicts detected",
                                    tint = MaterialTheme.colorScheme.error
                                )
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
                            contentDescription = if (isSearchVisible) "Close search" else "Search subjects"
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
                Text(
                    text = stringResource(R.string.import_screen_prompt),
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
                                    .clickable { onToggleSubject(selected.subject, fullGroupName) }
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

@Composable
fun ConflictDetailsDialog(
    detail: ConflictDetail,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_conflict_dialog_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (detail.theoryOverlaps.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_conflict_theory_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        detail.theoryOverlaps.forEach { overlap ->
                            Text(
                                text = "• ${overlap.subject1Name} & ${overlap.subject2Name}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    }
                    
                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_visualization),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScheduleMiniPreview(
                            modifier = Modifier.height(150.dp),
                            slots = detail.theoryOverlappingSlots,
                            overlappingSlotIds = detail.theoryOverlappingSlots.map { it.second.id }.toSet()
                        )
                    }
                }

                if (!detail.hasLabCombinationWithZeroOverlaps) {
                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_lab_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_conflict_lab_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ImportContentPreview() {
    val year1 = JsonYear(
        name = "1º",
        subjects = listOf(
            JsonSubject(code = "COMMON1", name = "Year 1 Common Subject")
        ),
        groups = listOf(
            JsonGroup(
                name = "A",
                subjects = listOf(
                    JsonSubject(code = "FP1", name = "Fundamentos de Programación 1"),
                    JsonSubject(code = "CALC", name = "Cálculo Infinitesimal"),
                )
            ),
            JsonGroup(
                name = "B",
                subjects = listOf(
                    JsonSubject(code = "ALGB", name = "Álgebra Lineal"),
                )
            )
        )
    )

    val sampleSchema = ScheduleJsonSchema(
        title = "Primer Cuatrimestre 2026/2027",
        subjects = listOf(
            JsonSubject(code = "GEN1", name = "General Seminar")
        ),
        years = listOf(year1)
    )

    val selectedSubjects = setOf(
        SelectedSubject(sampleSchema.subjects[0], "General"),
        SelectedSubject(year1.subjects[0], "1º Common")
    )

    ClemenTimeTheme {
        ImportContent(
            uiState = ImportUiState.Selection(
                schema = sampleSchema,
                selectedSubjects = selectedSubjects,
                selectedFile = ImportFile("bundled", "Horarios 2026/2027 - 1º Cuatrimestre", true, null)
            ),
            onToggleSubject = { _, _ -> },
            onToggleSection = { _ -> },
            onDeselectAll = {},
            onUpdateSearchQuery = {},
            onConfirmImport = {},
            onResetState = {}
        )
    }
}
