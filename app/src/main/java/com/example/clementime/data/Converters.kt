package com.example.clementime.data

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalTime

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
}
