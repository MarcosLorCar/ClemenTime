package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.SubjectWithSlots
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

object IcsExporter {

    data class SemesterExportData(
        val subjectsWithSlots: List<SubjectWithSlots>,
        val startDate: java.time.LocalDate,
        val endDate: java.time.LocalDate
    )

    private val icsDateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val icsDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun generateIcsContent(
        semesters: List<SemesterExportData>
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//MarcosLorCar//ClemenTime//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")

        val now = LocalDateTime.now(ZoneId.of("UTC")).format(icsDateTimeFormatter) + "Z"

        semesters.forEach { (subjectsWithSlots, semesterStart, semesterEnd) ->
            subjectsWithSlots.forEach { subjectWithSlots ->
                val subject = subjectWithSlots.subject
                subjectWithSlots.slots.filter { !it.isIgnored }.forEach { slot ->
                    // Find first occurrence of the day of week on or after semesterStart
                    val firstOccurrence = semesterStart.with(TemporalAdjusters.nextOrSame(slot.dayOfWeek))
                    
                    if (!firstOccurrence.isAfter(semesterEnd)) {
                        val startDateTime = LocalDateTime.of(firstOccurrence, slot.startTime)
                        val endDateTime = LocalDateTime.of(firstOccurrence, slot.endTime)
                        
                        val untilDate = semesterEnd.format(icsDateFormatter)

                        sb.append("BEGIN:VEVENT\r\n")
                        sb.append("UID:${UUID.randomUUID()}\r\n")
                        sb.append("DTSTAMP:$now\r\n")
                        sb.append("DTSTART:${startDateTime.format(icsDateTimeFormatter)}\r\n")
                        sb.append("DTEND:${endDateTime.format(icsDateTimeFormatter)}\r\n")
                        
                        val byDay = when (slot.dayOfWeek) {
                            java.time.DayOfWeek.MONDAY -> "MO"
                            java.time.DayOfWeek.TUESDAY -> "TU"
                            java.time.DayOfWeek.WEDNESDAY -> "WE"
                            java.time.DayOfWeek.THURSDAY -> "TH"
                            java.time.DayOfWeek.FRIDAY -> "FR"
                            java.time.DayOfWeek.SATURDAY -> "SA"
                            java.time.DayOfWeek.SUNDAY -> "SU"
                        }
                        
                        sb.append("RRULE:FREQ=WEEKLY;UNTIL=${untilDate}T235959Z;BYDAY=$byDay\r\n")
                        sb.append("SUMMARY:${subject.name}\r\n")
                        
                        val description = buildString {
                            append(subject.code)
                            slot.professor?.let { append(" - $it") }
                            append(" (${slot.entryType})")
                            subject.notes.let { if (it.isNotBlank()) append("\\n\\nNotes: $it") }
                        }.replace("\n", "\\n")
                        
                        sb.append("DESCRIPTION:$description\r\n")
                        slot.classroom?.let { sb.append("LOCATION:$it\r\n") }
                        sb.append("END:VEVENT\r\n")
                    }
                }
            }
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }
}
