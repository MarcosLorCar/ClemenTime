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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.text.style.TextOverflow
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
    onNavigateToImport: (String) -> Unit,
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
            viewModel.exportData(context, uri) { status ->
                exportStatus = status
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onNavigateToImport(uri.toString())
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
        onThemeChanged = viewModel::setThemeMode,
        onLanguageChanged = viewModel::setAppLanguage,
        onToggleScrollableTabs = viewModel::setScrollableTabs,
        onExportData = {
            createDocLauncher.launch("clementime_export.json")
        },
        onImportClick = {
            filePickerLauncher.launch("application/json")
        },
        onMenuClick = onMenuClick
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onToggleScrollableTabs: (Boolean) -> Unit,
    onExportData: () -> Unit,
    onImportClick: () -> Unit,
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

            // Theme Setting Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.theme_setting_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val selectedThemeLabel = when (uiState.themeMode) {
                                "light" -> stringResource(R.string.theme_light)
                                "dark" -> stringResource(R.string.theme_dark)
                                else -> stringResource(R.string.theme_system)
                            }
                            var showThemeMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showThemeMenu = true },
                                    modifier = Modifier.widthIn(max = 180.dp)
                                ) {
                                    Text(
                                        text = selectedThemeLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
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
                    }
                }
            }

            // Language Setting Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.language_setting_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val selectedLangLabel = when (uiState.appLanguage) {
                                "es" -> "Español"
                                else -> "English"
                            }
                            var showLangMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showLangMenu = true },
                                    modifier = Modifier.widthIn(max = 180.dp)
                                ) {
                                    Text(
                                        text = selectedLangLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
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
                    }
                }
            }

            // Tab Layout Setting Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCompact,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
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

            // Export Data Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportData
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

            // Import Data Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onImportClick
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
            onThemeChanged = {},
            onLanguageChanged = {},
            onToggleScrollableTabs = {},
            onExportData = {},
            onImportClick = {},
            onMenuClick = {}
        )
    }
}