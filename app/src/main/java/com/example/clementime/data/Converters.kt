package com.example.clementime.data

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class Converters {
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? {
        return value?.value
    }

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? {
        return value?.let { DayOfWeek.of(it) }
    }

    @TypeConverter
    fun fromEntryType(value: EntryType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toEntryType(value: String?): EntryType? {
        return value?.let { EntryType.valueOf(it) }
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromAttachedFiles(value: List<AttachedFileItem>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toAttachedFiles(value: String?): List<AttachedFileItem>? {
        return value?.let { json.decodeFromString(it) }
    }
}
