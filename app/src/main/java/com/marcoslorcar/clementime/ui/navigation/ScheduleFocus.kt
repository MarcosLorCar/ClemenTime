package com.marcoslorcar.clementime.ui.navigation

import java.time.DayOfWeek

/**
 * A one-shot "show me this slot in the schedule" request.
 *
 * Deliberately *not* carried as navigation arguments. `navigateToTab` uses `launchSingleTop`, which
 * reuses the existing back-stack entry, so `toRoute()` can hand back the previous navigation's
 * values and the ViewModel's SavedStateHandle keeps whatever the first request left behind. This
 * travels as ordinary Compose state held above the NavHost instead, where a new instance is always
 * a new value.
 *
 * [nonce] makes two requests for the same slot distinct, so asking for the same slot twice still
 * re-applies rather than being seen as unchanged state.
 *
 * Being plain composable state, this does not survive process death - acceptable for a transient
 * "jump here now" request, which has no meaning after the app is rebuilt from scratch.
 */
data class ScheduleFocus(
    val dayOfWeek: DayOfWeek,
    val slotId: Long?,
    val nonce: Long = System.nanoTime()
)
