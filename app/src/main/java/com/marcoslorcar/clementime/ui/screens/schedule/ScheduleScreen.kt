package com.marcoslorcar.clementime.ui.screens.schedule


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.ui.components.ClassSlotItemCard
import com.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.marcoslorcar.clementime.ui.components.OnboardingTooltip
import com.marcoslorcar.clementime.ui.components.ScheduleTimeline
import com.marcoslorcar.clementime.ui.screens.subject.ClassSlotUiModel
import com.marcoslorcar.clementime.ui.screens.subject.toUiModel
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
    onClickSubject: (Long, Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        onNavigateToImport = onNavigateToImport,
        onNavigateToConflictResolver = onNavigateToConflictResolver,
        onMenuClick = onMenuClick,
        onClickSubject = onClickSubject,
        onDeleteSlot = viewModel::deleteSlot,
        onMarkOptimizerTooltipSeen = viewModel::markOptimizerTooltipSeen
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    uiState: ScheduleUiState,
    overrideHighlightSlotId: Long? = null,
    onChangeTab: (ScheduleTab) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onClickSubject: (Long, Long) -> Unit = { _, _ -> },
    onDeleteSlot: (Long) -> Unit = { _ -> },
    onMarkOptimizerTooltipSeen: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val tabs = ScheduleTab.entries
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.ordinal,
        pageCount = { tabs.size }
    )

    var selectedSlotForSheet by remember { mutableStateOf<Pair<Subject, ClassSlotUiModel>?>(null) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

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

            if (shouldShowFab) {
                FloatingActionButton(
                    onClick = {
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
                CircularProgressIndicator()
            }
        } else if (uiState.subjectsWithSlots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_schedule_data),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_schedule_data_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateToImport) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_schedule_title))
                    }
                }
            }
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
                    onClickSubject = onClickSubject
                ) { subjectId, slotId ->
                    val subjectWithSlots =
                        uiState.subjectsWithSlots.find { it.subject.id == subjectId }
                    val slot = subjectWithSlots?.slots?.find { it.id == slotId }
                    if (subjectWithSlots != null && slot != null) {
                        selectedSlotForSheet = subjectWithSlots.subject to slot.toUiModel()
                    }
                }
            }
        }
    }

    if (selectedSlotForSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSlotForSheet = null },
            sheetState = sheetState,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            val (_, slot) = selectedSlotForSheet!!
            Box(modifier = Modifier.padding(16.dp)) {
                ClassSlotItemCard(
                    slot = slot,
                    onGoToSchedule = { _, _ -> 
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedSlotForSheet = null
                        }
                    },
                    onDelete = { 
                        onDeleteSlot(slot.id)
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            selectedSlotForSheet = null
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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