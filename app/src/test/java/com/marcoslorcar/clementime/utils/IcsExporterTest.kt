package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
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
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;UNTIL=20261201T235959Z;BYDAY=MO"))
        assertTrue(ics.contains("DESCRIPTION:CS101 - Dr. Alice (THEORY)"))
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
        assertTrue(ics.contains("UNTIL=20261201T235959Z"))
        assertTrue(ics.contains("UNTIL=20270501T235959Z"))
    }
}
