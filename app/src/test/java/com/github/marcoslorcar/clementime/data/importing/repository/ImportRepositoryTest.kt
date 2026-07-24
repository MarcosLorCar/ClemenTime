package com.github.marcoslorcar.clementime.data.importing.repository

import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject
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
}

class FakeScheduleDaoForRepositoryTest(
    initialSubjects: List<SubjectWithSlots> = emptyList()
) : com.github.marcoslorcar.clementime.data.ScheduleDao {
    val subjectsFlow = MutableStateFlow(initialSubjects)
    val upsertedSubjects = mutableListOf<Pair<Subject, List<ClassSlot>>>()

    override fun getAllSubjectsWithSlots() = subjectsFlow
    override fun getActiveSubjectsWithSlots() = subjectsFlow
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
