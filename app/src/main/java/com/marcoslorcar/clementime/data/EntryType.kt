package com.marcoslorcar.clementime.data

/**
 * Distinguishes fixed lectures ([THEORY]) from elective subdivisions or laboratory groups ([LAB]).
 * In the multi-faculty architecture, [LAB] is conceptually treated as generalized subgroups
 * (seminars, workshops, problem sets, or laboratory practice).
 */
enum class EntryType {
    THEORY,
    LAB
}

