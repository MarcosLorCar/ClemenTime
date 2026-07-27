package com.marcoslorcar.clementime.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.utils.DAY_END_TIME
import com.marcoslorcar.clementime.utils.DAY_START_TIME
import com.marcoslorcar.clementime.utils.TimelineCluster
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

val BLOCK_HEIGHT: Dp = 20.dp

@Composable
fun WidgetLoadingState() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().background(Color(0xFF141416)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(R.string.widget_loading),
            style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White))
        )
    }
}

@Composable
fun WidgetErrorState() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().background(Color(0xFF141416)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(R.string.widget_error),
            style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White))
        )
    }
}

@Composable
fun ScheduleWidgetContent(
    isTomorrow: Boolean,
    subjectsWithSlots: List<SubjectWithSlots>,
    showNowLine: Boolean,
    nowLineStyle: String = "discrete",
    highContrast: Boolean,
    dayStartTime: LocalTime = DAY_START_TIME,
    dayEndTime: LocalTime = DAY_END_TIME,
    launchAppAction: Action
) {
    val context = LocalContext.current
    val todayDate = LocalDate.now()
    val targetDate = if (isTomorrow) {
        when (todayDate.dayOfWeek) {
            java.time.DayOfWeek.FRIDAY -> todayDate.plusDays(3)
            java.time.DayOfWeek.SATURDAY -> todayDate.plusDays(2)
            else -> todayDate.plusDays(1)
        }
    } else {
        todayDate
    }
    val targetDayOfWeek = targetDate.dayOfWeek

    val locale = remember(context) {
        try {
            context.resources.configuration.locales[0]
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    val currentTime = LocalTime.now()

    val rawDayName = targetDayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val dayName = rawDayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val dayPillText = if (isTomorrow) {
        context.getString(R.string.widget_tomorrow_pill, dayName)
    } else {
        context.getString(R.string.widget_today_pill, dayName)
    }
    val toggleBtnText = if (isTomorrow) {
        context.getString(R.string.widget_toggle_today)
    } else {
        val nextDate = when (todayDate.dayOfWeek) {
            java.time.DayOfWeek.FRIDAY -> todayDate.plusDays(3)
            java.time.DayOfWeek.SATURDAY -> todayDate.plusDays(2)
            java.time.DayOfWeek.SUNDAY -> todayDate.plusDays(1)
            else -> todayDate.plusDays(1)
        }
        if (nextDate.dayOfWeek == java.time.DayOfWeek.MONDAY && todayDate.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
            val mondayName = java.time.DayOfWeek.MONDAY.getDisplayName(JavaTextStyle.SHORT, locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            "$mondayName →"
        } else {
            context.getString(R.string.widget_toggle_tomorrow)
        }
    }

    val daySlots = remember(subjectsWithSlots, targetDayOfWeek) {
        subjectsWithSlots.flatMap { subjectWithSlots ->
            val subject = subjectWithSlots.subject
            if (!subject.isActive) emptyList()
            else {
                subjectWithSlots.slots.filter { slot ->
                    slot.dayOfWeek == targetDayOfWeek &&
                            !slot.isIgnored &&
                            (slot.entryType == EntryType.THEORY ||
                                    subject.selectedLabGroup == null ||
                                    slot.labGroupName == subject.selectedLabGroup)
                }.map { slot -> Pair(subject, slot) }
            }
        }
    }

    val isToday = !isTomorrow
    val isWithinTimeRange = currentTime in dayStartTime..dayEndTime
    val shouldShowNowLine = showNowLine && isToday && isWithinTimeRange

    val timelineSegments = remember(daySlots, currentTime, shouldShowNowLine, dayStartTime, dayEndTime) {
        buildTimelineSegments(daySlots, currentTime, shouldShowNowLine, dayStartTime, dayEndTime)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF141416))
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E22))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .background(Color(0xFF2C2C34))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(launchAppAction)
            ) {
                Text(
                    text = dayPillText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = Color(0xFFFF9F0A), night = Color(0xFFFF9F0A))
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .background(Color(0xFF3A3A44))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<ToggleWidgetDayAction>())
            ) {
                Text(
                    text = toggleBtnText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = Color(0xFFE5E5EA), night = Color(0xFFE5E5EA))
                    )
                )
            }
        }

        if (daySlots.isEmpty()) {
            val emptyText = if (!isTomorrow) {
                context.getString(R.string.empty_schedule_today)
            } else {
                val dayOfWeekName = targetDayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
                context.getString(R.string.empty_schedule_day, dayOfWeekName)
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(launchAppAction),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(horizontal = 24.dp)
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = GlanceModifier.size(48.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    Text(
                        text = emptyText,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0x99E5E5EA), night = Color(0x99E5E5EA)),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                timelineSegments.forEachIndexed { index, segment ->
                    val isNowInSegment = shouldShowNowLine && (currentTime >= segment.startTime && currentTime < segment.endTime)
                    item {
                        when (segment) {
                            is WidgetTimelineSegment.ClusterSegment -> {
                                ClusterSegmentRow(
                                    cluster = segment.cluster,
                                    currentTime = currentTime,
                                    isNowInSegment = isNowInSegment,
                                    nowLineStyle = nowLineStyle,
                                    highContrast = highContrast,
                                    launchAppAction = launchAppAction
                                )
                            }
                            is WidgetTimelineSegment.EmptySegment -> {
                                EmptySegmentRow(
                                    segment = segment,
                                    currentTime = currentTime,
                                    isNowInSegment = isNowInSegment,
                                    nowLineStyle = nowLineStyle,
                                    launchAppAction = launchAppAction,
                                    isFirstSegment = index == 0,
                                    isLastSegment = index == timelineSegments.size - 1
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = GlanceModifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ClusterSegmentRow(
    cluster: TimelineCluster,
    currentTime: LocalTime,
    isNowInSegment: Boolean,
    nowLineStyle: String = "discrete",
    highContrast: Boolean,
    launchAppAction: Action
) {
    val durationMinutes = Duration.between(cluster.startTime, cluster.endTime).toMinutes().coerceAtLeast(30)
    val heightDp = BLOCK_HEIGHT * (durationMinutes / 30.0).toFloat()

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp + 4.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(launchAppAction),
        contentAlignment = Alignment.TopStart
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            cluster.columns.forEachIndexed { colIndex, columnItems ->
                if (colIndex > 0) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }

                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                ) {
                    var lastEndTime = cluster.startTime
                    columnItems.forEach { (subject, slot) ->
                        val gapBeforeMinutes = Duration.between(lastEndTime, slot.startTime).toMinutes()
                        if (gapBeforeMinutes > 0) {
                            val gapHeight = BLOCK_HEIGHT * (gapBeforeMinutes / 30.0).toFloat()
                            Spacer(modifier = GlanceModifier.height(gapHeight))
                        }

                        val itemDurationMinutes = Duration.between(slot.startTime, slot.endTime).toMinutes()
                        val itemHeight = BLOCK_HEIGHT * (itemDurationMinutes / 30.0).toFloat()

                        val rawColor = Color(subject.color)
                        val cardBgColor = if (highContrast) rawColor.copy(alpha = 0.95f) else rawColor

                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(itemHeight)
                                .cornerRadius(12.dp)
                                .background(cardBgColor)
                                .clickable(launchAppAction)
                        ) {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(rawColor)
                                ) {}

                                Spacer(modifier = GlanceModifier.width(6.dp))

                                Column(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                                    Text(
                                        text = subject.code.ifEmpty { subject.name },
                                        maxLines = 1,
                                        style = TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(day = Color.White, night = Color.White)
                                        )
                                    )
                                    val labText = if (!slot.labGroupName.isNullOrEmpty()) " (${slot.labGroupName})" else ""
                                    Text(
                                        text = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}$labText",
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(day = Color(0xFFE5E5EA), night = Color(0xFFE5E5EA))
                                        )
                                    )
                                }
                            }
                        }
                        lastEndTime = slot.endTime
                    }
                }
            }
        }

        if (isNowInSegment) {
            val minutesFromStart = Duration.between(cluster.startTime, currentTime).toMinutes()
            val nowTopDp = BLOCK_HEIGHT * (minutesFromStart / 30.0).toFloat()
            WidgetNowLine(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = nowTopDp),
                style = nowLineStyle
            )
        }
    }
}

