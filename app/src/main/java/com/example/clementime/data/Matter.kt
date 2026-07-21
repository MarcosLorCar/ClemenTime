package com.example.clementime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matters")
data class Matter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,                 // e.g. "FunProg1"
    val name: String,                 // e.g. "Fundamentos de Programación 1"
    val color: Int,                   // UI color tag
    val courseGroup: String? = null,  // e.g. "1º A"
    val defaultDurationMinutes: Int? = 90
)