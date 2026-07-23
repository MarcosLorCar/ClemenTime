package com.github.marcoslorcar.clementime.ui.screens.subject

import androidx.lifecycle.SavedStateHandle
import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.ScheduleDao
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class FakeScheduleDao : ScheduleDao {
    val subjects = mutableListOf<Subject>()
    val slots = mutableListOf<ClassSlot>()

    override fun getActiveSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = flowOf(emptyList())

    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {}

    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {
        val index = subjects.indexOfFirst { it.id == subjectId }
        if (index >= 0) {
            subjects[index] = subjects[index].copy(selectedLabGroup = labGroup)
        }
    }

    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {
        selections.forEach { (id, group) ->
            updateSelectedLabGroup(id, group)
        }
    }

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = flowOf(emptyList())

    override fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?> {
        val m = subjects.find { it.id == subjectId }
        val s = slots.filter { it.subjectId == subjectId }
        return flowOf(m?.let { SubjectWithSlots(it, s) })
    }

    override suspend fun insertSubject(subject: Subject): Long {
        val id = if (subject.id == 0L) (subjects.size + 1).toLong() else subject.id
        val newSubject = subject.copy(id = id)
        subjects.add(newSubject)
        return id
    }

    override suspend fun updateSubject(subject: Subject) {
        val index = subjects.indexOfFirst { it.id == subject.id }
        if (index >= 0) subjects[index] = subject
    }

    override suspend fun deleteSubjectById(subjectId: Long) {
        subjects.removeAll { it.id == subjectId }
    }

    override suspend fun insertSlot(slot: ClassSlot): Long {
        val id = if (slot.id == 0L) (slots.size + 1).toLong() else slot.id
        slots.add(slot.copy(id = id))
        return id
    }

    override suspend fun insertSlots(slots: List<ClassSlot>) {
        this@FakeScheduleDao.slots.addAll(slots)
    }

    override suspend fun updateSlot(slot: ClassSlot) {
        val index = slots.indexOfFirst { it.id == slot.id }
        if (index >= 0) slots[index] = slot
    }

    override suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean) {
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
        subjects.clear()
        slots.clear()
    }

    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {
        subjects.removeAll { it.id in subjectIds }
        slots.removeAll { it.subjectId in subjectIds }
    }

    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {
        for (i in subjects.indices) {
            val m = subjects[i]
            if (m.id in subjectIds) {
                subjects[i] = m.copy(isActive = isActive)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditSubjectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeScheduleDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeScheduleDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addSlot_initializesUnsetTimesAndAutofillsDetails() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)

        viewModel.addSlot()
        val slot1 = viewModel.uiState.value.slots.first()
        assertNull(slot1.startTime)
        assertNull(slot1.endTime)
    }

    @Test
    fun addSlot_autofillsFromPreviousSlot() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)

        viewModel.addSlot()
        val slot1 = viewModel.uiState.value.slots.first()
        viewModel.updateSlot(
            0,
            slot1.copy(
                professor = "Dr. Turing",
                classroom = "Room A",
                entryType = EntryType.LAB,
                labGroupName = "Lab-1"
            )
        )

        viewModel.addSlot()
        val slots = viewModel.uiState.value.slots
        assertEquals(2, slots.size)
        assertEquals("Dr. Turing", slots[1].professor)
        assertEquals("Room A", slots[1].classroom)
        assertEquals("Lab-1", slots[1].labGroupName)
        assertEquals(EntryType.LAB, slots[1].entryType)
        assertNull(slots[1].startTime)
        assertNull(slots[1].endTime)
    }

    @Test
    fun duplicateSlot_clonesExactDetails() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)

        viewModel.addSlot()
        val slot1 = viewModel.uiState.value.slots.first().copy(
            professor = "Prof. Hopper",
            classroom = "B202"
        )
        viewModel.updateSlot(0, slot1)

        viewModel.duplicateSlot(0)

        val slots = viewModel.uiState.value.slots
        assertEquals(2, slots.size)
        assertEquals("Prof. Hopper", slots[1].professor)
        assertEquals("B202", slots[1].classroom)
    }

    @Test
    fun startTimeSelection_autoFillsEndTimeUsingDefaultDuration() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)

        viewModel.updateDefaultDuration(90)
        viewModel.addSlot()

        val start = LocalTime.of(10, 0)
        viewModel.onStartTimeSelected(0, start)

        val updatedSlot = viewModel.uiState.value.slots.first()
        assertEquals(LocalTime.of(10, 0), updatedSlot.startTime)
        assertEquals(LocalTime.of(11, 30), updatedSlot.endTime)
    }

    @Test
    fun endTimeSelectionFirst_autoFillsStartTimeUsingDefaultDuration() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)

        viewModel.updateDefaultDuration(90)
        viewModel.addSlot()

        val end = LocalTime.of(16, 0)
        viewModel.onEndTimeSelected(0, end)

        val updatedSlot = viewModel.uiState.value.slots.first()
        assertEquals(LocalTime.of(14, 30), updatedSlot.startTime)
        assertEquals(LocalTime.of(16, 0), updatedSlot.endTime)
    }

    @Test
    fun saveSubject_persistsIgnoredStatus() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao)
        
        viewModel.updateCode("TEST")
        viewModel.updateName("Test Subject")
        viewModel.addSlot()
        
        val slot = viewModel.uiState.value.slots.first().copy(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            isIgnored = true
        )
        viewModel.updateSlot(0, slot)
        
        viewModel.saveSubject()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(true, fakeDao.slots.first().isIgnored)
    }
}
