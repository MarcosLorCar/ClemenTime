package com.github.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.ui.screens.subject.ClassSlotUiModel
import com.github.marcoslorcar.clementime.utils.shortName
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun SubjectScheduleMiniPreview(
    modifier: Modifier = Modifier,
    slots: List<ClassSlotUiModel>,
    subjectColor: Color,
    selectedLabGroup: String? = null,
    onSlotClick: ((ClassSlotUiModel) -> Unit)? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val days = remember {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
    }

    val startHour = 8
    val endHour = 20
    val totalMinutes = (endHour - startHour) * 60

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.shortName(locale),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar Grid Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEachIndexed { index, day ->
                        val daySlots = remember(slots, day, selectedLabGroup) {
                            slots.filter { slot ->
                                slot.dayOfWeek == day &&
                                        !slot.isIgnored &&
                                        (slot.entryType != EntryType.LAB || selectedLabGroup == null || slot.labGroupName == null || slot.labGroupName == selectedLabGroup)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (index < days.size - 1) {
                                        Modifier.border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    } else Modifier
                                )
                        ) {
                            daySlots.forEach { slot ->
                                val sTime = slot.startTime ?: LocalTime.of(9, 0)
                                val eTime = slot.endTime ?: sTime.plusMinutes(90)

                                val sMinutes = (sTime.hour * 60 + sTime.minute - startHour * 60).coerceIn(0, totalMinutes)
                                val eMinutes = (eTime.hour * 60 + eTime.minute - startHour * 60).coerceIn(0, totalMinutes)

                                val topWeight = sMinutes.toFloat() / totalMinutes.toFloat()
                                val slotWeight = ((eMinutes - sMinutes).coerceAtLeast(30)).toFloat() / totalMinutes.toFloat()

                                Column(modifier = Modifier.fillMaxHeight()) {
                                    if (topWeight > 0f) {
                                        Spacer(modifier = Modifier.weight(topWeight))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(slotWeight)
                                            .padding(horizontal = 2.dp, vertical = 1.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (slot.entryType == EntryType.LAB) subjectColor.copy(alpha = 0.7f)
                                                else subjectColor
                                            )
                                            .then(
                                                if (onSlotClick != null) Modifier.clickable { onSlotClick(slot) } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val labelText = if (slot.entryType == EntryType.LAB) {
                                            slot.labGroupName ?: "L"
                                        } else "T"
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    val remainingWeight = 1f - topWeight - slotWeight
                                    if (remainingWeight > 0f) {
                                        Spacer(modifier = Modifier.weight(remainingWeight.coerceAtLeast(0.001f)))
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
