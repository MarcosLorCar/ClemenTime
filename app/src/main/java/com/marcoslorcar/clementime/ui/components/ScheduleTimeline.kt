package com.marcoslorcar.clementime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.cardColor
import com.marcoslorcar.clementime.utils.DAY_END_TIME
import com.marcoslorcar.clementime.utils.DAY_START_TIME
import com.marcoslorcar.clementime.utils.TimelineCluster
import com.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val MINUTE_HEIGHT: Dp = 1.4.dp

private val TIME_LABEL_WIDTH: Dp = 56.dp
private val TIMELINE_END_PADDING: Dp = 8.dp
private val TOP_TIMELINE_PADDING: Dp = 8.dp
private val BOTTOM_TIMELINE_PADDING: Dp = 16.dp

private val DIVIDER_THICKNESS: Dp = 1.dp
private val SLOT_GAP: Dp = 3.dp

@Composable
fun ScheduleTimeline(
    modifier: Modifier = Modifier,
    clusters: List<TimelineCluster>,
    startTime: LocalTime = DAY_START_TIME,
    endTime: LocalTime = DAY_END_TIME,
    showNowLine: Boolean = false,
    nowLineStyle: String = "discrete",
    dayOfWeek: DayOfWeek? = null,
    scrollToNowTrigger: Long = 0L,
    highContrastEnabled: Boolean = false,
    highlightSlotId: Long? = null,
    onNearNowChanged: (Boolean) -> Unit = {},
    onClickSubject: (Long, Long) -> Unit,
    onLongClickSubject: (Long, Long) -> Unit = { _, _ -> },
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val totalMinutes = Duration.between(startTime, endTime).toMinutes().toInt()
    val totalHeight = MINUTE_HEIGHT * totalMinutes

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((1000 * 30).milliseconds) // Update every 30 seconds
            currentTime = LocalTime.now()
        }
    }

    val isToday = dayOfWeek == java.time.LocalDate.now().dayOfWeek
    val isWithinTimeRange = currentTime in startTime..endTime

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var hasAutoScrolled by remember { mutableStateOf(false) }

    val isNearNow by remember {
        derivedStateOf {
            if (viewportHeightPx == 0 || !isToday || !isWithinTimeRange) {
                false
            } else {
                val nowMinutes = Duration.between(startTime, currentTime).toMinutes().toInt()
                val nowLinePosPx = with(density) { (MINUTE_HEIGHT * nowMinutes).toPx() + TOP_TIMELINE_PADDING.toPx() }
                val centeredPosPx = nowLinePosPx - (viewportHeightPx / 2)
                val diff = abs(scrollState.value - centeredPosPx)

                diff < (viewportHeightPx * 0.15f) // 15% threshold for better feel
            }
        }
    }

    LaunchedEffect(isNearNow) {
        onNearNowChanged(isNearNow)
    }

    // Function to perform the scroll to now
    val scrollToNow = suspend {
        if (isWithinTimeRange && viewportHeightPx > 0) {
            val nowMinutes = Duration.between(startTime, currentTime).toMinutes().toInt()
            val nowLinePosPx = with(density) { (MINUTE_HEIGHT * nowMinutes).toPx() + TOP_TIMELINE_PADDING.toPx() }
            val targetPx = (nowLinePosPx - (viewportHeightPx / 2)).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(targetPx)
        }
    }

    // Reset auto-scroll flag when day or settings change
    LaunchedEffect(dayOfWeek, showNowLine, startTime, endTime) {
        hasAutoScrolled = false
    }

    // Handle external scroll trigger (FAB)
    LaunchedEffect(scrollToNowTrigger) {
        if (scrollToNowTrigger > 0 && isToday) {
            scrollToNow()
        }
    }

    LaunchedEffect(clusters, showNowLine, isToday, viewportHeightPx, highlightSlotId, startTime, endTime) {
        if (viewportHeightPx == 0) return@LaunchedEffect
        // An explicit highlight is a direct user request ("view in schedule"), so it bypasses the
        // once-per-day guard. Without this, highlighting a second slot on a day already visited
        // silently did nothing.
        if (hasAutoScrolled && highlightSlotId == null) return@LaunchedEffect

        if (highlightSlotId != null) {
            val targetSlot = clusters.flatMap { it.items }.find { it.second.id == highlightSlotId }?.second
            if (targetSlot != null) {
                val startMinutes = Duration.between(startTime, targetSlot.startTime).toMinutes().toInt()
                val slotTopPx = with(density) { ((MINUTE_HEIGHT * startMinutes) + TOP_TIMELINE_PADDING).toPx() }
                
                // Center the slot in the viewport
                val targetPx = (slotTopPx - (viewportHeightPx / 2)).toInt().coerceAtLeast(0)
                
                scrollState.animateScrollTo(targetPx)
                hasAutoScrolled = true
                return@LaunchedEffect
            }
        }

        if (showNowLine && isToday && isWithinTimeRange) {
            scrollToNow()
            hasAutoScrolled = true
        } else if (!isToday || !showNowLine) {
            val firstClass = clusters.minByOrNull { it.startTime }
            if (firstClass != null) {
                val startMinutes = Duration.between(startTime, firstClass.startTime).toMinutes().toInt()
                val targetMinutes = (startMinutes - 30).coerceAtLeast(0)
                val targetPx = with(density) { ((MINUTE_HEIGHT * targetMinutes) + TOP_TIMELINE_PADDING).toPx() }.toInt()
                scrollState.animateScrollTo(targetPx)
                hasAutoScrolled = true
            } else if (!isToday) {
                scrollState.scrollTo(0)
                hasAutoScrolled = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                viewportHeightPx = coords.size.height
            }
            .verticalScroll(scrollState)
            .padding(top = TOP_TIMELINE_PADDING, bottom = BOTTOM_TIMELINE_PADDING)
    ) {
        HourGridBackground(
            totalHeight = totalHeight,
            startTime = startTime,
            endTime = endTime
        )

        Box(
            modifier = Modifier
                .padding(start = TIME_LABEL_WIDTH, end = TIMELINE_END_PADDING)
                .fillMaxWidth()
                .height(totalHeight)
        ) {
            if (clusters.isEmpty()) {
                val text = if (isToday) {
                    stringResource(R.string.empty_schedule_today)
                } else {
                    val dayName = dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: ""
                    stringResource(R.string.empty_schedule_day, dayName)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                )
            }

            val minuteHeightPx = with(density) { MINUTE_HEIGHT.toPx() }
            val gapPx = with(density) { SLOT_GAP.toPx() }.roundToInt()
            val dividerThicknessPx = with(density) { DIVIDER_THICKNESS.toPx() }.roundToInt()

            clusters.forEach { cluster ->
                val startOffsetMinutes = Duration.between(startTime, cluster.startTime).toMinutes().toInt()
                val endOffsetMinutes = Duration.between(startTime, cluster.endTime).toMinutes().toInt()

                val startLinePx = (minuteHeightPx * startOffsetMinutes).roundToInt()
                val endLinePx = (minuteHeightPx * endOffsetMinutes).roundToInt()

                // Account for line stroke width at top start line
                val topOffsetPx = startLinePx + dividerThicknessPx + gapPx

                // End card exactly gapPx before bottom line's top edge
                val bottomLimitPx = endLinePx - gapPx
                val clusterHeightPx = (bottomLimitPx - topOffsetPx).coerceAtLeast(0)

                val clusterHeightDp = with(density) { clusterHeightPx.toDp() }

                Row(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = topOffsetPx) }
                        .fillMaxWidth()
                        .height(clusterHeightDp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    cluster.columns.forEach { columnItems ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            columnItems.forEach { (subject, slot) ->
                                val itemStartMinutes = Duration.between(cluster.startTime, slot.startTime).toMinutes().toInt()
                                val itemDurationMinutes = Duration.between(slot.startTime, slot.endTime).toMinutes().toInt()

                                val itemTopOffsetPx = (minuteHeightPx * itemStartMinutes).roundToInt()
                                val itemHeightPx = (minuteHeightPx * itemDurationMinutes).roundToInt() - gapPx * 2

                                ClassSlotRow(
                                    modifier = Modifier
                                        .offset { IntOffset(x = 0, y = itemTopOffsetPx) }
                                        .height(with(density) { itemHeightPx.toDp() }),
                                    subject = subject,
                                    slot = slot,
                                    highContrastEnabled = highContrastEnabled,
                                    isHighlighted = slot.id == highlightSlotId,
                                    onClickSubject = { onClickSubject(subject.id, slot.id) },
                                    onLongClickSubject = { onLongClickSubject(subject.id, slot.id) },
                                    isSingle = cluster.items.size == 1
                                )
                            }
                        }
                    }
                }
            }

            if (showNowLine && isToday && isWithinTimeRange) {
                NowLine(
                    modifier = Modifier.fillMaxWidth(),
                    currentTime = currentTime,
                    startTime = startTime,
                    style = nowLineStyle
                )
            }
        }
    }
}

