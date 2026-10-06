package com.marcoslorcar.clementime.data

/**
 * Defines the schedule synchronization mode chosen by the user.
 * - [ONLINE]: Automatic background synchronization, timetable updates, and change alerts.
 * - [OFFLINE]: Privacy-first, zero background network traffic, manual import only.
 */
enum class SyncMode {
    ONLINE,
    OFFLINE
}
