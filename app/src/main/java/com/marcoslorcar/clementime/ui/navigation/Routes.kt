package com.marcoslorcar.clementime.ui.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ScheduleListRoute(
    val dayOfWeek: String? = null,
    val highlightSlotId: Long? = null
)

@Keep
@Serializable
data class MoreRoute(
    val showDiff: Boolean = false
)

@Keep
@Serializable
object ImportRoute

@Keep
@Serializable
object SubjectsRoute

@Keep
@Serializable
object ConflictResolverRoute

@Keep
@Serializable
object OnboardingRoute

@Keep
@Serializable
data class AddEditSubjectRoute(
    val subjectId: Long? = null,
    val highlightSlotId: Long? = null
)