@Composable
fun NowLine(
    modifier: Modifier = Modifier,
    currentTime: LocalTime,
    startTime: LocalTime,
    style: String
) {
    val density = LocalDensity.current
    val nowMinutes = Duration.between(startTime, currentTime).toMinutes().toInt()
    val yOffsetPx = with(density) { (MINUTE_HEIGHT * nowMinutes).toPx() }
    
    val lineColor = if (style == "obvious") Color.Red else MaterialTheme.colorScheme.primary
    val lineThickness = if (style == "obvious") 2.dp else 1.dp
    val circleSize = if (style == "obvious") 8.dp else 6.dp

    Box(
        modifier = modifier
            .offset { IntOffset(0, yOffsetPx.roundToInt()) }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(circleSize)) {
            val circleRadiusPx = (circleSize / 2).toPx()
            val thicknessPx = lineThickness.toPx()
            
            // Draw circle indicator at the start
            drawCircle(
                color = lineColor,
                radius = circleRadiusPx,
                center = Offset(0f, 0f)
            )
            
            // Draw line
            drawLine(
                color = lineColor,
                start = Offset(circleRadiusPx, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = thicknessPx
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassSlotRow(
    modifier: Modifier = Modifier,
    subject: Subject,
    slot: ClassSlot,
    highContrastEnabled: Boolean = false,
    isHighlighted: Boolean = false,
    onClickSubject: () -> Unit,
    onLongClickSubject: () -> Unit = {},
    isSingle: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)),
        modifier = modifier
    ) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    val baseColor = subject.cardColor
    val cardBgColor = if (highContrastEnabled) {
        baseColor.copy(alpha = 0.95f)
    } else {
        baseColor
    }

    val contentColor = if (highContrastEnabled) {
        // Simple luminance check for text color
        val luminance = 0.299 * baseColor.red + 0.587 * baseColor.green + 0.114 * baseColor.blue
        if (luminance > 0.5) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val secondaryContentColor = if (highContrastEnabled) {
        contentColor.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val titleStyle = if (isSingle) {
        MaterialTheme.typography.titleLarge.copy(
            lineBreak = LineBreak.Heading
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(
            lineBreak = LineBreak.Heading
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .alpha(if (slot.isIgnored) 0.45f else 1f)
            .combinedClickable(
                onClick = onClickSubject,
                onLongClick = onLongClickSubject
            ),
        border = if (isHighlighted) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cardBgColor,
            contentColor = contentColor
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (slot.entryType == EntryType.LAB) {
                            Surface(
                                color = contentColor.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = "LAB",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor
                                )
                            }
                        }

                        Text(
                            text = subject.name,
                            modifier = Modifier.weight(1f),
                            style = titleStyle,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3,
                            color = contentColor
                        )
                    }

                    Column {
                        val codeAndGroup = listOfNotNull(
                            subject.code,
                            slot.labGroupName?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")

                        val timeRange = listOfNotNull(
                            "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}",
                            slot.classroom
                        ).joinToString(" • ")

                        Text(
                            text = codeAndGroup,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )

                        Text(
                            text = timeRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryContentColor,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (slot.isIgnored) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = stringResource(R.string.content_description_ignored),
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
private fun HourGridBackground(
    totalHeight: Dp,
    startTime: LocalTime = DAY_START_TIME,
    endTime: LocalTime = DAY_END_TIME,
    minuteHeight: Dp = MINUTE_HEIGHT
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val density = LocalDensity.current
    val minuteHeightPx = with(density) { minuteHeight.toPx() }
    var currentTime = startTime

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        while (currentTime <= endTime) {
            val minutesFromStart = Duration.between(startTime, currentTime).toMinutes().toInt()
            val lineTopOffsetPx = (minuteHeightPx * minutesFromStart).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = lineTopOffsetPx) }
                    .fillMaxWidth()
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = DIVIDER_THICKNESS,
                    modifier = Modifier
                        .padding(start = TIME_LABEL_WIDTH, end = TIMELINE_END_PADDING)
                        .fillMaxWidth()
                )

                Text(
                    text = currentTime.format(timeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(TIME_LABEL_WIDTH)
                        .padding(start = 8.dp, end = 4.dp)
                        .offset(y = (-6).dp)
                )
            }
            currentTime = currentTime.plusMinutes(30)
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 700)
@Composable
fun ScheduleTimelineClusterPreview() {
    val subject = Subject(
        id = 1,
        code = "PROG",
        name = "Programming",
        color = 0xFF2196F3.toInt(),
        isActive = true
    )

    val slots = listOf(
        // Slot 1: 09:00 - 10:30 (Theory)
        Pair(subject, ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30))),
        // Slot 2: 09:00 - 10:30 (Lab)
        Pair(subject, ClassSlot(id = 2, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "L1")),
        // Slot 3: 10:30 - 12:00 (Theory)
        Pair(subject, ClassSlot(id = 3, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 30), endTime = LocalTime.of(12, 0))),
        // Slot 4: 10:30 - 12:00 (Lab)
        Pair(subject, ClassSlot(id = 4, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 30), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")),
        // Slot 5: 09:00 - 12:00 (Overlapping all others)
        Pair(subject, ClassSlot(id = 5, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), classroom = "Main Hall"))
    )

    val clusters = groupSlotsIntoClusters(slots)

    MaterialTheme {
        ScheduleTimeline(
            clusters = clusters,
            onClickSubject = { _, _ -> }
        )
    }
}
