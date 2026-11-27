package com.marcoslorcar.clementime.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.marcoslorcar.clementime.BuildConfig
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.SyncMode
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.screens.scheduleimport.ScheduleDiffBottomSheet
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun MoreScreen(
    onNavigateToImport: () -> Unit,
    showDiffOnLaunch: Boolean = false,
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
    val context = LocalContext.current

    LaunchedEffect(showDiffOnLaunch) {
        if (showDiffOnLaunch) {
            viewModel.checkScheduleUpdatesNow()
        }
    }

    val updatedSuccessMsg = stringResource(R.string.schedule_updated_success)
    val noUpdatesMsg = stringResource(R.string.no_schedule_updates_found)
    val noFacultyMsg = stringResource(R.string.settings_check_updates_no_faculty)

    if (uiState.showDiffBottomSheet && uiState.pendingDiffs.isNotEmpty()) {
        ScheduleDiffBottomSheet(
            diffs = uiState.pendingDiffs,
            onApply = { selectedSubjectKeys ->
                viewModel.applyPendingSlotDiffs(selectedSubjectKeys) {
                    Toast.makeText(context, updatedSuccessMsg, Toast.LENGTH_SHORT).show()
                }
            },
            onIgnore = { viewModel.ignorePendingSlotDiffs() },
            onDismissRequest = { viewModel.dismissDiffBottomSheet() }
        )
    }

    MoreContent(
        uiState = uiState,
        actions = MoreActions(
            onThemeChanged = viewModel::setThemeMode,
            onLanguageChanged = viewModel::setAppLanguage,
            onToggleScrollableTabs = viewModel::setScrollableTabs,
            onToggleShowNowLine = viewModel::setShowNowLine,
            onNowLineStyleChanged = viewModel::setNowLineStyle,
            onToggleHighContrast = viewModel::setHighContrast,
            onThemeSelected = viewModel::setSelectedTheme,
            onDayStartTimeChanged = viewModel::setDayStartTime,
            onDayEndTimeChanged = viewModel::setDayEndTime,
            onGithubRepoUrlChanged = viewModel::setGithubRepoBaseUrl,
            onToggleOnboardingTooltips = viewModel::setOnboardingTooltipsEnabled,
            onExportData = viewModel::exportData,
            onPeriodConfigChanged = viewModel::setPeriodConfig,
            onExportIcsBoundaries = viewModel::exportFullYearToIcs,
            onSyncModeChanged = viewModel::setSyncMode,
            onCheckUpdatesNow = {
                viewModel.checkScheduleUpdatesNow(
                    onNoUpdatesFound = {
                        Toast.makeText(context, noUpdatesMsg, Toast.LENGTH_SHORT).show()
                    },
                    onNoFacultyLinked = {
                        Toast.makeText(context, noFacultyMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onDebugShowFcmStatus = viewModel::debugShowFcmStatus,
            onDebugResetHashes = viewModel::debugResetScheduleHashes,
            onDebugSimulateDiff = viewModel::debugSimulateScheduleDiff,
            onDebugResetOnboarding = viewModel::debugResetOnboardingAndTooltips,
            onImportClick = onNavigateToImport
        ),
        onMenuClick = onMenuClick
    )
}

internal data class MoreActions(
    val onThemeChanged: (String) -> Unit,
    val onLanguageChanged: (String) -> Unit,
    val onToggleScrollableTabs: (Boolean) -> Unit,
    val onToggleShowNowLine: (Boolean) -> Unit,
    val onNowLineStyleChanged: (String) -> Unit,
    val onToggleHighContrast: (Boolean) -> Unit,
    val onThemeSelected: (String) -> Unit,
    val onDayStartTimeChanged: (LocalTime) -> Unit,
    val onDayEndTimeChanged: (LocalTime) -> Unit,
    val onGithubRepoUrlChanged: (String) -> Unit,
    val onToggleOnboardingTooltips: (Boolean) -> Unit,
    val onExportData: (Context, Uri, (ExportStatus) -> Unit) -> Unit,
    val onPeriodConfigChanged: (PeriodConfig) -> Unit,
    val onExportIcsBoundaries: (Context, Uri, List<LocalDate>, (ExportStatus) -> Unit) -> Unit,
    val onSyncModeChanged: (SyncMode) -> Unit,
    val onCheckUpdatesNow: () -> Unit,
    val onDebugShowFcmStatus: ((String) -> Unit) -> Unit,
    val onDebugResetHashes: () -> Unit,
    val onDebugSimulateDiff: () -> Unit,
    val onDebugResetOnboarding: () -> Unit,
    val onImportClick: () -> Unit
)

@Composable
internal fun MoreContent(
    uiState: MoreUiState,
    actions: MoreActions,
    onMenuClick: (() -> Unit)? = null
) {
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
            MoreFeaturedSection(actions.onImportClick)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            MoreThemeSection(uiState, actions)
            MoreSectionTitle(R.string.settings_screen_title)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoreDisplaySettings(uiState, actions)
                MoreExportSettings(uiState.periodConfig, actions)
                MoreSyncSettings(uiState, actions)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MoreHelpSection(uiState.onboardingTooltipsEnabled, actions.onToggleOnboardingTooltips)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MoreAboutSection()

            if (BuildConfig.DEBUG) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MoreDebugSection(actions)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MoreScreenPreview() {
    ClemenTimeTheme {
        MoreContent(
            uiState = MoreUiState(),
            actions = MoreActions(
                onThemeChanged = {},
                onLanguageChanged = {},
                onToggleScrollableTabs = {},
                onToggleShowNowLine = {},
                onNowLineStyleChanged = {},
                onToggleHighContrast = {},
                onThemeSelected = {},
                onDayStartTimeChanged = {},
                onDayEndTimeChanged = {},
                onGithubRepoUrlChanged = {},
                onToggleOnboardingTooltips = {},
                onExportData = { _, _, _ -> },
                onPeriodConfigChanged = {},
                onExportIcsBoundaries = { _, _, _, _ -> },
                onSyncModeChanged = {},
                onCheckUpdatesNow = {},
                onDebugShowFcmStatus = {},
                onDebugResetHashes = {},
                onDebugSimulateDiff = {},
                onDebugResetOnboarding = {},
                onImportClick = {}
            )
        )
    }
}
