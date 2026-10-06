package com.marcoslorcar.clementime.data

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
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ClassSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val classroom: String? = null,
    val labGroupName: String? = null, // e.g. "Lab-A1", "Sem-1"
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null,
    val isIgnored: Boolean = false,
) {
    /**
     * Whether this slot represents an elective subgroup variant (e.g. lab, seminar, practice group).
     * Conceptually generalizes [EntryType.LAB].
     */
    val isSubgroup: Boolean get() = entryType == EntryType.LAB
}

