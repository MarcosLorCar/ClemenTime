@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.scheduleimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictDetail
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import com.marcoslorcar.clementime.utils.fadingEdges

@Composable
fun ImportLibraryContent(
    files: List<ImportFile>,
    searchQuery: String,
    error: String? = null,
    onNavigateBack: () -> Unit,
    onFileClick: (ImportFile) -> Unit,
    onDeleteFileClick: (ImportFile) -> Unit,
    onSelectNewFileClick: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit
) {
    var fileToDelete by remember { mutableStateOf<ImportFile?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            (it.description?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val groupedFiles = remember(filteredFiles) {
        filteredFiles.groupBy { it.sourceType }
    }

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
                onNavigateBack = onNavigateBack,
                actions = {
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
                            contentDescription = if (isSearchVisible) stringResource(R.string.content_description_close_search) else stringResource(R.string.content_description_search_schedules)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onUpdateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_schedules_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.content_description_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape
                )
            }

            if (!error.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.content_description_warning),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (files.isEmpty() && error.contains("unreachable", ignoreCase = true)) 
                                stringResource(R.string.import_library_online_unreachable) 
                                else error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (files.isEmpty() && error.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(100.dp))
                }
            } else if (filteredFiles.isEmpty() && files.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.import_library_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val libraryListState = rememberLazyListState()

                LazyColumn(
                    state = libraryListState,
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(libraryListState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedFiles.forEach { (sourceType, typeFiles) ->
                        item(key = "header_${sourceType.name}") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = when (sourceType) {
                                        com.marcoslorcar.clementime.data.importing.model.ImportSourceType.REMOTE -> stringResource(R.string.online_repository_title)
                                        com.marcoslorcar.clementime.data.importing.model.ImportSourceType.CUSTOM -> stringResource(R.string.import_custom_label)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        items(typeFiles, key = { it.id }) { file ->
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
                                        if (!file.description.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = file.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (file.isCached) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFC8E6C9)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.import_cached_label),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                            if (!file.updatedTime.isNullOrBlank()) {
                                                Text(
                                                    text = stringResource(R.string.bullet_point, file.updatedTime),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                    if (file.sourceType == com.marcoslorcar.clementime.data.importing.model.ImportSourceType.CUSTOM) {
                                        IconButton(onClick = { fileToDelete = file }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.content_description_delete_custom_file),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
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

@Composable
fun ImportContent(
    uiState: ImportUiState.Selection,
    onToggleSubject: (SelectedSubject) -> Unit,
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
                        is ConflictStatus.Error -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = uiState.conflictStatus.message,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
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
            val allFlattenedSubjects = remember(uiState.schema, uiState.selectedFile.remotePath) {
                val remotePath = uiState.selectedFile.remotePath
                val fromRoot = uiState.schema.subjects.map { 
                    SelectedSubject(it, "General", remotePath) 
                }
                val fromYears = uiState.schema.years.flatMap { year ->
                    val yearCommon = year.subjects.map { 
                        SelectedSubject(it, "${year.name} Common", remotePath) 
                    }
                    val fromGroups = year.groups.flatMap { group ->
                        group.subjects.map { 
                            SelectedSubject(it, "${year.name} ${group.name}", remotePath) 
                        }
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
                                        onClick = { onToggleSubject(selected) },
                                        onLongClick = { longPressedSubject = selected }
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSubject(selected) }
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
fun ImportPreviewDialog(
    previewSubject: SelectedSubject,
    selectedSubjects: Set<SelectedSubject>,
    existingSubjects: List<SubjectWithSlots>,
    onDismiss: () -> Unit
) {
    val cumulativeSlots = remember(previewSubject, selectedSubjects, existingSubjects) {
        val parser = JsonScheduleParser()
        var slotIdCounter = 1000L // Use high IDs for preview slots to avoid conflict if any
        
        val slotsList = mutableListOf<Pair<Subject, ClassSlot>>()

        // 1. Existing subjects
        existingSubjects.forEach { sWithSlots ->
            sWithSlots.slots.forEach { slot ->
                if (slot.entryType == EntryType.THEORY ||
                    sWithSlots.subject.selectedLabGroup == null ||
                    slot.labGroupName == sWithSlots.subject.selectedLabGroup
                ) {
                    slotsList.add(sWithSlots.subject to slot.copy(id = slotIdCounter++))
                }
            }
        }

        // 2. Selected subjects (from current import session)
        selectedSubjects.filter { it != previewSubject }.forEach { sel ->
            val subj = Subject(
                id = sel.subject.code.hashCode().toLong(),
                code = sel.subject.code,
                name = sel.subject.name,
                color = sel.subject.color ?: Subject.PRESET_COLORS.indices.random(),
                isActive = true
            )
            // Theory slots
            sel.subject.theorySlots.forEach { jsonSlot ->
                slotsList.add(subj to with(parser) { jsonSlot.toClassSlot(subj.id).copy(id = slotIdCounter++) })
            }
            // Lab variants
            sel.subject.labVariants.forEach { (group, variantSlots) ->
                variantSlots.forEach { jsonSlot ->
                    slotsList.add(subj to with(parser) {
                        jsonSlot.toClassSlot(subj.id).copy(
                            id = slotIdCounter++,
                            labGroupName = group,
                            entryType = EntryType.LAB
                        )
                    })
                }
            }
        }

        // 3. Long-pressed subject (Preview)
        val previewSubjObj = Subject(
            id = previewSubject.subject.code.hashCode().toLong(),
            code = previewSubject.subject.code,
            name = previewSubject.subject.name,
            color = previewSubject.subject.color ?: Subject.PRESET_COLORS.indices.random(),
            isActive = true
        )
        previewSubject.subject.theorySlots.forEach { jsonSlot ->
            slotsList.add(previewSubjObj to with(parser) { jsonSlot.toClassSlot(previewSubjObj.id).copy(id = slotIdCounter++) })
        }
        previewSubject.subject.labVariants.forEach { (group, variantSlots) ->
            variantSlots.forEach { jsonSlot ->
                slotsList.add(previewSubjObj to with(parser) {
                    jsonSlot.toClassSlot(previewSubjObj.id).copy(
                        id = slotIdCounter++,
                        labGroupName = group,
                        entryType = EntryType.LAB
                    )
                })
            }
        }

        slotsList
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.import_preview_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = previewSubject.subject.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                ScheduleMiniPreview(
                    modifier = Modifier.fillMaxSize(),
                    slots = cumulativeSlots,
                    overlappingSlotIds = emptySet()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
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
                                text = stringResource(R.string.bullet_point_with_ampersand, overlap.subject1Name, overlap.subject2Name),
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

                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_visualization),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScheduleMiniPreview(
                            modifier = Modifier.height(150.dp),
                            slots = detail.labOverlappingSlots,
                            overlappingSlotIds = detail.labOverlappingSlotIds
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
