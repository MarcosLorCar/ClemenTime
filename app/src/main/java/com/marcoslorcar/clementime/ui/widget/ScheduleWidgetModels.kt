package com.marcoslorcar.clementime.ui.widget

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.Subject
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

/**
 * Builds the widget timeline for one day. The range is exactly the day's classes: the
 * configured day start/end no longer play a part, since nothing is drawn outside them.
 */
fun buildTimelineSegments(
    daySlots: List<Pair<Subject, ClassSlot>>
): List<WidgetTimelineSegment> {
    val clusters = groupSlotsIntoClusters(daySlots)
    // No entries means no time scope, so there is nothing to draw and nowhere the Now line
    // could belong. Returning a full-day EmptySegment here would put a grid — and the line —
    // on a day with no classes.
    if (clusters.isEmpty()) {
        return emptyList()
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
