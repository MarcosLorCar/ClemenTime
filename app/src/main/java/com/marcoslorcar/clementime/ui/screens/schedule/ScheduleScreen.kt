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

    // The day requested by the route is applied inside ScheduleContent, where it can move the
    // pager and the ViewModel together. Doing it here only set the ViewModel, and the pager sync
    // then raced with the settledPage collector.

    // The route argument is immutable, and navigateToTab reuses the existing entry
    // (launchSingleTop), so on a second "view in schedule" the ViewModel's savedStateHandle is
    // already null from the first consume and only this local copy carries the new id. It has to
    // be clearable, or the highlight effect can never settle. Keyed on the route value so a fresh
    // navigation re-seeds it.
    var pendingHighlightSlotId by remember(targetHighlightSlotId) { mutableStateOf(targetHighlightSlotId) }

    ScheduleContent(
        uiState = uiState,
        targetDayOfWeek = targetDayOfWeek,
        overrideHighlightSlotId = pendingHighlightSlotId,
        onChangeTab = viewModel::changeTab,
        onSemesterSelected = viewModel::changeSemester,
        onToggleSemesterSwitcher = viewModel::toggleSemesterSwitcher,
        onNavigateToImport = onNavigateToImport,
        onNavigateToConflictResolver = onNavigateToConflictResolver,
        onMenuClick = onMenuClick,
        onClickSubject = onClickSubject,
        onSaveSlot = viewModel::saveSlot,
        onDeleteSlot = viewModel::deleteSlot,
        onHighlightConsumed = {
            pendingHighlightSlotId = null
            viewModel.onHighlightConsumed()
        },
        onMarkOptimizerTooltipSeen = viewModel::markOptimizerTooltipSeen,
        onMarkAutoSemesterChangeTooltipSeen = viewModel::markAutoSemesterChangeTooltipSeen
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    uiState: ScheduleUiState,
    targetDayOfWeek: String? = null,
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

    // Highlight state lives here, above the pager. It used to be declared inside the page lambda,
    // which meant every composed page ran its own copy of this effect and called
    // onHighlightConsumed() independently.
    val slotToHighlight = overrideHighlightSlotId ?: uiState.highlightSlotId
    var activeHighlightSlotId by remember { mutableStateOf<Long?>(null) }

    // Latch the request, then consume it. Consuming nulls slotToHighlight, which cancels this
    // effect - so it must not be the one holding the timeout, or the highlight would never clear.
    LaunchedEffect(slotToHighlight) {
        val requested = slotToHighlight ?: return@LaunchedEffect
        activeHighlightSlotId = requested
        onHighlightConsumed()
    }

    // The fade-out is keyed on the latched value instead, so it survives consumption and restarts
    // cleanly if a different slot is highlighted mid-timeout.
    LaunchedEffect(activeHighlightSlotId) {
        if (activeHighlightSlotId != null) {
            delay(2000L.milliseconds)
            activeHighlightSlotId = null
        }
    }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((1000 * 30).milliseconds)
            currentTime = LocalTime.now()
        }
    }

    // A day requested by navigation ("view in schedule") drives the pager directly and updates the
    // ViewModel in the same breath. Going VM -> uiState -> pager lost the race against the
    // settledPage collector below, which reported the pager's not-yet-updated page back and
    // cancelled the jump. Keyed on the slot id too, so navigating again re-applies the day even
    // when the day itself is unchanged.
    // Applied once per (day, slot) request. Without the latch this effect re-fired when
    // overrideHighlightSlotId went null on consume, jumping the pager back to the route's day a
    // second time - and again on every later consume, which is the pager yanking itself around.
    var lastAppliedDayRequest by remember { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(targetDayOfWeek, overrideHighlightSlotId) {
        val day = targetDayOfWeek ?: return@LaunchedEffect
        // Only act while a request is actually live; null means it has been consumed already.
        val slotId = overrideHighlightSlotId ?: return@LaunchedEffect
        val request = day to slotId
        if (lastAppliedDayRequest == request) return@LaunchedEffect

        val targetTab = tabs.find { it.dayOfWeek.name.equals(day, ignoreCase = true) }
            ?: return@LaunchedEffect

        lastAppliedDayRequest = request
        // Move the pager first, then the ViewModel. Ordered this way the sequence is
        // self-correcting: a settledPage emission arriving before the jump reports the old page
        // and is then overwritten by onChangeTab, and one arriving after already reports the
        // target. An earlier attempt used a suppression flag instead, which raced with the
        // collector's snapshot reads.
        pagerState.scrollToPage(targetTab.ordinal)
        onChangeTab(targetTab)
    }

    // Synchronize Pager state with ViewModel only when it settles and we're not programmatically scrolling
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (!pagerState.isScrollInProgress) {
                onChangeTab(tabs[page])
            }
        }
    }

    // Synchronize Pager page when the ViewModel changes selectedTab by any other route. Keyed on
    // isLoading as well: the guard below depends on it, so without it the effect would never
    // re-run once loading finished on an unchanged tab.
    LaunchedEffect(uiState.selectedTab, uiState.isLoading) {
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
                            if (uiState.hasAnySubjects) {
                                IconButton(onClick = onToggleSemesterSwitcher) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = stringResource(R.string.content_description_toggle_semester_switcher),
                                        tint = if (uiState.isSemesterSwitcherVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
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
                        onSemesterSelected = onSemesterSelected,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
            val isWithinTimelineRange = currentTime in uiState.dayStartTime..uiState.dayEndTime
            
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
                onAddManuallyClick = { onClickSubject(0L, null) },
                modifier = Modifier.padding(paddingValues)
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

                // Only the page that actually holds the slot gets the highlight. The pager keeps
                // neighbouring pages composed, so handing every page the same id made each one
                // race its own animateScrollTo.
                val pageHighlightSlotId = activeHighlightSlotId
                    ?.takeIf { id -> daySlots.any { (_, slot) -> slot.id == id } }

                ScheduleTimeline(
                    modifier = Modifier.fillMaxSize(),
                    clusters = clusters,
                    startTime = uiState.dayStartTime,
                    endTime = uiState.dayEndTime,
                    showNowLine = uiState.showNowLine,
                    nowLineStyle = uiState.nowLineStyle,
                    dayOfWeek = currentDay.dayOfWeek,
                    scrollToNowTrigger = scrollToNowTrigger,
                    highContrastEnabled = uiState.highContrast,
                    highlightSlotId = pageHighlightSlotId,
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
