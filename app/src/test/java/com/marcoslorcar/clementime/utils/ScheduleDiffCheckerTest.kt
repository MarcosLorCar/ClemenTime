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
        assertNotNull(diff.oldSlot)
        assertNotNull(diff.newSlot)
        assertEquals("A1.1", diff.oldSlot?.classroom)
        assertEquals("A1.2", diff.newSlot?.classroom)
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
        assertEquals(LocalTime.of(8, 30), diff.oldSlot?.startTime)
        assertEquals(LocalTime.of(10, 0), diff.oldSlot?.endTime)
        assertEquals(LocalTime.of(9, 0), diff.newSlot?.startTime)
        assertEquals(LocalTime.of(10, 30), diff.newSlot?.endTime)
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
        assertNull(diff.oldSlot)
        assertNotNull(diff.newSlot)
        assertEquals(DayOfWeek.WEDNESDAY, diff.newSlot?.dayOfWeek)
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
        assertNotNull(diff.oldSlot)
        assertNull(diff.newSlot)
        assertEquals(DayOfWeek.WEDNESDAY, diff.oldSlot?.dayOfWeek)
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
        assertEquals("A2.4", diffs.first().newSlot?.classroom)
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
        assertEquals("B1.1", diffs.first().oldSlot?.classroom)
        assertEquals("B2.5", diffs.first().newSlot?.classroom)
    }

    @Test
    fun `findDiffs populates structured snapshots and diff preview preserves theory and weekend slots`() {
        val existingTheory = ClassSlot(
            id = 40L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            classroom = "A1.1",
            professor = "Prof. Ada",
            entryType = EntryType.THEORY
        )
        val remoteSlots = listOf(
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Sábado",
                horaInicio = "10:00",
                horaFin = "12:00",
                codigo = "FunProg1",
                asignatura = "Fundamentos de Programación 1",
                tipo = "teoría",
                aula = "A2.3",
                profesor = "Prof. Ada"
            ),
            JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Jueves",
                horaInicio = "15:30",
                horaFin = "17:30",
                codigo = "FunProg1",
                asignatura = "Fundamentos de Programación 1",
                tipo = "laboratorio",
                esLaboratorio = true,
                aula = "Lab 3",
                grupoPracticas = "L1"
            )
        )

        val diffs = ScheduleDiffChecker.findDiffs(
            existingSubjects = listOf(SubjectWithSlots(sampleSubject, listOf(existingTheory))),
            remoteSlots = remoteSlots,
            currentSemester = 1
        )

        assertEquals(2, diffs.size)
        val modified = diffs.first { it.changeType == DiffType.MODIFIED }
        val addedLab = diffs.first { it.changeType == DiffType.ADDED }

        assertEquals(EntryType.THEORY, modified.oldSlot?.entryType)
        assertEquals(DayOfWeek.MONDAY, modified.oldSlot?.dayOfWeek)
        assertEquals(EntryType.THEORY, modified.newSlot?.entryType)
        assertEquals(DayOfWeek.SATURDAY, modified.newSlot?.dayOfWeek)
        assertEquals(EntryType.LAB, addedLab.newSlot?.entryType)
        assertEquals(DayOfWeek.THURSDAY, addedLab.newSlot?.dayOfWeek)

        val (beforeSlots, afterSlots, highlighted) =
            com.marcoslorcar.clementime.ui.screens.scheduleimport.buildSlotsFromDiffs(diffs)

        assertEquals(1, beforeSlots.size)
        assertEquals(EntryType.THEORY, beforeSlots.single().second.entryType)
        assertEquals(DayOfWeek.MONDAY, beforeSlots.single().second.dayOfWeek)
        assertEquals(sampleSubject.color, beforeSlots.single().first.color)

        assertEquals(2, afterSlots.size)
        assertEquals(2, highlighted.size)
        val afterTheory = afterSlots.first { it.second.dayOfWeek == DayOfWeek.SATURDAY }
        val afterLab = afterSlots.first { it.second.dayOfWeek == DayOfWeek.THURSDAY }
        assertEquals(EntryType.THEORY, afterTheory.second.entryType)
        assertEquals(EntryType.LAB, afterLab.second.entryType)
    }

    @Test
    fun `formatDiffSlotDetail formats weekday and metadata in English and Spanish`() {
        val snapshot = DiffSlotSnapshot(
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            professor = "Prof. Ada"
        )
        val en = com.marcoslorcar.clementime.ui.screens.scheduleimport.formatDiffSlotDetail(
            snapshot = snapshot,
            locale = java.util.Locale.ENGLISH,
            roomFormatted = "Room: A1.1",
            profFormatted = "Prof: Prof. Ada"
        )
        val es = com.marcoslorcar.clementime.ui.screens.scheduleimport.formatDiffSlotDetail(
            snapshot = snapshot,
            locale = java.util.Locale.forLanguageTag("es"),
            roomFormatted = "Aula: A1.1",
            profFormatted = "Prof: Prof. Ada"
        )
        assertEquals("Wednesday 08:30 - 10:00 (Room: A1.1, Prof: Prof. Ada)", en)
        assertEquals("Miércoles 08:30 - 10:00 (Aula: A1.1, Prof: Prof. Ada)", es)
    }
}
