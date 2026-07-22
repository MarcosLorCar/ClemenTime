package com.example.clementime.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(
    tableName = "class_slots",
    foreignKeys = [
        ForeignKey(
            entity = Matter::class,
            parentColumns = ["id"],
            childColumns = ["matterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("matterId")]
)
data class ClassSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matterId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val classroom: String? = null,
    val labGroupName: String? = null, // e.g. "Lab-A1"
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null,
)
