package com.github.marcoslorcar.clementime.utils

import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.Subject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Returns a unique single-character label for the day of the week, handling common
 * linguistic collisions (e.g., Tuesday/Thursday in English, Tuesday/Wednesday in Spanish).
 */
fun DayOfWeek.getNarrowLabel(locale: Locale): String {
    val standard = this.getDisplayName(TextStyle.NARROW, locale)
    if (standard.isEmpty()) return ""
    
    val firstChar = standard.first()

    return when {
        // Spanish Wednesday: Martes (M) vs Miércoles (M) -> Miércoles becomes 'X'
        this == DayOfWeek.WEDNESDAY && locale.language == "es" && firstChar.equals('m', true) -> {
            if (firstChar.isLowerCase()) "x" else "X"
        }
        
        // English Thursday: Tuesday (T) vs Thursday (T) -> Thursday becomes 'R'
        this == DayOfWeek.THURSDAY && locale.language == "en" && firstChar.equals('t', true) -> {
            if (firstChar.isLowerCase()) "r" else "R"
        }
        
        // English Sunday: Saturday (S) vs Sunday (S) -> Sunday becomes 'U'
        this == DayOfWeek.SUNDAY && locale.language == "en" && firstChar.equals('s', true) -> {
            if (firstChar.isLowerCase()) "u" else "U"
        }
        
        else -> standard
    }
}

fun DayOfWeek.shortName(locale: Locale): String =
    getDisplayName(
        TextStyle.FULL_STANDALONE,
        locale
    ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }.take(3)

/**
 * Represents a horizontal row/bucket in the timeline that contains
 * one or more overlapping class slots.
 */
data class TimelineCluster(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val items: List<Pair<Subject, ClassSlot>>
)

fun groupSlotsIntoClusters(
    slots: List<Pair<Subject, ClassSlot>>
): List<TimelineCluster> {
    if (slots.isEmpty()) return emptyList()

    val sorted = slots.sortedBy { it.second.startTime }
    val clusters = mutableListOf<TimelineCluster>()

    var currentClusterItems = mutableListOf(sorted.first())
    var clusterStart = sorted.first().second.startTime
    var clusterEnd = sorted.first().second.endTime

    for (i in 1 until sorted.size) {
        val item = sorted[i]
        val slot = item.second

        // Check if current slot overlaps with the active cluster
        if (slot.startTime < clusterEnd) {
            currentClusterItems.add(item)
            if (slot.endTime > clusterEnd) {
                clusterEnd = slot.endTime
            }
        } else {
            // No overlap: finalize current cluster and start a new one
            clusters.add(TimelineCluster(clusterStart, clusterEnd, currentClusterItems))
            currentClusterItems = mutableListOf(item)
            clusterStart = slot.startTime
            clusterEnd = slot.endTime
        }
    }

    clusters.add(TimelineCluster(clusterStart, clusterEnd, currentClusterItems))
    return clusters
}
