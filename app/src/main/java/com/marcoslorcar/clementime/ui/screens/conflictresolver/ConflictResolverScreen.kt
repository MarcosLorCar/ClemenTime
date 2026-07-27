@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.conflictresolver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.utils.ScheduleSolution
import kotlinx.coroutines.launch

@Composable
fun ConflictResolverScreen(
    onBack: () -> Unit,
    viewModel: ConflictResolverViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var showSolutionsSheet by remember { mutableStateOf(false) }
    var solutionToConfirm by remember { mutableStateOf<ScheduleSolution?>(null) }
    var showIgnoreHelpDialog by remember { mutableStateOf(false) }
    var showLabHelpDialog by remember { mutableStateOf(false) }
    var showGeneralHelpDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val appliedMessage = stringResource(R.string.conflict_resolver_applied_snackbar)
    val undoAction = stringResource(R.string.action_undo)

    if (showGeneralHelpDialog) {
        AlertDialog(
            onDismissRequest = { showGeneralHelpDialog = false },
            title = { Text(stringResource(R.string.conflict_resolver_help_title)) },
            text = { Text(stringResource(R.string.conflict_resolver_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showGeneralHelpDialog = false }) {
                    Text(stringResource(R.string.onboarding_got_it))
                }
            }
        )
    }

    if (showIgnoreHelpDialog) {
        AlertDialog(
            onDismissRequest = { showIgnoreHelpDialog = false },
            title = { Text(stringResource(R.string.ignore_help_title)) },
            text = { Text(stringResource(R.string.ignore_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showIgnoreHelpDialog = false }) {
                    Text(stringResource(R.string.onboarding_got_it))
                }
            }
        )
    }

    if (showLabHelpDialog) {
        AlertDialog(
            onDismissRequest = { showLabHelpDialog = false },
            title = { Text(stringResource(R.string.conflict_resolver_help_title)) },
            text = { Text(stringResource(R.string.conflict_resolver_lab_help_desc)) },
            confirmButton = {
                TextButton(onClick = { showLabHelpDialog = false }) {
                    Text(stringResource(R.string.onboarding_got_it))
                }
            }
        )
    }

    if (solutionToConfirm != null) {
        ModalBottomSheet(
            onDismissRequest = { solutionToConfirm = null },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ConfirmationSheetContent(
                solution = solutionToConfirm!!,
                subjects = uiState.subjects,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.applySolution(solutionToConfirm!!)
                    solutionToConfirm = null
                    showSolutionsSheet = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = appliedMessage,
                            actionLabel = undoAction
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoLastApply()
                        }
                    }
                },
                onCancel = { solutionToConfirm = null }
            )
        }
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(R.string.conflict_resolver_title),
                onNavigateBack = onBack,
                actions = {
                    if (uiState.canUndo) {
                        IconButton(onClick = { viewModel.undoLastApply() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.content_description_undo))
                        }
                    }
                    IconButton(onClick = { showGeneralHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.content_description_help)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isLoading && uiState.solutions.isNotEmpty()) {
                val perfectPossible = remember(uiState.solutions) {
                    uiState.solutions.any { it.overlappingSlotIds.isEmpty() }
                }
                val currentHasOverlaps = remember(uiState.solutions) {
                    uiState.solutions.any { it.isCurrent && it.overlappingSlotIds.isNotEmpty() }
                }

                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp
                ) {
                    Box(modifier = Modifier.navigationBarsPadding()) {
                        Button(
                            onClick = { showSolutionsSheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (perfectPossible) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (perfectPossible) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (perfectPossible) Icons.Default.AutoAwesome else Icons.Default.Warning,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (currentHasOverlaps) {
                                    if (perfectPossible) stringResource(R.string.conflict_resolver_generate_preview)
                                    else stringResource(R.string.conflict_resolver_generate_preview_warning)
                                } else {
                                    stringResource(R.string.conflict_resolver_continue)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                LoadingIndicator(modifier = Modifier.size(100.dp))
            } else {
                UnifiedWorkspace(
                    subjects = uiState.subjects,
                    preferenceMode = uiState.preferenceMode,
                    onSelectPreferenceMode = viewModel::setPreferenceMode,
                    onToggleIgnored = viewModel::toggleSlotIgnored,
                    onSelectLabGroup = viewModel::selectLabGroup,
                    onShowIgnoreHelp = { showIgnoreHelpDialog = true },
                    onShowLabHelp = { showLabHelpDialog = true },
                    onboardingTooltipsEnabled = uiState.onboardingTooltipsEnabled,
                    hasSeenPrioritiesTooltip = uiState.hasSeenPrioritiesTooltip,
                    onMarkPrioritiesTooltipSeen = viewModel::markPrioritiesTooltipSeen
                )
            }
        }
    }

    if (showSolutionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSolutionsSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SolutionsSheetContent(
                solutions = uiState.solutions,
                subjects = uiState.subjects,
                onSelectSolution = { solutionToConfirm = it },
                onboardingTooltipsEnabled = uiState.onboardingTooltipsEnabled,
                hasSeenApplyTooltip = uiState.hasSeenApplyTooltip,
                onMarkApplyTooltipSeen = viewModel::markApplyTooltipSeen
            )
        }
    }
}
