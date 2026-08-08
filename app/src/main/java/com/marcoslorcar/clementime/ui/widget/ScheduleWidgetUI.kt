package com.marcoslorcar.clementime.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
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

fun stepWeekday(startDate: LocalDate, daysToStep: Int): LocalDate {
    var date = startDate
    val step = if (daysToStep >= 0) 1 else -1
    repeat(kotlin.math.abs(daysToStep)) {
        date = date.plusDays(step.toLong())
        while (date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            date = date.plusDays(step.toLong())
        }
    }
    return date
}

fun getWeekdayDate(todayDate: LocalDate, offset: Int): LocalDate {
    val baseDate = when (todayDate.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> todayDate.plusDays(2)
        java.time.DayOfWeek.SUNDAY -> todayDate.plusDays(1)
        else -> todayDate
    }
    return stepWeekday(baseDate, offset)
}

@Composable
fun ScheduleWidgetContent(
    dayOffset: Int = 0,
    subjectsWithSlots: List<SubjectWithSlots>,
    showNowLine: Boolean,
    nowLineStyle: String = "discrete",
    highContrast: Boolean,
    isDarkTheme: Boolean = true,
    selectedTheme: String = "clementine",
    dayStartTime: LocalTime = DAY_START_TIME,
    dayEndTime: LocalTime = DAY_END_TIME,
    launchAppAction: Action
) {
    val context = LocalContext.current
    val todayDate = LocalDate.now()
    val targetDate = getWeekdayDate(todayDate, dayOffset)
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

    val tomorrowWeekday = stepWeekday(todayDate, 1)
    val dayPillText = when {
        targetDate == todayDate -> context.getString(R.string.widget_today_pill, dayName)
        targetDate == tomorrowWeekday -> context.getString(R.string.widget_tomorrow_pill, dayName)
        else -> dayName
    }

    val forwardBtnText = if (dayOffset == 0 && targetDate == todayDate) {
        context.getString(R.string.widget_toggle_tomorrow)
    } else {
        "→"
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

    val isToday = targetDate == todayDate
    val isWithinTimeRange = currentTime in dayStartTime..dayEndTime
    val shouldShowNowLine = showNowLine && isToday && isWithinTimeRange

    val timelineSegments = remember(daySlots, currentTime, shouldShowNowLine, dayStartTime, dayEndTime) {
        buildTimelineSegments(daySlots, currentTime, shouldShowNowLine, dayStartTime, dayEndTime)
    }

    val themeAccent = if (isDarkTheme) {
        when (selectedTheme.lowercase()) {
            "blueberry" -> Color(0xFF5B8CFF)
            "matcha" -> Color(0xFF81C784)
            "espresso" -> Color(0xFFD7CCC8)
            "grape" -> Color(0xFFBA68C8)
            else -> Color(0xFFFF9F0A)
        }
    } else {
        when (selectedTheme.lowercase()) {
            "blueberry" -> Color(0xFF1E56B7)
            "matcha" -> Color(0xFF2E7D32)
            "espresso" -> Color(0xFF5D4037)
            "grape" -> Color(0xFF7B1FA2)
            else -> Color(0xFFD97706)
        }
    }

    val bgColor = if (isDarkTheme) Color(0xFF141416) else Color(0xFFF5F5F8)
    val headerBgColor = if (isDarkTheme) Color(0xFF1E1E22) else Color(0xFFE8E8EE)
    val primaryPillBg = if (isDarkTheme) Color(0xFF2C2C34) else Color(0xFFD8D8E0)
    val secondaryPillBg = if (isDarkTheme) Color(0xFF3A3A44) else Color(0xFFE0E0E8)
    val textColorSecondary = if (isDarkTheme) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)
    val emptyTextColor = if (isDarkTheme) Color(0x99E5E5EA) else Color(0x993C3C43)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(bgColor)
            .clickable(launchAppAction)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(headerBgColor)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .background(primaryPillBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<ResetWidgetDayAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayPillText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(day = themeAccent, night = themeAccent)
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            if (dayOffset == 0 && targetDate == todayDate) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(secondaryPillBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<NavigateWidgetDayAction>(actionParametersOf(DIRECTION_KEY to 1))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = forwardBtnText.replace(" →", ""),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(day = textColorSecondary, night = textColorSecondary)
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Image(
                            provider = ImageProvider(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = textColorSecondary, night = textColorSecondary))
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(secondaryPillBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .clickable(actionRunCallback<NavigateWidgetDayAction>(actionParametersOf(DIRECTION_KEY to -1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = textColorSecondary, night = textColorSecondary))
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(secondaryPillBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .clickable(actionRunCallback<NavigateWidgetDayAction>(actionParametersOf(DIRECTION_KEY to 1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(day = textColorSecondary, night = textColorSecondary))
                        )
                    }
                }
            }
        }

        if (daySlots.isEmpty()) {
            val emptyText = if (dayOffset == 0) {
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
                            color = ColorProvider(day = emptyTextColor, night = emptyTextColor),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                item {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                }
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
                                    isDarkTheme = isDarkTheme,
                                    launchAppAction = launchAppAction,
                                    isFirstSegment = index == 0
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = GlanceModifier.height(3.dp))
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
            .height(heightDp + 6.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp)
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
                        val cardBgColor = if (highContrast) rawColor.copy(alpha = 0.95f) else rawColor.copy(alpha = 0.35f)
                        val luminance = 0.299 * rawColor.red + 0.587 * rawColor.green + 0.114 * rawColor.blue
                        val titleTextColor = if (highContrast) {
                            if (luminance > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
                        } else {
                            Color(0xFFFFFFFF)
                        }
                        val subTextColor = if (highContrast) {
                            titleTextColor.copy(alpha = 0.75f)
                        } else {
                            Color(0xFFE5E5EA)
                        }

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
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                            color = ColorProvider(day = titleTextColor, night = titleTextColor)
                                        )
                                    )
                                    val labText = if (!slot.labGroupName.isNullOrEmpty()) " (${slot.labGroupName})" else ""
                                    Text(
                                        text = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}$labText",
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = ColorProvider(day = subTextColor, night = subTextColor)
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
    isDarkTheme: Boolean = true,
    launchAppAction: Action,
    isFirstSegment: Boolean = false
) {
    val durationMinutes = Duration.between(segment.startTime, segment.endTime).toMinutes().coerceAtLeast(1)
    val totalHeight = BLOCK_HEIGHT * (durationMinutes / 30.0).toFloat()
    val numBlocks = (durationMinutes / 30).toInt()

    val hourLineColor = if (isDarkTheme) Color(0xFF3A3A3C) else Color(0xFFC6C6C8)
    val halfHourLineColor = if (isDarkTheme) Color(0xFF222224) else Color(0xFFE5E5EA)

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
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.TopStart
            ) {
                val shouldSkipTopLine = isFirstSegment && blockIndex == 0
                if (!shouldSkipTopLine) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                if (isHourMark) hourLineColor else halfHourLineColor
                            )
                    ) {}
                }

                if (blockIndex == numBlocks - 1) {
                    val isEndHourMark = segment.endTime.minute == 0
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    if (isEndHourMark) hourLineColor else halfHourLineColor
                                )
                        ) {}
                    }
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
