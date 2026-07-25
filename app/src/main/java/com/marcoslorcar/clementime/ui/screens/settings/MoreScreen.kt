package com.marcoslorcar.clementime.ui.screens.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.marcoslorcar.clementime.BuildConfig
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.ui.components.AppSkeletonPreview
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.marcoslorcar.clementime.ui.theme.getThemeColorScheme
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetReceiver
import com.marcoslorcar.clementime.utils.fadingEdges

@Composable
fun MoreScreen(
    onNavigateToImport: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(
        checkNotNull(
            LocalViewModelStoreOwner.current
        ) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        }, null
    ),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MoreContent(
        uiState = uiState,
        onThemeChanged = viewModel::setThemeMode,
        onLanguageChanged = viewModel::setAppLanguage,
        onToggleScrollableTabs = viewModel::setScrollableTabs,
        onToggleShowNowLine = viewModel::setShowNowLine,
        onNowLineStyleChanged = viewModel::setNowLineStyle,
        onToggleHighContrast = viewModel::setHighContrast,
        onThemeSelected = viewModel::setSelectedTheme,
        onGithubRepoUrlChanged = viewModel::setGithubRepoBaseUrl,
        onToggleOnboardingTooltips = viewModel::setOnboardingTooltipsEnabled,
        onExportData = viewModel::exportData,
        onImportClick = onNavigateToImport,
        onMenuClick = onMenuClick
    )
}

@Composable
fun MoreContent(
    uiState: MoreUiState,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onToggleScrollableTabs: (Boolean) -> Unit,
    onToggleShowNowLine: (Boolean) -> Unit,
    onNowLineStyleChanged: (String) -> Unit,
    onToggleHighContrast: (Boolean) -> Unit,
    onThemeSelected: (String) -> Unit,
    onGithubRepoUrlChanged: (String) -> Unit,
    onToggleOnboardingTooltips: (Boolean) -> Unit,
    onExportData: (android.content.Context, Uri, (ExportStatus) -> Unit) -> Unit,
    onImportClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showRepoUrlDialog by remember { mutableStateOf(false) }
    var tempRepoUrl by remember { mutableStateOf(uiState.githubRepoBaseUrl) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportData(context, uri) { status ->
                when (status) {
                    is ExportStatus.Success -> Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                    is ExportStatus.Error -> Toast.makeText(context, status.error, Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
        }
    }

    if (showRepoUrlDialog) {
        AlertDialog(
            onDismissRequest = { showRepoUrlDialog = false },
            title = { Text(stringResource(R.string.github_repository_url_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.github_repository_url_description), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = tempRepoUrl,
                        onValueChange = { tempRepoUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.online_repository_url_setting_title)) }
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
                    Text(stringResource(R.string.save_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepoUrlDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(id = R.string.more_screen_title),
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
            // --- Section: Featured ---
            Text(
                text = stringResource(R.string.featured_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingItem(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    title = stringResource(R.string.import_library_title),
                    subtitle = stringResource(R.string.import_library_desc),
                    onClick = onImportClick
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        SettingItem(
                            icon = Icons.Default.Widgets,
                            title = stringResource(R.string.setup_widget_title),
                            subtitle = stringResource(R.string.setup_widget_desc),
                            onClick = {
                                val myProvider = ComponentName(context, ScheduleWidgetReceiver::class.java)
                                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- Section: Appearance ---
            Text(
                text = stringResource(R.string.theme_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
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

            // --- Section: Settings ---
            Text(
                text = stringResource(id = R.string.settings_screen_title), // Using old string for section title
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Data & Storage
                Text(
                    text = stringResource(R.string.data_storage_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )

                SettingItem(
                    icon = Icons.Default.SaveAlt,
                    title = stringResource(R.string.export_backup_setting_title),
                    subtitle = stringResource(R.string.export_backup_desc),
                    onClick = { exportLauncher.launch("ClemenTime_Backup_${System.currentTimeMillis()}.json") }
                )

                SettingItem(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.online_repository_url_setting_title),
                    subtitle = uiState.githubRepoBaseUrl,
                    onClick = {
                        tempRepoUrl = uiState.githubRepoBaseUrl
                        showRepoUrlDialog = true
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- Section: Help & Guidance ---
            Text(
                text = stringResource(R.string.help_guidance_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            SettingItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.onboarding_tooltips_setting_title),
                subtitle = stringResource(R.string.onboarding_tooltips_setting_desc),
                trailingContent = {
                    Switch(checked = uiState.onboardingTooltipsEnabled, onCheckedChange = onToggleOnboardingTooltips)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- Section: About ---
            Text(
                text = stringResource(R.string.about_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            val githubRepoUrl = stringResource(R.string.github_repo_url)
            SettingItem(
                icon = Icons.Default.Tag,
                title = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                subtitle = stringResource(R.string.about_app_description),
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, githubRepoUrl.toUri())
                    context.startActivity(intent)
                }
            )

            val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
            SettingItem(
                icon = Icons.Default.Policy,
                title = stringResource(R.string.privacy_policy_label),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    expandedContent: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
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
}

@Preview(showBackground = true)
@Composable
fun MoreScreenPreview() {
    ClemenTimeTheme {
        MoreContent(
            uiState = MoreUiState(),
            onThemeChanged = {},
            onLanguageChanged = {},
            onToggleScrollableTabs = {},
            onToggleShowNowLine = {},
            onNowLineStyleChanged = {},
            onToggleHighContrast = {},
            onThemeSelected = {},
            onGithubRepoUrlChanged = {},
            onToggleOnboardingTooltips = {},
            onExportData = { _, _, _ -> },
            onImportClick = {}
        )
    }
}
