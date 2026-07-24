package com.github.marcoslorcar.clementime.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.github.marcoslorcar.clementime.utils.fadingEdges
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.github.marcoslorcar.clementime.ui.components.AppSkeletonPreview
import com.github.marcoslorcar.clementime.ui.theme.getThemeColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme

@Composable
fun SettingsScreen(
    onNavigateToImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(
        checkNotNull(
            LocalViewModelStoreOwner.current
        ) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            }, null
    ),
    onMenuClick: (() -> Unit)? = null
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
        onToggleShowNowLine = viewModel::setShowNowLine,
        onNowLineStyleChanged = viewModel::setNowLineStyle,
        onToggleHighContrast = viewModel::setHighContrast,
        onThemeSelected = viewModel::setSelectedTheme,
        onGithubRepoUrlChanged = viewModel::setGithubRepoBaseUrl,
        onExportData = {
            createDocLauncher.launch("clementime_export.json")
        },
        onImportClick = onNavigateToImport,
        onMenuClick = onMenuClick
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    if (onClick != null) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            SettingItemContent(icon, title, subtitle, trailingContent, expandedContent)
        }
    } else {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingItemContent(icon, title, subtitle, trailingContent, expandedContent)
        }
    }
}

@Composable
private fun SettingItemContent(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailingContent?.invoke()
        }
        expandedContent?.invoke()
    }
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onToggleScrollableTabs: (Boolean) -> Unit,
    onToggleShowNowLine: (Boolean) -> Unit,
    onNowLineStyleChanged: (String) -> Unit,
    onToggleHighContrast: (Boolean) -> Unit,
    onThemeSelected: (String) -> Unit,
    onGithubRepoUrlChanged: (String) -> Unit,
    onExportData: () -> Unit,
    onImportClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    var showRepoUrlDialog by remember { mutableStateOf(false) }
    var tempRepoUrl by remember { mutableStateOf(uiState.githubRepoBaseUrl) }

    if (showRepoUrlDialog) {
        AlertDialog(
            onDismissRequest = { showRepoUrlDialog = false },
            title = { Text("GitHub Repository URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the GitHub raw content base URL for online schedules:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = tempRepoUrl,
                        onValueChange = { tempRepoUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Base URL") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onGithubRepoUrlChanged(tempRepoUrl)
                        showRepoUrlDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepoUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Section: Featured ---
            SettingItem(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringResource(R.string.import_library_title),
                subtitle = stringResource(R.string.import_library_desc),
                onClick = onImportClick
            )

            // --- Section: Appearance ---
            Text(
                text = stringResource(R.string.theme_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            val themes = listOf(
                "clementine" to stringResource(R.string.theme_clementine),
                "blueberry" to stringResource(R.string.theme_blueberry),
                "matcha" to stringResource(R.string.theme_matcha),
                "espresso" to stringResource(R.string.theme_espresso),
                "grape" to stringResource(R.string.theme_grape)
            )

            val themeListState = rememberLazyListState()
            LazyRow(
                state = themeListState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges(themeListState, horizontal = true)
            ) {
                items(themes) { (id, name) ->
                    val systemIsDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val isPreviewDark = when (uiState.themeMode) {
                        "dark" -> true
                        "light" -> false
                        else -> systemIsDark
                    }
                    AppSkeletonPreview(
                        name = name,
                        colorScheme = getThemeColorScheme(id, isPreviewDark),
                        isSelected = uiState.selectedTheme == id,
                        onClick = { onThemeSelected(id) }
                    )
                }
            }

            // --- Section: Interface ---
            Text(
                text = stringResource(R.string.interface_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Theme Setting
            var showThemeMenu by remember { mutableStateOf(false) }
            val selectedThemeLabel = when (uiState.themeMode) {
                "light" -> stringResource(R.string.theme_light)
                "dark" -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
            }

            SettingItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.theme_setting_title),
                trailingContent = {
                    Box {
                        OutlinedButton(
                            onClick = { showThemeMenu = true },
                            modifier = Modifier.widthIn(max = 140.dp)
                        ) {
                            Text(
                                text = selectedThemeLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            listOf("system", "light", "dark").forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                "light" -> stringResource(R.string.theme_light)
                                                "dark" -> stringResource(R.string.theme_dark)
                                                else -> stringResource(R.string.theme_system)
                                            }
                                        )
                                    },
                                    onClick = {
                                        onThemeChanged(mode)
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            // Language Setting
            var showLangMenu by remember { mutableStateOf(false) }
            val selectedLangLabel = if (uiState.appLanguage == "es") "Español" else "English"

            SettingItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.language_setting_title),
                trailingContent = {
                    Box {
                        OutlinedButton(
                            onClick = { showLangMenu = true },
                            modifier = Modifier.widthIn(max = 140.dp)
                        ) {
                            Text(text = selectedLangLabel)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showLangMenu,
                            onDismissRequest = { showLangMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("English") }, onClick = { onLanguageChanged("en"); showLangMenu = false })
                            DropdownMenuItem(text = { Text("Español") }, onClick = { onLanguageChanged("es"); showLangMenu = false })
                        }
                    }
                }
            )

            // Tab Layout
            SettingItem(
                icon = Icons.Default.ViewCompact,
                title = stringResource(R.string.tab_layout_setting_title),
                subtitle = if (uiState.scrollableTabs) stringResource(R.string.tab_layout_weekdays) else stringResource(R.string.tab_layout_letters),
                trailingContent = {
                    Switch(checked = uiState.scrollableTabs, onCheckedChange = onToggleScrollableTabs)
                }
            )

            // High Contrast
            SettingItem(
                icon = Icons.Default.InvertColors,
                title = stringResource(R.string.high_contrast_setting_title),
                subtitle = stringResource(R.string.high_contrast_setting_desc),
                trailingContent = {
                    Switch(checked = uiState.highContrast, onCheckedChange = onToggleHighContrast)
                }
            )

            // "Now" Line
            SettingItem(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.now_line_setting_title),
                trailingContent = {
                    Switch(checked = uiState.showNowLine, onCheckedChange = onToggleShowNowLine)
                },
                expandedContent = {
                    if (uiState.showNowLine) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.now_line_style_title),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 36.dp)
                            )
                            var showStyleMenu by remember { mutableStateOf(false) }
                            val styleLabel = if (uiState.nowLineStyle == "obvious") stringResource(R.string.now_line_style_obvious) else stringResource(R.string.now_line_style_discrete)
                            Box {
                                OutlinedButton(
                                    onClick = { showStyleMenu = true },
                                    modifier = Modifier.widthIn(max = 160.dp)
                                ) {
                                    Text(
                                        text = styleLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = showStyleMenu, onDismissRequest = { showStyleMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.now_line_style_discrete)) }, onClick = { onNowLineStyleChanged("discrete"); showStyleMenu = false })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.now_line_style_obvious)) }, onClick = { onNowLineStyleChanged("obvious"); showStyleMenu = false })
                                }
                            }
                        }
                    }
                }
            )

            // --- Section: Data & Storage ---
            Text(
                text = stringResource(R.string.data_storage_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            SettingItem(
                icon = Icons.Default.SaveAlt,
                title = stringResource(R.string.export_backup_setting_title),
                subtitle = stringResource(R.string.export_backup_desc),
                onClick = onExportData
            )

            SettingItem(
                icon = Icons.Default.Cloud,
                title = "Online Repository URL",
                subtitle = uiState.githubRepoBaseUrl,
                onClick = {
                    tempRepoUrl = uiState.githubRepoBaseUrl
                    showRepoUrlDialog = true
                }
            )
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
            onToggleShowNowLine = {},
            onNowLineStyleChanged = {},
            onToggleHighContrast = {},
            onThemeSelected = {},
             onGithubRepoUrlChanged = {},
            onExportData = {},
            onImportClick = {}
        )
    }
}
