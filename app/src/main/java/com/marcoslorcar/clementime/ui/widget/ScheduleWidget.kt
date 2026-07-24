package com.marcoslorcar.clementime.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.marcoslorcar.clementime.MainActivity
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.utils.DAY_END_TIME
import com.marcoslorcar.clementime.utils.DAY_START_TIME
import com.marcoslorcar.clementime.utils.TimelineCluster
import com.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

private val BLOCK_HEIGHT: Dp = 18.dp

sealed interface WidgetTimelineSegment {
    val startTime: LocalTime
    val endTime: LocalTime

    data class ClusterSegment(
        val cluster: TimelineCluster
    ) : WidgetTimelineSegment {
        override val startTime: LocalTime get() = cluster.startTime
        override val endTime: LocalTime get() = cluster.endTime
    }

    data class EmptySegment(
        override val startTime: LocalTime,
        override val endTime: LocalTime
    ) : WidgetTimelineSegment
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleWidgetEntryPoint {
    fun scheduleDao(): ScheduleDao
    fun settingsRepository(): SettingsRepository
}

class ScheduleWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ScheduleWidgetEntryPoint::class.java
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val isTomorrowSelected = prefs[IS_TOMORROW_KEY] ?: false

            val subjectsWithSlots by remember(entryPoint) {
                entryPoint?.scheduleDao()?.getActiveSubjectsWithSlots() ?: kotlinx.coroutines.flow.flowOf(emptyList())
            }.collectAsState(initial = emptyList())

            val showNowLine by remember(entryPoint) {
                entryPoint?.settingsRepository()?.showNowLineFlow ?: kotlinx.coroutines.flow.flowOf(true)
            }.collectAsState(initial = true)

            val highContrast by remember(entryPoint) {
                entryPoint?.settingsRepository()?.highContrastFlow ?: kotlinx.coroutines.flow.flowOf(false)
            }.collectAsState(initial = false)

            GlanceTheme {
                ScheduleWidgetContent(
                    context = context,
                    isTomorrow = isTomorrowSelected,
                    subjectsWithSlots = subjectsWithSlots,
                    showNowLine = showNowLine,
                    highContrast = highContrast
                )
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(ScheduleWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    val current = this[REFRESH_KEY] ?: 0
                    this[REFRESH_KEY] = current + 1
                }
            }
            update(context, glanceId)
        }
    }
}

val REFRESH_KEY = androidx.datastore.preferences.core.intPreferencesKey("refresh_count")

