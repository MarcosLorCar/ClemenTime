@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.EmptyStateContent
import com.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.marcoslorcar.clementime.ui.components.ScheduleTimeline
import com.marcoslorcar.clementime.ui.components.SemesterSwitcher
import com.marcoslorcar.clementime.ui.model.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.model.toUiModel
import com.marcoslorcar.clementime.ui.screens.subject.SlotEditBottomSheet
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.marcoslorcar.clementime.utils.DAY_END_TIME
import com.marcoslorcar.clementime.utils.DAY_START_TIME
import com.marcoslorcar.clementime.utils.getNarrowLabel
import com.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ScheduleScreen(
    targetDayOfWeek: String? = null,
    targetHighlightSlotId: Long? = null,
    onClickSubject: (Long, Long?) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closeSemesterSwitcher()
        }
    }

    LaunchedEffect(targetDayOfWeek) {
        if (targetDayOfWeek != null) {
            val targetTab = ScheduleTab.entries.find { it.dayOfWeek.name.equals(targetDayOfWeek, ignoreCase = true) }
            if (targetTab != null) {
                viewModel.changeTab(targetTab)
            }
        }
    }

    ScheduleContent(
        uiState = uiState,
        overrideHighlightSlotId = targetHighlightSlotId,
        onChangeTab = viewModel::changeTab,
        onSemesterSelected = viewModel::changeSemester,
        onToggleSemesterSwitcher = viewModel::toggleSemesterSwitcher,
        onNavigateToImport = onNavigateToImport,
        onNavigateToConflictResolver = onNavigateToConflictResolver,
        onMenuClick = onMenuClick,
        onClickSubject = onClickSubject,
        onSaveSlot = viewModel::saveSlot,
        onDeleteSlot = viewModel::deleteSlot,
        onHighlightConsumed = viewModel::onHighlightConsumed,
        onMarkOptimizerTooltipSeen = viewModel::markOptimizerTooltipSeen,
        onMarkAutoSemesterChangeTooltipSeen = viewModel::markAutoSemesterChangeTooltipSeen
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    uiState: ScheduleUiState,
    overrideHighlightSlotId: Long? = null,
    onChangeTab: (ScheduleTab) -> Unit,
    onSemesterSelected: (Int) -> Unit = {},
    onToggleSemesterSwitcher: () -> Unit = {},
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onClickSubject: (Long, Long?) -> Unit = { _, _ -> },
    onSaveSlot: (ClassSlotUiModel) -> Unit = { _ -> },
    onDeleteSlot: (Long) -> Unit = { _ -> },
    onHighlightConsumed: () -> Unit = {},
    onMarkOptimizerTooltipSeen: () -> Unit = {},
    onMarkAutoSemesterChangeTooltipSeen: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val tabs = ScheduleTab.entries
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.ordinal,
        pageCount = { tabs.size }
    )

    var selectedSlotForSheet by remember { mutableStateOf<Pair<Subject, ClassSlotUiModel>?>(null) }

    var scrollToNowTrigger by remember { mutableLongStateOf(0L) }
    var isNearNow by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now().dayOfWeek }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((1000 * 30).milliseconds)
            currentTime = LocalTime.now()
        }
    }

    // Synchronize Pager state with ViewModel only when it settles and we're not programmatically scrolling
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (!pagerState.isScrollInProgress) {
                onChangeTab(tabs[page])
            }
        }
    }

    // Synchronize Pager page when ViewModel changes selectedTab (e.g. via navigation)
    LaunchedEffect(uiState.selectedTab) {
        if (!uiState.isLoading && pagerState.currentPage != uiState.selectedTab.ordinal) {
            pagerState.scrollToPage(uiState.selectedTab.ordinal)
        }
    }

    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    onMenuClick = onMenuClick,
                    title = stringResource(R.string.schedule_screen_title),
                    actions = {
                        OnboardingTooltip(
                            text = stringResource(R.string.tooltip_auto_semester_desc),
                            title = stringResource(R.string.tooltip_auto_semester_title),
                            show = uiState.onboardingTooltipsEnabled && uiState.showAutoChangeTooltip,
                            onDismiss = onMarkAutoSemesterChangeTooltipSeen
                        ) {
                            IconButton(onClick = onToggleSemesterSwitcher) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = stringResource(R.string.content_description_toggle_semester_switcher),
                                    tint = if (uiState.isSemesterSwitcherVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (uiState.subjectsWithSlots.isNotEmpty()) {
                            OnboardingTooltip(
                                text = stringResource(R.string.tooltip_optimizer_desc),
                                title = stringResource(R.string.tooltip_optimizer_title),
                                show = uiState.onboardingTooltipsEnabled && !uiState.hasSeenOptimizerTooltip && uiState.hasOverlaps,
                                onDismiss = onMarkOptimizerTooltipSeen
                            ) {
                                IconButton(onClick = onNavigateToConflictResolver) {
                                    if (uiState.hasOverlaps) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text("!")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoFixHigh,
                                                contentDescription = stringResource(R.string.resolve_conflicts_tooltip)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = stringResource(R.string.resolve_conflicts_tooltip)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = uiState.isSemesterSwitcherVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    SemesterSwitcher(
                        selectedSemester = uiState.selectedSemester,
                        onSemesterSelected = onSemesterSelected
                    )
                }

                if (uiState.subjectsWithSlots.isEmpty()) return@Column

                val locale = LocalConfiguration.current.locales[0]

                if (uiState.scrollableTabs) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                        edgePadding = 16.dp
                    ) {
                        tabs.forEachIndexed { index, day ->
                            val selected = index == pagerState.currentPage
                            val fullWeekdayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                                .replaceFirstChar { it.uppercase(locale) }
                            Tab(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = fullWeekdayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabs.forEachIndexed { index, day ->
                            val selected = index == pagerState.currentPage
                            Tab(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 6.dp)
                                        .size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.letter(locale),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            val isWeekday = today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY
            val isWithinTimelineRange = currentTime in DAY_START_TIME..DAY_END_TIME
            
            val shouldShowFab = uiState.showNowLine && 
                              isWeekday && 
                              isWithinTimelineRange &&
                              uiState.subjectsWithSlots.isNotEmpty() && 
                              (!isNearNow || pagerState.currentPage != (tabs.find { it.dayOfWeek == today }?.ordinal ?: -1))

            AnimatedVisibility(
                visible = shouldShowFab,
                enter = scaleIn(transformOrigin = TransformOrigin(0.5f, 0.5f)),
                exit = scaleOut(transformOrigin = TransformOrigin(0.5f, 0.5f))
            ) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val todayTab = tabs.find { it.dayOfWeek == today }
                        if (todayTab != null) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(todayTab.ordinal)
                                scrollToNowTrigger = System.currentTimeMillis()
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = stringResource(R.string.jump_to_now_label)
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(100.dp))
            }
        } else if (uiState.subjectsWithSlots.isEmpty()) {
            EmptyStateContent(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.no_schedule_data),
                subtitle = stringResource(R.string.no_schedule_data_subtitle),
                onImportClick = onNavigateToImport,
                onAddManuallyClick = { onClickSubject(0L, null) }
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { pageIndex ->
                val currentDay = tabs[pageIndex]

                val daySlots = remember(uiState.subjectsWithSlots, currentDay) {
                    uiState.subjectsWithSlots.flatMap { subjectWithSlots ->
                        subjectWithSlots.slots
                            .filter { it.dayOfWeek == currentDay.dayOfWeek }
                            .map { slot -> subjectWithSlots.subject to slot }
                    }
                }

                val clusters = remember(daySlots) {
                    groupSlotsIntoClusters(daySlots)
                }

    val slotToHighlight = overrideHighlightSlotId ?: uiState.highlightSlotId
    var activeHighlightSlotId by remember(slotToHighlight) { mutableStateOf(slotToHighlight) }

    LaunchedEffect(slotToHighlight) {
        if (slotToHighlight != null) {
            activeHighlightSlotId = slotToHighlight
            onHighlightConsumed()
            delay(2000L.milliseconds)
            activeHighlightSlotId = null
        }
    }

                ScheduleTimeline(
                    modifier = Modifier.fillMaxSize(),
                    clusters = clusters,
                    showNowLine = uiState.showNowLine,
                    nowLineStyle = uiState.nowLineStyle,
                    dayOfWeek = currentDay.dayOfWeek,
                    scrollToNowTrigger = scrollToNowTrigger,
                    highContrastEnabled = uiState.highContrast,
                    highlightSlotId = activeHighlightSlotId,
                    onNearNowChanged = { if (currentDay.dayOfWeek == today) isNearNow = it },
                    onClickSubject = onClickSubject,
                    onLongClickSubject = { subjectId, slotId ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val subjectWithSlots =
                            uiState.subjectsWithSlots.find { it.subject.id == subjectId }
                        val slot = subjectWithSlots?.slots?.find { it.id == slotId }
                        if (subjectWithSlots != null && slot != null) {
                            selectedSlotForSheet = subjectWithSlots.subject to slot.toUiModel()
                        }
                    }
                )
            }
        }
    }

    if (selectedSlotForSheet != null) {
        val (_, slot) = selectedSlotForSheet!!
        SlotEditBottomSheet(
            initialSlot = slot,
            onDismiss = { selectedSlotForSheet = null },
            onSaveSlot = { updatedSlot: ClassSlotUiModel ->
                onSaveSlot(updatedSlot)
                selectedSlotForSheet = null
            },
            onDelete = {
                onDeleteSlot(slot.id)
                selectedSlotForSheet = null
            }
        )
    }
}

enum class ScheduleTab(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    TUESDAY(DayOfWeek.TUESDAY),
    WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY),
    FRIDAY(DayOfWeek.FRIDAY);

    fun letter(locale: Locale): String = this.dayOfWeek.getNarrowLabel(locale).uppercase()
}

@Preview(showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    val previewSubject = Subject(
        id = 1,
        name = "Computer Architecture",
        color = 0xFF6200EE.toInt(),
        code = "ARCH",
        isActive = true
    )

    val previewData = listOf(
        SubjectWithSlots(
            subject = previewSubject,
            slots = listOf(
                ClassSlot(
                    id = 1,
                    subjectId = 1,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(11, 0),
                    classroom = "A1.1",
                    labGroupName = "Teoría",
                ),
                ClassSlot(
                    id = 2,
                    subjectId = 1,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(11, 30),
                    endTime = LocalTime.of(13, 30),
                    classroom = "L1.2",
                    labGroupName = "Lab A1",
                    entryType = EntryType.LAB,
                )
            )
        )
    )

    ClemenTimeTheme {
        ScheduleContent(
            uiState = ScheduleUiState(
                isLoading = false,
                selectedTab = ScheduleTab.MONDAY,
                subjectsWithSlots = previewData
            ),
            onChangeTab = {},
            onMenuClick = {},
            onNavigateToImport = {},
            onNavigateToConflictResolver = {}
        )
    }
}
