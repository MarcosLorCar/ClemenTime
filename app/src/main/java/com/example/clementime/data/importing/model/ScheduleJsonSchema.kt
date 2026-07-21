package com.example.clementime.data.importing.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleJsonSchema(
    val version: Int = 1,
    val title: String? = null,
    val courseGroup: String? = null,
    val matters: List<JsonMatter> = emptyList()
)

@Serializable
data class JsonMatter(
    val code: String,
    val name: String,
    val color: Int? = null,
    val courseGroup: String? = null,
    val theorySlots: List<JsonTimeSlot> = emptyList(),
    val labVariants: Map<String, List<JsonTimeSlot>> = emptyMap()
)

@Serializable
data class JsonTimeSlot(
    val dayOfWeek: String, // e.g. "MONDAY"
    val startTime: String, // e.g. "08:30"
    val endTime: String,   // e.g. "10:00"
    val classroom: String? = null,
    val groupName: String? = null, // e.g. "Lab-A1"
    val entryType: String = "THEORY", // "THEORY", "LAB", "EXAM", "SEMINAR"
    val professor: String? = null
)