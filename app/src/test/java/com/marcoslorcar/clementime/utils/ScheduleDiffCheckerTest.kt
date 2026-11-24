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

    @Test
    fun `findDiffs ignores manually edited subject when upstream remote slots are unchanged`() {
        // Student manually moved their FunProg1 slot to Friday 12:00-13:30 in Room
        val manuallyMovedSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.FRIDAY,
            startTime = LocalTime.of(12, 0),
            endTime = LocalTime.of(13, 30),
            classroom = "Custom Room",
            professor = "Jesus.Serrano",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(manuallyMovedSlot))

        val initialRemoteSlots = listOf(
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Lunes",
                horaInicio = "08:30",
                horaFin = "10:00",
                codigo = "FunProg1",
                asignatura = "Fundamentos de Programación 1",
                tipo = "teoría",
                aula = "A1.1",
                profesor = "Jesus.Serrano"
            )
        )

        val previousRemoteHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = initialRemoteSlots,
            currentSemester = 1
        )

        // New remote JSON updates another subject, while FunProg1 remains identical upstream
        val newRemoteSlots = initialRemoteSlots + JsonFlatSlot(
            grupo = "1A",
            cuatrimestre = "1C",
            dia = "Martes",
            horaInicio = "10:00",
            horaFin = "11:30",
            codigo = "Cálculo",
            asignatura = "Cálculo",
            tipo = "teoría",
            aula = "B2.1"
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = newRemoteSlots,
            currentSemester = 1,
            previousRemoteSubjectHashes = previousRemoteHashes
        )

        assertTrue(diffs.isEmpty())
    }

    @Test
    fun `findDiffs alerts manually edited subject if upstream remote slots actually change`() {
        val manuallyMovedSlot = ClassSlot(
            id = 10L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.FRIDAY,
            startTime = LocalTime.of(12, 0),
            endTime = LocalTime.of(13, 30),
            classroom = "Custom Room",
            professor = "Jesus.Serrano",
            entryType = EntryType.THEORY
        )
        val existingSubjectWithSlots = SubjectWithSlots(sampleSubject, listOf(manuallyMovedSlot))

        val initialRemoteSlots = listOf(
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Lunes",
                horaInicio = "08:30",
                horaFin = "10:00",
                codigo = "FunProg1",
                asignatura = "Fundamentos de Programación 1",
                tipo = "teoría",
                aula = "A1.1",
                profesor = "Jesus.Serrano"
            )
        )

        val previousRemoteHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = initialRemoteSlots,
            currentSemester = 1
        )

        // Now the university actually changes FunProg1's official classroom
        val updatedRemoteSlots = listOf(
            initialRemoteSlots.first().copy(aula = "A2.4")
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(existingSubjectWithSlots),
            remoteSlots = updatedRemoteSlots,
            currentSemester = 1,
            previousRemoteSubjectHashes = previousRemoteHashes
        )

        assertEquals(1, diffs.size)
        assertEquals("FunProg1", diffs.first().subjectCode)
        assertTrue(diffs.first().newDetail!!.contains("A2.4"))
    }

    @Test
    fun `findDiffs isolates third trimester slots from earlier periods`() {
        val term3Subject = sampleSubject.copy(id = 3L, code = "OPT3", name = "Optativa III", semester = 3)
        val existingSlot = ClassSlot(
            id = 30L,
            subjectId = 3L,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 30),
            classroom = "B1.1",
            entryType = EntryType.THEORY
        )
        val remoteSlots = listOf(
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1T",
                dia = "Lunes",
                horaInicio = "08:30",
                horaFin = "10:00",
                codigo = "OPT3",
                asignatura = "Optativa III",
                tipo = "teoría",
                aula = "A1.1"
            ),
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "3T",
                dia = "Miércoles",
                horaInicio = "10:00",
                horaFin = "11:30",
                codigo = "OPT3",
                asignatura = "Optativa III",
                tipo = "teoría",
                aula = "B2.5"
            )
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(SubjectWithSlots(term3Subject, listOf(existingSlot))),
            remoteSlots = remoteSlots,
            currentSemester = 3
        )

        assertEquals(1, diffs.size)
        assertEquals(DiffType.MODIFIED, diffs.first().changeType)
        assertTrue(diffs.first().oldDetail!!.contains("B1.1"))
        assertTrue(diffs.first().newDetail!!.contains("B2.5"))
    }
}
