package com.github.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.uiColor
import com.github.marcoslorcar.clementime.utils.getNarrowLabel
import com.github.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

private val PREVIEW_START_TIME = LocalTime.of(8, 0)
private val PREVIEW_END_TIME = LocalTime.of(21, 0)

@Composable
fun ScheduleMiniPreview(
    modifier: Modifier = Modifier,
    slots: List<Pair<Subject, ClassSlot>>,
    overlappingSlotIds: Set<Long> = emptySet()
) {
    val days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val locale = LocalConfiguration.current.locales[0]
    
    val totalMinutes = Duration.between(PREVIEW_START_TIME, PREVIEW_END_TIME).toMinutes().toInt()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    Text(
                        text = day.getNarrowLabel(locale).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))

            BoxWithConstraints(modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .drawBehind {
                    val strokeColor = Color.LightGray.copy(alpha = 0.25f)
                    val strokeWidth = 0.75.dp.toPx()

                    // Draw 5 vertical lines for day columns separator
                    val daysCount = 5
                    for (i in 1 until daysCount) {
                        val x = size.width * (i.toFloat() / daysCount)
                        drawLine(
                            color = strokeColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            ) {
                val maxHeightDp = maxHeight

                Row(modifier = Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        val daySlots = remember(slots) { slots.filter { it.second.dayOfWeek == day } }
                        val clusters = remember(daySlots) { groupSlotsIntoClusters(daySlots) }
                        
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                        ) {
                            clusters.forEach { cluster ->
                                val startMinutes = Duration.between(PREVIEW_START_TIME, cluster.startTime).toMinutes().toInt()
                                val durationMinutes = Duration.between(cluster.startTime, cluster.endTime).toMinutes().toInt().coerceAtLeast(1)
                                
                                val topOffset = maxHeightDp * (startMinutes.toFloat() / totalMinutes)
                                val clusterHeight = maxHeightDp * (durationMinutes.toFloat() / totalMinutes)
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(clusterHeight)
                                        .offset(y = topOffset),
                                    horizontalArrangement = Arrangement.spacedBy(0.5.dp)
                                ) {
                                    cluster.items.forEach { (subject, slot) ->
                                        val isOverlapping = overlappingSlotIds.contains(slot.id)
                                        val itemDuration = Duration.between(slot.startTime, slot.endTime).toMinutes().toInt().coerceAtLeast(1)
                                        val itemHeight = clusterHeight * (itemDuration.toFloat() / durationMinutes)
                                        val itemTopOffset = clusterHeight * (Duration.between(cluster.startTime, slot.startTime).toMinutes().toFloat() / durationMinutes)

                                        val verticalGap = 0.5.dp

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height((itemHeight - verticalGap * 2).coerceAtLeast(1.dp))
                                                .offset(y = itemTopOffset + verticalGap)
                                                .padding(horizontal = 0.2.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(subject.uiColor.copy(alpha = if (slot.isIgnored) 0.3f else 1f))
                                                .then(
                                                    if (isOverlapping) {
                                                        Modifier.border(
                                                            BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                                            RoundedCornerShape(1.dp)
                                                        )
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (clusterHeight > 10.dp) {
                                                Text(
                                                    text = subject.code,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 5.sp,
                                                        lineHeight = 5.sp,
                                                        fontWeight = FontWeight.Black
                                                    ),
                                                    color = Color.White,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
