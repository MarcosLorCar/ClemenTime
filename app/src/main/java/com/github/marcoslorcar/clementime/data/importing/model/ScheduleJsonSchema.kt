package com.github.marcoslorcar.clementime.data.importing.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root schema for imported JSON schedules.
 */
@Serializable
data class ScheduleJsonSchema(
    val version: Int = 1,                 // Schema version for future-proofing compatibility
    val title: String? = null,            // Descriptive name of the schedule (e.g. "First Semester 2026/27")
    @SerialName("matters") val subjects: List<JsonSubject> = emptyList(), // Root subjects (Common for everyone)
    val years: List<JsonYear> = emptyList()
)

/**
 * Representation of a year level in the JSON schema.
 */
@Serializable
data class JsonYear(
    val name: String,                     // e.g. "1º"
    @SerialName("matters") val subjects: List<JsonSubject> = emptyList(), // Subjects common to all groups in this year
    val groups: List<JsonGroup> = emptyList()
)

/**
 * Representation of a course group/section in the JSON schema.
 */
@Serializable
data class JsonGroup(
    val name: String,                     // e.g. "A"
    @SerialName("matters") val subjects: List<JsonSubject> = emptyList()
)

/**
 * Representation of a subject/matter in the JSON schema.
 */
@Serializable
data class JsonSubject(
    val code: String,                     // Unique subject identifier (e.g. "SO")
    val name: String,                     // Full name of the subject (e.g. "Sistemas Operativos")
    val color: Int? = null,               // Color represented as ARGB Int (e.g. 0xFF4CAF50)
    val theorySlots: List<JsonTimeSlot> = emptyList(),
    val labVariants: Map<String, List<JsonTimeSlot>> = emptyMap(), // Map of lab variants, keyed by group name (e.g. "Lab-A1")
    val isDummy: Boolean = false
)

/**
 * Class schedule slot details in the JSON schema.
 */
@Serializable
data class JsonTimeSlot(
    val dayOfWeek: String,                // e.g. "MONDAY" (matching DayOfWeek enum names)
    val startTime: String,                // Format: "HH:mm" (e.g. "08:30")
    val endTime: String,                  // Format: "HH:mm" (e.g. "10:00")
    val classroom: String? = null,        // Classroom or room number
    val groupName: String? = null,        // Specific subgroup or variant name, null for theory
    val entryType: String = "THEORY",     // Class type: "THEORY" or "LAB"
    val professor: String? = null         // Instructor's name
)