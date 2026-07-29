package com.marcoslorcar.clementime.data.importing.repository

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportRepositoryTest {

    @Test
    fun importSubjects_replacesExistingSubjectWithSameCode() = runTest {
        val existingSubject = Subject(
            id = 42L,
            code = "SO",
            name = "Sistemas Operativos Old",
            color = 0xFF123456.toInt(),
            isActive = true
        )
        val fakeDao = FakeScheduleDaoForRepositoryTest(
            initialSubjects = listOf(SubjectWithSlots(existingSubject, emptyList()))
        )
        val repository = ImportRepository(dao = fakeDao)

        val newJsonSubject = JsonSubject(
            code = "SO",
            name = "Sistemas Operativos Updated"
        )
        val selected = SelectedSubject(newJsonSubject, "1º A")

        repository.importSubjects(listOf(selected))

        assertEquals(1, fakeDao.upsertedSubjects.size)
        val (upsertedSubject, _) = fakeDao.upsertedSubjects[0]
        // Should preserve the existing ID (42L) and color (0xFF123456.toInt())
        assertEquals(42L, upsertedSubject.id)
        assertEquals("SO", upsertedSubject.code)
        assertEquals("Sistemas Operativos Updated", upsertedSubject.name)
        assertEquals(0xFF123456.toInt(), upsertedSubject.color)
    }

    @Test
    fun applySlotDiffs_updatesSubjectWithNewClassSlots() = runTest {
        val existingSubject = Subject(
            id = 10L,
            code = "FunProg1",
            name = "Fundamentos de Programación 1",
            color = 0xFF4CAF50.toInt(),
            isActive = true,
            semester = 1
        )
        val fakeDao = FakeScheduleDaoForRepositoryTest(
            initialSubjects = listOf(SubjectWithSlots(existingSubject, emptyList()))
        )
        val repository = ImportRepository(dao = fakeDao)

        val diffs = listOf(
            com.marcoslorcar.clementime.utils.SlotDiff(
                subjectCode = "FunProg1",
                subjectName = "Fundamentos de Programación 1",
                changeType = com.marcoslorcar.clementime.utils.DiffType.MODIFIED,
                oldDetail = "Lunes 08:30 - 10:00",
                newDetail = "Martes 10:00 - 11:30"
            )
        )

        val remoteSlots = listOf(
            com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Martes",
                horaInicio = "10:00",
                horaFin = "11:30",
                asignatura = "FunProg1",
                tipo = "teoría",
                aula = "A1.1",
                profesor = "Prof. Serrano"
            )
        )

        repository.applySlotDiffs(diffs = diffs, remoteSlots = remoteSlots, semester = 1)

        assertEquals(1, fakeDao.upsertedSubjects.size)
        val (upsertedSubject, newSlots) = fakeDao.upsertedSubjects[0]
        assertEquals(10L, upsertedSubject.id)
        assertEquals("FunProg1", upsertedSubject.code)
        assertEquals(1, newSlots.size)
        val slot = newSlots[0]
        assertEquals(java.time.DayOfWeek.TUESDAY, slot.dayOfWeek)
        assertEquals(java.time.LocalTime.of(10, 0), slot.startTime)
        assertEquals(java.time.LocalTime.of(11, 30), slot.endTime)
        assertEquals("A1.1", slot.classroom)
        assertEquals("Prof. Serrano", slot.professor)
    }
}

class FakeScheduleDaoForRepositoryTest(
    initialSubjects: List<SubjectWithSlots> = emptyList()
) : com.marcoslorcar.clementime.data.ScheduleDao {
    val subjectsFlow = MutableStateFlow(initialSubjects)
    val upsertedSubjects = mutableListOf<Pair<Subject, List<ClassSlot>>>()

    override fun getAllSubjectsWithSlots() = subjectsFlow
    override fun getAllSubjectsWithSlotsBySemester(semester: Int) = subjectsFlow
    override fun getActiveSubjectsWithSlotsBySemester(semester: Int) = subjectsFlow
    override fun getSubjectWithSlotsById(subjectId: Long) = MutableStateFlow(null)
    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {}
    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {}
    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {}
    override suspend fun insertSubject(subject: Subject): Long = 1L
    override suspend fun updateSubject(subject: Subject) {}
    override suspend fun deleteSubjectById(subjectId: Long) {}
    override suspend fun insertSlot(slot: ClassSlot): Long = 1L
    override suspend fun insertSlots(slots: List<ClassSlot>) {}
    override suspend fun updateSlot(slot: ClassSlot) {}
    override suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean) {}
    override suspend fun deleteSlot(slot: ClassSlot) {}
    override suspend fun deleteSlotById(slotId: Long) {}
    override suspend fun deleteSlotsForSubject(subjectId: Long) {}
    override suspend fun deleteAllSubjects() {}
    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {}
    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {}

    override suspend fun upsertSubjectWithSlots(subject: Subject, slots: List<ClassSlot>) {
        upsertedSubjects.add(Pair(subject, slots))
    }
}
