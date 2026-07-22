package com.example.clementime.data

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matters")
data class Matter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,                 // e.g. "FunProg1"
    val name: String,                 // e.g. "Fundamentos de Programación 1"
    val color: Int,                   // UI color tag
    val courseGroup: String? = null,  // e.g. "1º A"
    val isActive: Boolean,
    val defaultDurationMinutes: Int? = 90,
    val notes: String = "",
    val attachedFiles: List<AttachedFileItem> = emptyList()
) {
    companion object {
        val PRESET_COLORS = listOf(
            0xFF4CAF50.toInt(), // Green
            0xFF2196F3.toInt(), // Blue
            0xFF9C27B0.toInt(), // Purple
            0xFFFF9800.toInt(), // Orange
            0xFFE91E63.toInt(), // Pink
            0xFF00BCD4.toInt(), // Cyan
            0xFF3F51B5.toInt(), // Indigo
            0xFF8BC34A.toInt(), // Lime
            0xFFFFC107.toInt(), // Amber
            0xFFFF5722.toInt(), // Deep Orange
            0xFF009688.toInt(), // Teal
            0xFF673AB7.toInt(), // Deep Purple
            0xFF795548.toInt(), // Brown
            0xFF607D8B.toInt()  // Blue Grey
        )
    }
}

val Matter.uiColor: Color
    get() = Color(this.color)

val Matter.cardColor: Color
    get() = uiColor.copy(alpha = 0.3f)