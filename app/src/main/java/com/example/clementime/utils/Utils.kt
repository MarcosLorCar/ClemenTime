package com.example.clementime.utils

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.Subject
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

/**
 * Represents a horizontal row/bucket in the timeline that contains
 * one or more overlapping class slots.
 */
data class TimelineCluster(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val items: List<Pair<Subject, ClassSlot>>
)