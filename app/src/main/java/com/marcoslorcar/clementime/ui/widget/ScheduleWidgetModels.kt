package com.marcoslorcar.clementime.ui.widget

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.utils.DAY_END_TIME
import com.marcoslorcar.clementime.utils.DAY_START_TIME
import com.marcoslorcar.clementime.utils.TimelineCluster
import com.marcoslorcar.clementime.utils.groupSlotsIntoClusters
import java.time.LocalTime

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

fun buildTimelineSegments(
    daySlots: List<Pair<Subject, ClassSlot>>,
    dayStartTime: LocalTime = DAY_START_TIME,
    dayEndTime: LocalTime = DAY_END_TIME
): List<WidgetTimelineSegment> {
    val clusters = groupSlotsIntoClusters(daySlots)
    if (clusters.isEmpty()) {
        return listOf(WidgetTimelineSegment.EmptySegment(dayStartTime, dayEndTime))
    }

    val firstClassStart = clusters.minOf { it.startTime }
    val lastClassEnd = clusters.maxOf { it.endTime }

    // The timeline always spans exactly the classes of the day. The "Now" line is only
    // drawn when the current time falls inside that range (see isNowInSegment in ScheduleWidgetUI).
    val displayStartTime = firstClassStart
    val displayEndTime = lastClassEnd

    val segments = mutableListOf<WidgetTimelineSegment>()
    var curr = displayStartTime

    for (cluster in clusters) {
        if (cluster.startTime > curr) {
            val gapEnd = cluster.startTime.coerceAtMost(displayEndTime)
            if (gapEnd > curr) {
                segments.add(WidgetTimelineSegment.EmptySegment(curr, gapEnd))
            }
        }

        if (curr >= displayEndTime) break

        if (cluster.endTime > curr && cluster.startTime < displayEndTime) {
            val actualClusterEnd = cluster.endTime.coerceAtMost(displayEndTime)
            segments.add(WidgetTimelineSegment.ClusterSegment(cluster))
            curr = actualClusterEnd
        }
    }

    if (curr < displayEndTime) {
        segments.add(WidgetTimelineSegment.EmptySegment(curr, displayEndTime))
    }

    return segments
}
