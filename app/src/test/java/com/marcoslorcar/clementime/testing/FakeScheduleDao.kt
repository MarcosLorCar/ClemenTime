package com.marcoslorcar.clementime.testing

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeScheduleDao(
    initialSubjects: List<SubjectWithSlots> = emptyList()
) : ScheduleDao {

    val subjects = mutableListOf<Subject>()
    val slots = mutableListOf<ClassSlot>()
    val subjectsFlow = MutableStateFlow(initialSubjects)

    val upsertedSubjects = mutableListOf<Pair<Subject, List<ClassSlot>>>()
    val deletedSubjectIds = mutableListOf<Long>()
    val updatedActiveStatus = mutableMapOf<Long, Boolean>()
    val updatedIgnoredStatus = mutableMapOf<Long, Boolean>()
    val bulkUpdatedActiveStatus = mutableMapOf<List<Long>, Boolean>()
    var deleteAllSubjectsCalled = false
    var deleteSubjectsByIdsCalledWith: List<Long>? = null
    var updateSelectedLabGroupsCalledWith: Map<Long, String?>? = null

    init {
        initialSubjects.forEach { (subject, subjectSlots) ->
            subjects.add(subject)
            slots.addAll(subjectSlots)
        }
    }

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = subjectsFlow

    override fun getAllSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        subjectsFlow.map { list -> list.filter { it.subject.semester == semester } }

    override fun getActiveSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        subjectsFlow.map { list -> list.filter { it.subject.semester == semester && it.subject.isActive } }

    override fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?> {
        val found = subjects.find { it.id == subjectId }
        val foundSlots = slots.filter { it.subjectId == subjectId }
        return flowOf(found?.let { SubjectWithSlots(it, foundSlots) })
    }

    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {
        updatedActiveStatus[subjectId] = isActive
        val index = subjects.indexOfFirst { it.id == subjectId }
        if (index >= 0) {
            subjects[index] = subjects[index].copy(isActive = isActive)
        }
    }

    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {
        val index = subjects.indexOfFirst { it.id == subjectId }
        if (index >= 0) {
            subjects[index] = subjects[index].copy(selectedLabGroup = labGroup)
        }
    }

    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {
        updateSelectedLabGroupsCalledWith = selections
        selections.forEach { (id, group) -> updateSelectedLabGroup(id, group) }
    }

    override suspend fun insertSubject(subject: Subject): Long {
        val id = if (subject.id == 0L) (subjects.size + 1).toLong() else subject.id
        val newSubject = subject.copy(id = id)
        val existingIdx = subjects.indexOfFirst { it.id == id }
        if (existingIdx >= 0) {
            subjects[existingIdx] = newSubject
        } else {
            subjects.add(newSubject)
        }
        return id
    }

    override suspend fun updateSubject(subject: Subject) {
        val index = subjects.indexOfFirst { it.id == subject.id }
        if (index >= 0) subjects[index] = subject
    }

    override suspend fun deleteSubjectById(subjectId: Long) {
        deletedSubjectIds.add(subjectId)
        subjects.removeAll { it.id == subjectId }
    }

    override suspend fun insertSlot(slot: ClassSlot): Long {
        val id = if (slot.id == 0L) (slots.size + 1).toLong() else slot.id
        slots.add(slot.copy(id = id))
        return id
    }

    override suspend fun insertSlots(slots: List<ClassSlot>) {
        this.slots.addAll(slots)
    }

    override suspend fun updateSlot(slot: ClassSlot) {
        val index = slots.indexOfFirst { it.id == slot.id }
        if (index >= 0) slots[index] = slot
    }

    override suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean) {
        updatedIgnoredStatus[slotId] = isIgnored
        val index = slots.indexOfFirst { it.id == slotId }
        if (index >= 0) {
            slots[index] = slots[index].copy(isIgnored = isIgnored)
        }
    }

    override suspend fun deleteSlot(slot: ClassSlot) {
        slots.removeIf { it.id == slot.id }
    }

    override suspend fun deleteSlotById(slotId: Long) {
        slots.removeIf { it.id == slotId }
    }

    override suspend fun deleteSlotsForSubject(subjectId: Long) {
        slots.removeAll { it.subjectId == subjectId }
    }

    override suspend fun deleteAllSubjects() {
        deleteAllSubjectsCalled = true
        subjects.clear()
        slots.clear()
    }

    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {
        deleteSubjectsByIdsCalledWith = subjectIds
        subjects.removeAll { it.id in subjectIds }
        slots.removeAll { it.subjectId in subjectIds }
    }

    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {
        bulkUpdatedActiveStatus[subjectIds] = isActive
        for (i in subjects.indices) {
            val subject = subjects[i]
            if (subject.id in subjectIds) {
                subjects[i] = subject.copy(isActive = isActive)
            }
        }
    }

    override suspend fun upsertSubjectWithSlots(subject: Subject, slots: List<ClassSlot>) {
        upsertedSubjects.add(subject to slots)
        super.upsertSubjectWithSlots(subject, slots)
    }
}
