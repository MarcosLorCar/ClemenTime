package com.example.clementime.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleListRoute(
    val dayOfWeek: String? = null
)

@Serializable
object SettingsRoute {
}

@Serializable
data class ImportRoute(
    val uriString: String
)

@Serializable
object MattersRoute

@Serializable
data class AddEditMatterRoute(
    val matterId: Long? = null,
    val highlightSlotId: Long? = null
)