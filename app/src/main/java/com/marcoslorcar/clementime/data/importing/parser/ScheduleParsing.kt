package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException

private val labType = Regex("""\b(?:labs?|laboratorios?|laborator(?:y|ies))\b""", RegexOption.IGNORE_CASE)
private val daysByName = DayOfWeek.entries
    .associateBy { dayOfWeekToSpanish(it).uppercase() } +
    DayOfWeek.entries.associateBy { it.name } +
    mapOf("MIERCOLES" to DayOfWeek.WEDNESDAY, "SABADO" to DayOfWeek.SATURDAY)

internal fun parseDayOfWeek(day: String): DayOfWeek =
    daysByName[day.trim().uppercase()] ?: DayOfWeek.MONDAY

internal fun dayOfWeekToSpanish(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "Lunes"
        DayOfWeek.TUESDAY -> "Martes"
        DayOfWeek.WEDNESDAY -> "Miércoles"
        DayOfWeek.THURSDAY -> "Jueves"
        DayOfWeek.FRIDAY -> "Viernes"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }
}

internal fun normalizeGroup(group: String?): String {
    if (group.isNullOrBlank()) return ""
    return group.uppercase()
        .replace("GRUPO", "")
        .replace("GRADO", "")
        .replace("BILINGÜE", "")
        .replace("BILINGUE", "")
        .replace("º", "")
        .replace("ª", "")
        .replace("-", "")
        .replace(".", "")
        .replace(" ", "")
        .trim()
}

internal fun parseLocalTime(time: String): LocalTime {
    val trimmed = time.trim()
    val parts = trimmed.split(':', limit = 3)
    if (parts.size == 2) {
        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()
        if (hour != null && minute != null) return LocalTime.of(hour, minute)
    }
    return LocalTime.parse(trimmed)
}

internal fun parseRemoteTime(time: String): LocalTime = try {
    parseLocalTime(time)
} catch (_: DateTimeParseException) {
    LocalTime.MIDNIGHT
}

internal fun JsonFlatSlot.isLab(): Boolean = esLaboratorio || labType.containsMatchIn(tipo)
