package com.marcoslorcar.clementime.ui.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.uiColor
import com.marcoslorcar.clementime.utils.getNarrowLabel
import com.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Composable
fun ScheduleMiniPreview(
    modifier: Modifier = Modifier,
    slots: List<Pair<Subject, ClassSlot>>,
    overlappingSlotIds: Set<Long> = emptySet(),
    highlightedSlotIds: Set<Long> = emptySet(),
    highlightedSlots: Set<Pair<Subject, ClassSlot>> = emptySet(),
    startTime: LocalTime = LocalTime.of(8, 30),
    endTime: LocalTime = LocalTime.of(21, 30)
) {
    val days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val locale = LocalConfiguration.current.locales[0]
    
    val totalMinutes = Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(1)

    val slotsByDay = remember(slots) { slots.groupBy { it.second.dayOfWeek } }
    val clustersByDay = remember(slotsByDay) {
        days.associateWith { day ->
            groupSlotsIntoClusters(slotsByDay[day].orEmpty())
        }
    }

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
                .weight(1f)
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

                    // Draw horizontal lines indicating 30 mins
                    val stepMinutes = 30
                    val intervalCount = totalMinutes / stepMinutes
                    for (i in 1 until intervalCount) {
                        val minuteOffset = i * stepMinutes
                        val y = size.height * (minuteOffset.toFloat() / totalMinutes)
                        val isHour = minuteOffset % 60 == 0
                        val lineAlpha = if (isHour) 0.20f else 0.10f
                        
                        drawLine(
                            color = Color.LightGray.copy(alpha = lineAlpha),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            ) {
                val maxHeightDp = maxHeight

                Row(modifier = Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        val clusters = clustersByDay[day].orEmpty()
                        
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                        ) {
                            clusters.forEach { cluster ->
                                val startMinutes = Duration.between(startTime, cluster.startTime).toMinutes().toInt()
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
                                    cluster.columns.forEach { columnItems ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            columnItems.forEach { (subject, slot) ->
                                                val isOverlapping = overlappingSlotIds.contains(slot.id)
                                                val isHighlighted = (slot.id > 0 && highlightedSlotIds.contains(slot.id)) ||
                                                        highlightedSlots.any { (hSub, hSlot) ->
                                                            (hSlot.id > 0 && hSlot.id == slot.id) ||
                                                                    (hSub.code == subject.code && hSlot.dayOfWeek == slot.dayOfWeek && hSlot.startTime == slot.startTime && hSlot.endTime == slot.endTime)
                                                        }
                                                val isLab = slot.entryType == EntryType.LAB
                                                val itemDuration = Duration.between(slot.startTime, slot.endTime).toMinutes().toInt().coerceAtLeast(1)
                                                val itemHeight = clusterHeight * (itemDuration.toFloat() / durationMinutes)
                                                val itemTopOffset = clusterHeight * (Duration.between(cluster.startTime, slot.startTime).toMinutes().toFloat() / durationMinutes)

                                                val verticalGap = 0.5.dp
                                                val backgroundAlpha = if (slot.isIgnored) 0.3f else 1f

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height((itemHeight - verticalGap * 2).coerceAtLeast(1.dp))
                                                        .offset(y = itemTopOffset + verticalGap)
                                                        .padding(horizontal = 0.2.dp)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(subject.uiColor.copy(alpha = backgroundAlpha))
                                                        .then(
                                                            if (isHighlighted) {
                                                                Modifier.border(
                                                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                                                    RoundedCornerShape(1.dp)
                                                                )
                                                            } else if (isOverlapping) {
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
                                                            text = if (isLab) "${subject.code}-L" else subject.code,
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
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
fun ScheduleMiniPreviewPreview() {
    val sub1 = Subject(id = 1, code = "PROG", name = "Programming", color = 0xFF2196F3.toInt(), isActive = true)
    val sub2 = Subject(id = 2, code = "ED", name = "Data Structures", color = 0xFF4CAF50.toInt(), isActive = true)

    val slot1 = ClassSlot(id = 101, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(11, 0))
    val slot2 = ClassSlot(id = 102, subjectId = 2, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(13, 0))

    com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme(dynamicColor = false) {
        ScheduleMiniPreview(
            slots = listOf(Pair(sub1, slot1), Pair(sub2, slot2)),
            highlightedSlotIds = setOf(102L),
            modifier = Modifier.fillMaxSize()
        )
    }
}

