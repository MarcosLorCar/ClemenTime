package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class IcsExporterTest {

    @Test
    fun `generateIcsContent contains basic event details`() {
        val subject = Subject(
            id = 1,
            code = "CS101",
            name = "Intro to CS",
            color = 0,
            isActive = true
        )
        val slot = ClassSlot(
            id = 1,
            subjectId = 1,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            classroom = "Room 101",
            professor = "Dr. Alice",
            entryType = EntryType.THEORY
        )
        val subjectsWithSlots = listOf(SubjectWithSlots(subject, listOf(slot)))
        
        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 12, 1)
        
        val semesterData = IcsExporter.SemesterExportData(subjectsWithSlots, startDate, endDate)
        val ics = IcsExporter.generateIcsContent(listOf(semesterData))
        
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:Intro to CS"))
        assertTrue(ics.contains("LOCATION:Room 101"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;UNTIL=20261201T235959;BYDAY=MO"))
        assertTrue(ics.contains("DESCRIPTION:CS101 - Dr. Alice (Theory)"))
        assertTrue(ics.contains("DTSTART:20260907T090000")) // Sep 7 is first Monday on or after Sep 1
        assertTrue(ics.contains("END:VCALENDAR"))
    }

    @Test
    fun `generateIcsContent handles multiple semesters`() {
        val s1Subject = Subject(id = 1, code = "S1", name = "Sem 1", color = 0, isActive = true)
        val s1Slot = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0))
        
        val s2Subject = Subject(id = 2, code = "S2", name = "Sem 2", color = 0, isActive = true)
        val s2Slot = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(15, 0))

        val semesters = listOf(
            IcsExporter.SemesterExportData(listOf(SubjectWithSlots(s1Subject, listOf(s1Slot))), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1)),
            IcsExporter.SemesterExportData(listOf(SubjectWithSlots(s2Subject, listOf(s2Slot))), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 5, 1))
        )

        val ics = IcsExporter.generateIcsContent(semesters)

        assertTrue(ics.contains("SUMMARY:Sem 1"))
        assertTrue(ics.contains("SUMMARY:Sem 2"))
        assertTrue(ics.contains("UNTIL=20261201T235959"))
        assertTrue(ics.contains("UNTIL=20270501T235959"))
    }

    private fun icsFor(
        subject: Subject,
        slot: ClassSlot,
        start: LocalDate = LocalDate.of(2026, 9, 1),
        end: LocalDate = LocalDate.of(2026, 12, 1)
    ): String = IcsExporter.generateIcsContent(
        listOf(
            IcsExporter.SemesterExportData(
                listOf(SubjectWithSlots(subject, listOf(slot))),
                start,
                end
            )
        )
    )

    private fun slot(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(10, 0),
        classroom: String? = null,
        professor: String? = null,
        isIgnored: Boolean = false
    ) = ClassSlot(
        id = 1,
        subjectId = 1,
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        classroom = classroom,
        professor = professor,
        entryType = EntryType.THEORY,
        isIgnored = isIgnored
    )

    private fun subject(name: String = "Sub", code: String = "C1", notes: String = "") =
        Subject(id = 1, code = code, name = name, color = 0, isActive = true, notes = notes)

    /** UNTIL must be floating to match the floating DTSTART (RFC 5545 3.3.10). */
    @Test
    fun `until has no Z suffix so it matches floating dtstart`() {
        val ics = icsFor(subject(), slot())
        assertTrue(ics.contains("UNTIL=20261201T235959;"))
        assertFalse(ics.contains("UNTIL=20261201T235959Z"))
    }

    @Test
    fun `commas and semicolons in summary and location are escaped`() {
        val ics = icsFor(
            subject(name = "Algebra, Linear; Advanced"),
            slot(classroom = "Room 1, Building B")
        )
        assertTrue(ics.contains("SUMMARY:Algebra\\, Linear\\; Advanced"))
        assertTrue(ics.contains("LOCATION:Room 1\\, Building B"))
    }

    @Test
    fun `backslash in text is escaped before other separators`() {
        val ics = icsFor(subject(name = "A\\B"), slot())
        assertTrue(ics.contains("SUMMARY:A\\\\B"))
    }

    /**
     * Notes reach the description already carrying a literal "\n" separator inserted during
     * assembly; a real newline inside the notes must become "\n" without the assembly
     * separator being double-escaped.
     */
    @Test
    fun `real newline in notes is escaped without double escaping`() {
        val ics = icsFor(subject(notes = "line one\nline two"), slot())
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(unfolded.contains("\\n\\nNotes: line one\\nline two"))
        assertFalse(unfolded.contains("\\\\n"))
    }

    @Test
    fun `entry type is rendered as a readable label not the raw enum`() {
        val ics = icsFor(subject(), slot())
        assertTrue(ics.contains("(Theory)"))
        assertFalse(ics.contains("(THEORY)"))
    }

    @Test
    fun `long lines are folded to 75 octets with a leading space`() {
        val longName = "N".repeat(200)
        val ics = icsFor(subject(name = longName), slot())

        ics.split("\r\n").filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(
                "line exceeded 75 octets: $line",
                line.toByteArray(Charsets.UTF_8).size <= 75
            )
        }
        // Unfolding must restore the original value.
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:$longName"))
    }

    /** Folding counts octets and must never split a multi-byte codepoint. */
    @Test
    fun `folding does not split multibyte characters`() {
        val accented = "Matemáticas Avanzadas ".repeat(8).trim()
        val ics = icsFor(subject(name = accented), slot())

        ics.split("\r\n").filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(
                "line exceeded 75 octets: $line",
                line.toByteArray(Charsets.UTF_8).size <= 75
            )
        }
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:$accented"))
        // A split codepoint would survive an encode/decode round trip as U+FFFD.
        assertFalse(ics.contains("�"))
    }

    @Test
    fun `ignored slots are excluded`() {
        val ics = icsFor(subject(), slot(isIgnored = true))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `slot whose first occurrence falls after semester end is skipped`() {
        // No Monday between Sep 1 2026 (Tue) and Sep 4 2026.
        val ics = icsFor(
            subject(),
            slot(day = DayOfWeek.MONDAY),
            start = LocalDate.of(2026, 9, 1),
            end = LocalDate.of(2026, 9, 4)
        )
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `every line ends with crlf`() {
        val ics = icsFor(subject(), slot(classroom = "R1", professor = "Dr. Bob"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        // No bare LF outside of a CRLF pair.
        assertFalse(ics.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun `uids are stable for same subject and slot`() {
        val s1 = subject()
        val sl1 = slot()
        val ics1 = icsFor(s1, sl1)
        val uid1 = ics1.split("\r\n").find { it.startsWith("UID:") }

        val ics2 = icsFor(s1, sl1)
        val uid2 = ics2.split("\r\n").find { it.startsWith("UID:") }

        assertEquals(uid1, uid2)
    }

    @Test
    fun `contains color property`() {
        val s = subject().copy(color = 0xFFFF0000.toInt()) // Red (AARRGGBB)
        val ics = icsFor(s, slot())
        assertTrue(ics.contains("COLOR:#FF0000"))
    }
}
