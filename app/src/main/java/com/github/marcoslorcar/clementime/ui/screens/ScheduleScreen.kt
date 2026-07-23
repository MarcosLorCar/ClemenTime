package com.github.marcoslorcar.clementime.ui.screens


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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.github.marcoslorcar.clementime.R
import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.ui.components.ClemenTimeTopBar
import com.github.marcoslorcar.clementime.ui.components.ScheduleTimeline
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.github.marcoslorcar.clementime.utils.getNarrowLabel
import com.github.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    onClickSubject: (Long, Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScheduleContent(
        uiState = uiState,
        onChangeTab = viewModel::changeTab,
        onMenuClick = onMenuClick,
        onClickSubject = onClickSubject,
        onNavigateToImport = onNavigateToImport,
        onNavigateToConflictResolver = onNavigateToConflictResolver
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    uiState: ScheduleUiState,
    onChangeTab: (ScheduleTab) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToConflictResolver: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onClickSubject: (Long, Long) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    val tabs = ScheduleTab.entries
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.ordinal,
        pageCount = { tabs.size }
    )

    // Synchronize Pager swipe with ViewModel state
    LaunchedEffect(pagerState.currentPage) {
        val newTab = tabs[pagerState.currentPage]
        if (newTab != uiState.selectedTab) {
            onChangeTab(newTab)
        }
    }

    // Keep pager in sync if selectedTab changes externally
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab.ordinal) {
            pagerState.animateScrollToPage(uiState.selectedTab.ordinal)
        }
    }

    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    onMenuClick = onMenuClick,
                    title = stringResource(R.string.schedule_screen_title),
                    actions = {
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

                if (clusters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No classes scheduled for ${currentDay.name.lowercase().replaceFirstChar { it.uppercase() }}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ScheduleTimeline(
                        clusters = clusters,
                        onClickSubject = onClickSubject,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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