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

    /**
     * Escapes a raw value for use as an iCalendar TEXT property (RFC 5545 3.3.11).
     * Must be applied to raw user data *before* it is assembled into a property, otherwise
     * the backslash pass would double-escape separators inserted during assembly.
     */
    private fun escapeText(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")

    /**
     * Folds a content line to 75 octets (RFC 5545 3.1). Counting is done in UTF-8 octets and
     * never splits a multi-byte codepoint - subject names carry accents, and cutting one in
     * half would corrupt the file.
     */
    private fun foldLine(line: String): String {
        val sb = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val codePoint = line.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val chunk = line.substring(i, i + charCount)
            val size = chunk.toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                sb.append("\r\n ")
                octets = 1 // the leading space counts toward the folded line
                limit = 75
            }
            sb.append(chunk)
            octets += size
            i += charCount
        }
        return sb.toString()
    }

    private fun StringBuilder.appendProperty(line: String) {
        append(foldLine(line))
        append("\r\n")
    }

    private fun entryTypeLabel(entryType: Any?): String = when (entryType?.toString()) {
        "THEORY" -> "Theory"
        "LAB" -> "Lab"
        else -> entryType?.toString().orEmpty()
    }

    private fun generateStableUid(subject: com.marcoslorcar.clementime.data.Subject, slot: com.marcoslorcar.clementime.data.ClassSlot): String {
        val raw = "${subject.code}-${subject.semester}-${slot.dayOfWeek}-${slot.startTime}-${slot.entryType}"
        val hash = UUID.nameUUIDFromBytes(raw.toByteArray(Charsets.UTF_8)).toString()
        return "$hash@marcoslorcar.clementime"
    }

    private fun formatColor(colorInt: Int): String {
        return String.format("#%06X", (0xFFFFFF and colorInt))
    }

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
                        sb.append("UID:${generateStableUid(subject, slot)}\r\n")
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
                        
                        // UNTIL must match DTSTART's value type (RFC 5545 3.3.10). DTSTART is
                        // floating local time, so UNTIL must be floating too - no trailing Z.
                        sb.appendProperty("RRULE:FREQ=WEEKLY;UNTIL=${untilDate}T235959;BYDAY=$byDay")
                        sb.appendProperty("SUMMARY:${escapeText(subject.name)}")
                        sb.appendProperty("COLOR:${formatColor(subject.color)}")

                        val description = buildString {
                            append(escapeText(subject.code))
                            slot.professor?.let { append(" - ${escapeText(it)}") }
                            append(" (${escapeText(entryTypeLabel(slot.entryType))})")
                            subject.notes.let {
                                if (it.isNotBlank()) append("\\n\\nNotes: ${escapeText(it)}")
                            }
                        }

                        sb.appendProperty("DESCRIPTION:$description")
                        slot.classroom?.let { sb.appendProperty("LOCATION:${escapeText(it)}") }
                        sb.append("END:VEVENT\r\n")
                    }
                }
            }
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }
}
