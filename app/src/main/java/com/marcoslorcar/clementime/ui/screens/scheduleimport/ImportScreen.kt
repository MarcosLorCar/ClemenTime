@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.scheduleimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.marcoslorcar.clementime.R

@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectAndSaveNewFile(context, it) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLibrary(context)
    }

    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Success) {
            onImportSuccess()
        }
    }

    when (uiState) {
        is ImportUiState.LoadingLibrary -> {
            ImportLibraryContent(
                files = emptyList(),
                searchQuery = "",
                onNavigateBack = onNavigateBack,
                onFileClick = {},
                onDeleteFileClick = {},
                onSelectNewFileClick = { filePickerLauncher.launch(arrayOf("application/json")) },
                onUpdateSearchQuery = {}
            )
        }
        is ImportUiState.Parsing, is ImportUiState.Importing -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(100.dp))
            }
        }
        is ImportUiState.Library -> {
            val state = uiState as ImportUiState.Library
            ImportLibraryContent(
                files = state.files,
                searchQuery = state.searchQuery,
                error = state.error,
                onNavigateBack = onNavigateBack,
                onFileClick = { viewModel.loadFile(context, it) },
                onDeleteFileClick = { viewModel.deleteFile(context, it) },
                onSelectNewFileClick = { filePickerLauncher.launch(arrayOf("application/json")) },
                onUpdateSearchQuery = viewModel::updateSearchQuery
            )
        }
        is ImportUiState.Selection -> {
            ImportContent(
                uiState = uiState as ImportUiState.Selection,
                onToggleSubject = viewModel::toggleSubjectSelection,
                onToggleSection = viewModel::toggleSectionSubjects,
                onDeselectAll = viewModel::deselectAll,
                onUpdateSearchQuery = viewModel::updateSearchQuery,
                onConfirmImport = viewModel::confirmImport,
                onResetState = { viewModel.resetToLibrary(context) },
                onMarkConflictTooltipSeen = viewModel::markConflictTooltipSeen,
                onMarkPreviewTooltipSeen = viewModel::markPreviewTooltipSeen
            )
        }
        is ImportUiState.Error -> {
            val state = uiState as ImportUiState.Error
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.resetToLibrary(context) }) {
                    Text(stringResource(R.string.try_again))
                }
            }
        }
        else -> {}
    }
}
