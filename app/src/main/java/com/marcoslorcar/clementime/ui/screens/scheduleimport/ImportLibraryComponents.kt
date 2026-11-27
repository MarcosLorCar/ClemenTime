@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.scheduleimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.ImportSourceType
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
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
    onUpdateSearchQuery: (String) -> Unit,
    onCreateSubjectClick: () -> Unit = {}
) {
    var fileToDelete by remember { mutableStateOf<ImportFile?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                (it.description?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.universityName?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.universityId?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.facultyName?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.facultyId?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.termId?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val remoteFiles = remember(filteredFiles) {
        filteredFiles.filter { it.sourceType == ImportSourceType.REMOTE }
    }
    val customFiles = remember(filteredFiles) {
        filteredFiles.filter { it.sourceType == ImportSourceType.CUSTOM }
    }
    val groupedRemoteFiles = remember(remoteFiles) {
        remoteFiles.groupBy { it.universityId.orEmpty() to it.facultyId.orEmpty() }
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
            } else {
                val libraryListState = rememberLazyListState()
                val defaultRemoteHeader = stringResource(R.string.online_repository_title)

                LazyColumn(
                    state = libraryListState,
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(libraryListState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredFiles.isEmpty() && files.isNotEmpty()) {
                        item(key = "no_results") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.import_library_no_results),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (remoteFiles.isNotEmpty()) {
                        groupedRemoteFiles.forEach { (groupKey, groupFiles) ->
                            val first = groupFiles.first()
                            val uniLabel = first.universityName?.takeIf { it.isNotBlank() }
                                ?: first.universityId?.takeIf { it.isNotBlank() }?.uppercase()
                            val facLabel = first.facultyName?.takeIf { it.isNotBlank() }
                                ?: first.facultyId?.takeIf { it.isNotBlank() }?.uppercase()
                            val headerTitle = listOfNotNull(uniLabel, facLabel)
                                .joinToString(" · ")
                                .ifBlank { defaultRemoteHeader }
                            val isLinkedGroup = groupFiles.any { it.isLinkedFaculty }

                            item(key = "header_remote_${groupKey.first}_${groupKey.second}") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = headerTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isLinkedGroup) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.import_faculty_linked_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(groupFiles, key = { it.id }) { file ->
                                ImportFileItem(
                                    file = file,
                                    onFileClick = onFileClick,
                                    onDeleteClick = { fileToDelete = file }
                                )
                            }
                        }
                    }

                    if (customFiles.isNotEmpty()) {
                        item(key = "header_custom") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.import_custom_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        items(customFiles, key = { it.id }) { file ->
                            ImportFileItem(
                                file = file,
                                onFileClick = onFileClick,
                                onDeleteClick = { fileToDelete = file }
                            )
                        }
                    }

                    item(key = "unlisted_university_card") {
                        Spacer(modifier = Modifier.height(8.dp))
                        UnlistedUniversityCard(
                            onCreateSubjectClick = onCreateSubjectClick,
                            onAddCustomFileClick = onSelectNewFileClick
                        )
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
private fun ImportFileItem(
    file: ImportFile,
    onFileClick: (ImportFile) -> Unit,
    onDeleteClick: () -> Unit
) {
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
                    if (file.isUpdateAvailable) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = stringResource(R.string.import_update_available_label),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
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
            if (file.sourceType == ImportSourceType.CUSTOM) {
                IconButton(onClick = onDeleteClick) {
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

@Composable
private fun UnlistedUniversityCard(
    onCreateSubjectClick: () -> Unit,
    onAddCustomFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.import_unlisted_university_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = stringResource(R.string.import_unlisted_university_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onCreateSubjectClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_unlisted_create_subject))
                }
                OutlinedButton(onClick = onAddCustomFileClick) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.import_unlisted_pick_json))
                }
            }
        }
    }
}
