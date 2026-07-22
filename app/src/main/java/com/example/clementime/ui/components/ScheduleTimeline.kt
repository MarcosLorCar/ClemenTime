package com.example.clementime.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Subject
import com.example.clementime.data.cardColor
import com.example.clementime.utils.TimelineCluster
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DAY_START_TIME: LocalTime = LocalTime.of(8, 30)
private val DAY_END_TIME: LocalTime = LocalTime.of(21, 30)
private val MINUTE_HEIGHT: Dp = 1.4.dp

private val TIME_LABEL_WIDTH: Dp = 56.dp
private val TIMELINE_END_PADDING: Dp = 8.dp
private val TOP_TIMELINE_PADDING: Dp = 8.dp
private val BOTTOM_TIMELINE_PADDING: Dp = 16.dp

private val DIVIDER_THICKNESS: Dp = 1.dp
private val SLOT_GAP: Dp = 3.dp

@Composable
fun ScheduleTimeline(
    clusters: List<TimelineCluster>,
    onClickSubject: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val totalMinutes = Duration.between(DAY_START_TIME, DAY_END_TIME).toMinutes().toInt()
    val totalHeight = MINUTE_HEIGHT * totalMinutes

    LaunchedEffect(clusters) {
        val firstClass = clusters.minByOrNull { it.startTime }
        if (firstClass != null) {
            val startMinutes = Duration.between(DAY_START_TIME, firstClass.startTime).toMinutes().toInt()
            val targetMinutes = (startMinutes - 30).coerceAtLeast(0)
            val targetPx = with(density) { ((MINUTE_HEIGHT * targetMinutes) + TOP_TIMELINE_PADDING).toPx() }.toInt()
            scrollState.animateScrollTo(targetPx)
        } else {
            scrollState.scrollTo(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = TOP_TIMELINE_PADDING, bottom = BOTTOM_TIMELINE_PADDING)
    ) {
        HourGridBackground(
            startTime = DAY_START_TIME,
            endTime = DAY_END_TIME,
            minuteHeight = MINUTE_HEIGHT,
            totalHeight = totalHeight
        )

        Box(
            modifier = Modifier
                .padding(start = TIME_LABEL_WIDTH, end = TIMELINE_END_PADDING)
                .fillMaxWidth()
                .height(totalHeight)
        ) {
            val minuteHeightPx = with(density) { MINUTE_HEIGHT.toPx() }
            val gapPx = with(density) { SLOT_GAP.toPx() }.roundToInt()
            val dividerThicknessPx = with(density) { DIVIDER_THICKNESS.toPx() }.roundToInt()

            clusters.forEach { cluster ->
                val startOffsetMinutes = Duration.between(DAY_START_TIME, cluster.startTime).toMinutes().toInt()
                val endOffsetMinutes = Duration.between(DAY_START_TIME, cluster.endTime).toMinutes().toInt()

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
                    cluster.items.forEach { (subject, slot) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ClassSlotRow(
                                subject = subject,
                                slot = slot,
                                onClickSubject = { onClickSubject(subject.id, slot.id) },
                                modifier = Modifier.fillMaxHeight(),
                                isSingle = cluster.items.size == 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassSlotRow(
    subject: Subject,
    slot: ClassSlot,
    onClickSubject: () -> Unit,
    modifier: Modifier = Modifier,
    isSingle: Boolean = true
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
        onClick = onClickSubject,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .alpha(if (slot.isIgnored) 0.6f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = subject.cardColor
        ) {
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "LAB",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    Text(
                        text = subject.name,
                        modifier = Modifier.weight(1f),
                        style = titleStyle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 3
                    )

                    if (slot.isIgnored) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Ignored",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
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
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = timeRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HourGridBackground(
    startTime: LocalTime,
    endTime: LocalTime,
    minuteHeight: Dp,
    totalHeight: Dp
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