package com.example.clementime.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.clementime.R
import com.example.clementime.ui.components.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme

@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(
        checkNotNull(
            LocalViewModelStoreOwner.current
        ) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            }, null
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var exportStatus by remember { mutableStateOf<ExportStatus>(ExportStatus.Idle) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(context, uri) { status ->
                exportStatus = status
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setSyncDirectoryUri(uri.toString())
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to persist directory permissions: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(exportStatus) {
        when (exportStatus) {
            is ExportStatus.Success -> {
                Toast.makeText(context, (exportStatus as ExportStatus.Success).message, Toast.LENGTH_SHORT).show()
                exportStatus = ExportStatus.Idle
            }
            is ExportStatus.Error -> {
                val errorMsg = (exportStatus as ExportStatus.Error).error
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                exportStatus = ExportStatus.Idle
            }
            else -> {}
        }
    }

    SettingsContent(
        uiState = uiState,
        onSelectSyncPath = { folderPickerLauncher.launch(null) },
        onClearSyncPath = { viewModel.setSyncDirectoryUri(null) },
        onThemeChanged = viewModel::setThemeMode,
        onLanguageChanged = viewModel::setAppLanguage,
        onToggleScrollableTabs = viewModel::setScrollableTabs,
        onExportBackup = {
            if (uiState.syncDirectoryUri != null) {
                viewModel.exportBackup(context, null) { status ->
                    exportStatus = status
                }
            } else {
                createDocLauncher.launch("clementime_backup.json")
            }
        },
        onNavigateToImport = onNavigateToImport,
        onMenuClick = onMenuClick
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onSelectSyncPath: () -> Unit,
    onClearSyncPath: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onToggleScrollableTabs: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onNavigateToImport: () -> Unit,
    onMenuClick: () -> Unit
) {
    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(id = R.string.settings_screen_title),
                onMenuClick = onMenuClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Section: Interface ---
            Text(
                text = stringResource(R.string.interface_header),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme Row
                    var showThemeMenu by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.theme_setting_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Box {
                            val selectedThemeLabel = when (uiState.themeMode) {
                                "light" -> stringResource(R.string.theme_light)
                                "dark" -> stringResource(R.string.theme_dark)
                                else -> stringResource(R.string.theme_system)
                            }
                            OutlinedButton(onClick = { showThemeMenu = true }) {
                                Text(selectedThemeLabel)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.theme_system)) },
                                    onClick = {
                                        onThemeChanged("system")
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.theme_light)) },
                                    onClick = {
                                        onThemeChanged("light")
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.theme_dark)) },
                                    onClick = {
                                        onThemeChanged("dark")
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Language Row
                    var showLangMenu by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.language_setting_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Box {
                            val selectedLangLabel = when (uiState.appLanguage) {
                                "es" -> "Español"
                                else -> "English"
                            }
                            OutlinedButton(onClick = { showLangMenu = true }) {
                                Text(selectedLangLabel)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showLangMenu,
                                onDismissRequest = { showLangMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("English") },
                                    onClick = {
                                        onLanguageChanged("en")
                                        showLangMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Español") },
                                    onClick = {
                                        onLanguageChanged("es")
                                        showLangMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Tab Layout Customization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCompact,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tab_layout_setting_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (uiState.scrollableTabs) {
                                    stringResource(R.string.tab_layout_weekdays)
                                } else {
                                    stringResource(R.string.tab_layout_letters)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.scrollableTabs,
                            onCheckedChange = onToggleScrollableTabs
                        )
                    }
                }
            }

            // --- Section: Data & Storage ---
            Text(
                text = stringResource(R.string.data_storage_header),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Sync Folder Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.sync_directory_setting_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    val pathLabel = if (uiState.syncDirectoryUri != null) {
                        stringResource(R.string.sync_directory_path_label, uiState.syncDirectoryUri.substringAfterLast("%3A"))
                    } else {
                        stringResource(R.string.sync_directory_not_selected)
                    }

                    Text(
                        text = pathLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSelectSyncPath,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.choose_folder_button))
                        }
                        if (uiState.syncDirectoryUri != null) {
                            OutlinedButton(
                                onClick = onClearSyncPath,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.clear_folder_button))
                            }
                        }
                    }
                }
            }

            // Backup Actions
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportBackup
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.export_backup_setting_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.export_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToImport
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.import_schedule_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.import_schedule_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ClemenTimeTheme {
        SettingsContent(
            uiState = SettingsUiState(),
            onSelectSyncPath = {},
            onClearSyncPath = {},
            onThemeChanged = {},
            onLanguageChanged = {},
            onToggleScrollableTabs = {},
            onExportBackup = {},
            onNavigateToImport = {},
            onMenuClick = {}
        )
    }
}