package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleDiffCheckerTest {

    private val sampleSubject = Subject(
        id = 1L,
        code = "FunProg1",
        name = "Fundamentos de Programación 1",
        color = 0xFF4CAF50.toInt(),
        courseGroup = "1A",
        isActive = true,
        semester = 1
    )

    @Test
    fun `findDiffs returns empty list when schedule is unchanged`() {
        val existingSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "John von Neumann - A1.1",
            professor = "Jesus.Serrano",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(existingSlot))

        val remoteSlot = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Lunes",
            horaInicio = "08:30",
            horaFin = "10:00",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "John von Neumann - A1.1",
            profesor = "Jesus.Serrano"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = listOf(remoteSlot),
            currentSemester = 1
        )

        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `findDiffs detects room change`() {
        val existingSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            professor = "Jesus.Serrano",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(existingSlot))

        val remoteSlot = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Lunes",
            horaInicio = "08:30",
            horaFin = "10:00",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "A1.2",
            profesor = "Jesus.Serrano"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = listOf(remoteSlot),
            currentSemester = 1
        )

        assertEquals(1, diffs.size)
        val diff = diffs.first()
        assertEquals("FunProg1", diff.subjectCode)
        assertEquals(DiffType.MODIFIED, diff.changeType)
        assertNotNull(diff.oldDetail)
        assertNotNull(diff.newDetail)
        assertTrue(diff.oldDetail!!.contains("A1.1"))
        assertTrue(diff.newDetail!!.contains("A1.2"))
    }

    @Test
    fun `findDiffs detects time change`() {
        val existingSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            professor = "Jesus.Serrano",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(existingSlot))

        val remoteSlot = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Lunes",
            horaInicio = "09:00",
            horaFin = "10:30",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "A1.1",
            profesor = "Jesus.Serrano"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = listOf(remoteSlot),
            currentSemester = 1
        )

        assertEquals(1, diffs.size)
        val diff = diffs.first()
        assertEquals("FunProg1", diff.subjectCode)
        assertEquals(DiffType.MODIFIED, diff.changeType)
        assertTrue(diff.oldDetail!!.contains("08:30 - 10:00"))
        assertTrue(diff.newDetail!!.contains("09:00 - 10:30"))
    }

    @Test
    fun `findDiffs detects added slot`() {
        val existingSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(existingSlot))

        val remoteSlot1 = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Lunes",
            horaInicio = "08:30",
            horaFin = "10:00",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "A1.1"
        )
        val remoteSlot2 = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Miércoles",
            horaInicio = "11:30",
            horaFin = "13:00",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "A1.1"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = listOf(remoteSlot1, remoteSlot2),
            currentSemester = 1
        )

        assertEquals(1, diffs.size)
        val diff = diffs.first()
        assertEquals(DiffType.ADDED, diff.changeType)
        assertNull(diff.oldDetail)
        assertNotNull(diff.newDetail)
        assertTrue(diff.newDetail!!.contains("Miércoles"))
    }

    @Test
    fun `findDiffs detects removed slot`() {
        val existingSlot1 = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            entryType = EntryType.THEORY
        )
        val existingSlot2 = ClassSlot(
            id = 11L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startTime = LocalTime.of(11, 30),
            endTime = LocalTime.of(13, 0),
            classroom = "A1.1",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(existingSlot1, existingSlot2))

        val remoteSlot = JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Lunes",
            horaInicio = "08:30",
            horaFin = "10:00",
            asignatura = "FunProg1",
            tipo = "teoría",
            aula = "A1.1"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = listOf(remoteSlot),
            currentSemester = 1
        )

        assertEquals(1, diffs.size)
        val diff = diffs.first()
        assertEquals(DiffType.REMOVED, diff.changeType)
        assertNotNull(diff.oldDetail)
        assertNull(diff.newDetail)
        assertTrue(diff.oldDetail!!.contains("Miércoles"))
    }
}