@Composable
fun WidgetNowLine(
    modifier: GlanceModifier = GlanceModifier,
    style: String
) {
    val isObvious = style == "obvious"
    val lineColor = if (isObvious) Color(0xFFFF3B30) else Color(0xFFFF9F0A)
    val lineThickness = if (isObvious) 2.dp else 1.2.dp
    val circleSize = if (isObvious) 7.dp else 5.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(circleSize)
                .cornerRadius(circleSize / 2)
                .background(lineColor)
        ) {}
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(lineThickness)
                .background(lineColor)
        ) {}
    }
}

@Composable
fun EmptySegmentRow(
    segment: WidgetTimelineSegment.EmptySegment,
    currentTime: LocalTime,
    isNowInSegment: Boolean,
    nowLineStyle: String = "discrete",
    launchAppAction: Action,
    isFirstSegment: Boolean = false,
    isLastSegment: Boolean = false
) {
    val durationMinutes = Duration.between(segment.startTime, segment.endTime).toMinutes().coerceAtLeast(1)
    val totalHeight = BLOCK_HEIGHT * (durationMinutes / 30.0).toFloat()
    val numBlocks = (durationMinutes / 30).toInt()

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(totalHeight)
            .clickable(launchAppAction)
    ) {
        var curr = segment.startTime
        repeat(numBlocks) { blockIndex ->
            val isHourMark = curr.minute == 0
            val isNowInBlock = isNowInSegment && (currentTime >= curr && currentTime < curr.plusMinutes(30))

            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(BLOCK_HEIGHT)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.TopStart
            ) {
                val shouldSkipLine = (isFirstSegment && blockIndex == 0) || (isLastSegment && blockIndex == 0)
                if (!shouldSkipLine) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                if (isHourMark) Color(0xFF3A3A3C) else Color(0xFF222224)
                            )
                    ) {}
                }

                if (isNowInBlock) {
                    val minutesFromBlockStart = Duration.between(curr, currentTime).toMinutes()
                    val nowTopDp = BLOCK_HEIGHT * (minutesFromBlockStart / 30.0).toFloat()
                    WidgetNowLine(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = nowTopDp),
                        style = nowLineStyle
                    )
                }
            }

            curr = curr.plusMinutes(30)
        }
    }
}
