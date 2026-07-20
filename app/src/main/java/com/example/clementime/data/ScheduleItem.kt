package com.example.clementime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_items")
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val startTime: Long, // Epoch millis
    val endTime: Long,   // Epoch millis
    val isCompleted: Boolean = false
)
