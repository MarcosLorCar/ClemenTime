package com.example.clementime.data

import androidx.room.Embedded
import androidx.room.Relation

data class MatterWithSlots(
    @Embedded val matter: Matter,
    @Relation(
        parentColumn = "id",
        entityColumn = "matterId"
    )
    val slots: List<ClassSlot>
)