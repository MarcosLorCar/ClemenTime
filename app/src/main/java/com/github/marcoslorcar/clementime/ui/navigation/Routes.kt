package com.github.marcoslorcar.clementime.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleListRoute(
    val dayOfWeek: String? = null,
    val highlightSlotId: Long? = null
)

@Serializable
object SettingsRoute

@Serializable
object ImportRoute

@Serializable
object SubjectsRoute

@Serializable
object ConflictResolverRoute

@Serializable
data class AddEditSubjectRoute(
    val subjectId: Long? = null,
    val highlightSlotId: Long? = null
)