@Composable
private fun ScheduleWidgetContent(
    context: Context,
    isTomorrow: Boolean,
    subjectsWithSlots: List<SubjectWithSlots>,
    showNowLine: Boolean,
    highContrast: Boolean
) {
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
    val locale =
        context.resources.configuration.locales[0]
    val currentTime = LocalTime.now()

    val rawDayName = targetDayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val dayName = rawDayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val dayPillText = if (isTomorrow) "Tomorrow • $dayName" else "Today • $dayName"
    val toggleBtnText = if (isTomorrow) "← Today" else "Tomorrow →"

    // Gather active slots for target day
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

    val timelineSegments = remember(daySlots) {
        buildTimelineSegments(daySlots)
    }

    val isToday = !isTomorrow
    val isWithinTimeRange = currentTime in DAY_START_TIME..DAY_END_TIME
    val shouldShowNowLine = showNowLine && isToday && isWithinTimeRange

    // Target ComponentName action to open MainActivity reliably
    val launchAppAction = remember(context) {
        val intent = Intent().apply {
            component = ComponentName(context, MainActivity::class.java)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        actionStartActivity(intent)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF141416)))
    ) {
        // Sleek Minimalist Header Bar
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(0xFF1E1E22)))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day Pill Badge (Tap opens app)
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF2C2C34)))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(launchAppAction)
            ) {
                Text(
                    text = dayPillText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFFFF9F0A))
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Day Toggle Button (Tap switches Today/Tomorrow)
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF3A3A44)))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<ToggleWidgetDayAction>())
            ) {
                Text(
                    text = toggleBtnText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFFE5E5EA))
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Manual Refresh Button
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(Color(0xFF3A3A44)))
                    .cornerRadius(12.dp)
                    .padding(6.dp)
                    .clickable(actionRunCallback<RefreshAction>())
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }

        // Timeline Segment LazyColumn or Empty State
        if (daySlots.isEmpty()) {
            val dayOfWeekName = targetDayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
            val emptyText = context.getString(R.string.empty_schedule_day, dayOfWeekName)
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
                            color = ColorProvider(Color(0x99E5E5EA)),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                timelineSegments.forEach { segment ->
                    val isNowInSegment = shouldShowNowLine && (currentTime >= segment.startTime && currentTime < segment.endTime)
                    item {
                        when (segment) {
                            is WidgetTimelineSegment.ClusterSegment -> {
                                ClusterSegmentRow(
                                    cluster = segment.cluster,
                                    currentTime = currentTime,
                                    isNowInSegment = isNowInSegment,
                                    highContrast = highContrast,
                                    launchAppAction = launchAppAction
                                )
                            }
                            is WidgetTimelineSegment.EmptySegment -> {
                                EmptySegmentRow(
                                    segment = segment,
                                    currentTime = currentTime,
                                    isNowInSegment = isNowInSegment,
                                    launchAppAction = launchAppAction
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildTimelineSegments(
    daySlots: List<Pair<Subject, ClassSlot>>
): List<WidgetTimelineSegment> {
    val clusters = groupSlotsIntoClusters(daySlots)
    if (clusters.isEmpty()) {
        return listOf(WidgetTimelineSegment.EmptySegment(DAY_START_TIME, DAY_END_TIME))
    }

    val segments = mutableListOf<WidgetTimelineSegment>()
    var curr = DAY_START_TIME

    for (cluster in clusters) {
        if (cluster.startTime > curr) {
            segments.add(WidgetTimelineSegment.EmptySegment(curr, cluster.startTime))
        }
        segments.add(WidgetTimelineSegment.ClusterSegment(cluster))
        curr = cluster.endTime
    }

    if (curr < DAY_END_TIME) {
        segments.add(WidgetTimelineSegment.EmptySegment(curr, DAY_END_TIME))
    }

    return segments
}

@Composable
private fun ClusterSegmentRow(
    cluster: TimelineCluster,
    currentTime: LocalTime,
    isNowInSegment: Boolean,
    highContrast: Boolean,
    launchAppAction: Action
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val durationMinutes = Duration.between(cluster.startTime, cluster.endTime).toMinutes().coerceAtLeast(30)
    val heightDp = BLOCK_HEIGHT * (durationMinutes / 30.0).toFloat()

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(launchAppAction),
        contentAlignment = Alignment.TopStart
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            cluster.items.forEachIndexed { index, (subject, slot) ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                
                val rawColor = Color(subject.color)
                val cardBgColor = if (highContrast) rawColor.copy(alpha = 0.95f) else rawColor

                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(ColorProvider(cardBgColor))
                        .cornerRadius(12.dp)
                        .clickable(launchAppAction)
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color strip
                        Box(
                            modifier = GlanceModifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(ColorProvider(rawColor))
                        ) {}

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Column(
                            modifier = GlanceModifier.defaultWeight()
                        ) {
                            Text(
                                text = subject.code.ifEmpty { subject.name },
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White)
                                )
                            )
                            val labText = if (!slot.labGroupName.isNullOrEmpty()) " (${slot.labGroupName})" else ""
                            Text(
                                text = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}$labText",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFE5E5EA))
                                )
                            )
                        }
                    }
                }
            }
        }

        if (isNowInSegment) {
            val minutesFromStart = Duration.between(cluster.startTime, currentTime).toMinutes()
            val nowTopDp = BLOCK_HEIGHT * (minutesFromStart / 30.0).toFloat()
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = nowTopDp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(6.dp)
                        .height(6.dp)
                        .background(ColorProvider(Color(0xFFFF3B30)))
                        .cornerRadius(3.dp)
                ) {}
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(2.dp)
                        .background(ColorProvider(Color(0xFFFF3B30)))
                ) {}
            }
        }
    }
}

@Composable
private fun EmptySegmentRow(
    segment: WidgetTimelineSegment.EmptySegment,
    currentTime: LocalTime,
    isNowInSegment: Boolean,
    launchAppAction: Action
) {
    val durationMinutes = Duration.between(segment.startTime, segment.endTime).toMinutes().coerceAtLeast(30)
    val numBlocks = (durationMinutes / 30).toInt()

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(launchAppAction)
    ) {
        var curr = segment.startTime
        repeat(numBlocks) {
            val isHourMark = curr.minute == 0
            val isNowInBlock = isNowInSegment && (currentTime >= curr && currentTime < curr.plusMinutes(30))

            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(BLOCK_HEIGHT)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.TopStart
            ) {
                // Subtle horizontal grid line
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            ColorProvider(
                                if (isHourMark) Color(0xFF3A3A3C) else Color(0xFF222224)
                            )
                        )
                ) {}

                if (isNowInBlock) {
                    val minutesFromBlockStart = Duration.between(curr, currentTime).toMinutes()
                    val nowTopDp = BLOCK_HEIGHT * (minutesFromBlockStart / 30.0).toFloat()
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = nowTopDp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(6.dp)
                                .height(6.dp)
                                .background(ColorProvider(Color(0xFFFF3B30)))
                                .cornerRadius(3.dp)
                        ) {}
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(2.dp)
                                .background(ColorProvider(Color(0xFFFF3B30)))
                        ) {}
                    }
                }
            }

            curr = curr.plusMinutes(30)
        }
    }
}

