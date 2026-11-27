package com.marcoslorcar.clementime.ui.screens.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.marcoslorcar.clementime.BuildConfig
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.SyncMode
import com.marcoslorcar.clementime.ui.components.AppSkeletonPreview
import com.marcoslorcar.clementime.ui.components.periodConfigSummaryLabel
import com.marcoslorcar.clementime.ui.screens.subject.RadialTimePickerDialog
import com.marcoslorcar.clementime.ui.theme.getThemeColorScheme
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetReceiver
import com.marcoslorcar.clementime.utils.fadingEdges
import java.time.format.DateTimeFormatter

@Composable
internal fun MoreSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun MoreFeaturedSection(onImportClick: () -> Unit) {
    val context = LocalContext.current
    MoreSectionTitle(R.string.featured_section_title)

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
}

@Composable
internal fun MoreThemeSection(uiState: MoreUiState, actions: MoreActions) {
    val context = LocalContext.current
    MoreSectionTitle(R.string.theme_preview_title)

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
            val previewColorScheme = if (id == "clementine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isPreviewDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                getThemeColorScheme(id, isPreviewDark)
            }

            AppSkeletonPreview(
                name = name,
                colorScheme = previewColorScheme,
                isSelected = uiState.selectedTheme == id,
                onClick = { actions.onThemeSelected(id) }
            )
        }
    }
}

@Composable
internal fun MoreDisplaySettings(uiState: MoreUiState, actions: MoreActions) {
    var showPeriodsDialog by remember { mutableStateOf(false) }
    if (showPeriodsDialog) {
        AcademicPeriodsDialog(
            config = uiState.periodConfig,
            subjectCounts = uiState.subjectCountsBySemester,
            onConfirm = {
                actions.onPeriodConfigChanged(it)
                showPeriodsDialog = false
            },
            onDismiss = { showPeriodsDialog = false }
        )
    }

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
                                actions.onThemeChanged(mode)
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
    val selectedLangLabel = if (uiState.appLanguage == "es") stringResource(R.string.lang_es) else stringResource(R.string.lang_en)

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
                    DropdownMenuItem(text = { Text(stringResource(R.string.lang_en)) }, onClick = { actions.onLanguageChanged("en"); showLangMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.lang_es)) }, onClick = { actions.onLanguageChanged("es"); showLangMenu = false })
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
            Switch(checked = uiState.scrollableTabs, onCheckedChange = actions.onToggleScrollableTabs)
        }
    )

    // High Contrast
    SettingItem(
        icon = Icons.Default.InvertColors,
        title = stringResource(R.string.high_contrast_setting_title),
        subtitle = stringResource(R.string.high_contrast_setting_desc),
        trailingContent = {
            Switch(checked = uiState.highContrast, onCheckedChange = actions.onToggleHighContrast)
        }
    )

    // Academic Periods
    SettingItem(
        icon = Icons.Default.DateRange,
        title = stringResource(R.string.academic_periods_setting_title),
        subtitle = periodConfigSummaryLabel(uiState.periodConfig),
        onClick = { showPeriodsDialog = true }
    )

    // Schedule Range
    Text(
        text = stringResource(R.string.schedule_range_header),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    SettingItem(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.day_start_time_label),
        subtitle = uiState.dayStartTime.format(timeFormatter),
        onClick = { showStartTimePicker = true }
    )

    SettingItem(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.day_end_time_label),
        subtitle = uiState.dayEndTime.format(timeFormatter),
        onClick = { showEndTimePicker = true }
    )

    if (showStartTimePicker) {
        RadialTimePickerDialog(
            initialTime = uiState.dayStartTime,
            onDismiss = { showStartTimePicker = false },
            onTimeConfirm = {
                actions.onDayStartTimeChanged(it)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        RadialTimePickerDialog(
            initialTime = uiState.dayEndTime,
            onDismiss = { showEndTimePicker = false },
            onTimeConfirm = {
                actions.onDayEndTimeChanged(it)
                showEndTimePicker = false
            }
        )
    }

    // "Now" Line
    SettingItem(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.now_line_setting_title),
        trailingContent = {
            Switch(checked = uiState.showNowLine, onCheckedChange = actions.onToggleShowNowLine)
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.now_line_style_discrete)) }, onClick = { actions.onNowLineStyleChanged("discrete"); showStyleMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.now_line_style_obvious)) }, onClick = { actions.onNowLineStyleChanged("obvious"); showStyleMenu = false })
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun MoreSyncSettings(uiState: MoreUiState, actions: MoreActions) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    var repoUrlToEdit by remember { mutableStateOf<String?>(null) }
    repoUrlToEdit?.let { currentUrl ->
        RepositoryUrlDialog(
            currentUrl = currentUrl,
            onConfirm = {
                actions.onGithubRepoUrlChanged(it)
                repoUrlToEdit = null
            },
            onDismiss = { repoUrlToEdit = null }
        )
    }

    SettingItem(
        icon = Icons.Default.Cloud,
        title = stringResource(R.string.online_repository_url_setting_title),
        subtitle = uiState.githubRepoBaseUrl,
        onClick = { repoUrlToEdit = uiState.githubRepoBaseUrl }
    )

    // Linked Faculty Status
    SettingItem(
        icon = Icons.Default.School,
        title = stringResource(R.string.settings_linked_faculty_title),
        subtitle = uiState.linkedFacultyLabel.ifBlank { stringResource(R.string.settings_linked_faculty_none) },
        onClick = actions.onImportClick
    )

    // Sync Mode Setting
    val isOnline = uiState.syncMode == SyncMode.ONLINE
    SettingItem(
        icon = Icons.Default.Sync,
        title = stringResource(R.string.sync_mode_settings_title),
        subtitle = stringResource(R.string.sync_mode_settings_desc),
        trailingContent = {
            Switch(
                checked = isOnline,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        requestNotificationPermissionIfNeeded()
                        actions.onSyncModeChanged(SyncMode.ONLINE)
                    } else {
                        actions.onSyncModeChanged(SyncMode.OFFLINE)
                    }
                }
            )
        }
    )

    // Check for Updates Now Button
    SettingItem(
        icon = Icons.Default.Refresh,
        title = stringResource(R.string.check_updates_now_title),
        subtitle = if (uiState.isCheckingUpdates) stringResource(R.string.checking_updates) else null,
        onClick = {
            if (!uiState.isCheckingUpdates) {
                requestNotificationPermissionIfNeeded()
                actions.onCheckUpdatesNow()
            }
        }
    )
}

@Composable
internal fun MoreHelpSection(tooltipsEnabled: Boolean, onTooltipsChanged: (Boolean) -> Unit) {
    MoreSectionTitle(R.string.help_guidance_header)

    SettingItem(
        icon = Icons.Default.Info,
        title = stringResource(R.string.onboarding_tooltips_setting_title),
        subtitle = stringResource(R.string.onboarding_tooltips_setting_desc),
        trailingContent = {
            Switch(checked = tooltipsEnabled, onCheckedChange = onTooltipsChanged)
        }
    )
}

@Composable
internal fun MoreAboutSection() {
    val context = LocalContext.current
    MoreSectionTitle(R.string.about_header)

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

@Composable
internal fun SettingItem(
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
                        fontWeight = FontWeight.SemiBold
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
