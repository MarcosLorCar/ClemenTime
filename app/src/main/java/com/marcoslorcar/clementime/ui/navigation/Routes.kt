package com.marcoslorcar.clementime.ui.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
// No arguments: a "view in schedule" request travels as ScheduleFocus instead, precisely because
// launchSingleTop reuses this entry and route arguments went stale between navigations.
object ScheduleListRoute

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
