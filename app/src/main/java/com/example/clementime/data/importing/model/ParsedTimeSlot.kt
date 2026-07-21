package com.example.clementime.data.importing.model

import com.example.clementime.data.EntryType
import java.time.DayOfWeek
import java.time.LocalTime

data class ParsedTimeSlot(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val classroom: String? = null,
    val labGroupName: String? = null,
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null
)