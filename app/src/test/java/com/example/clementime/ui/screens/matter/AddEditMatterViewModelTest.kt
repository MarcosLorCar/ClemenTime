package com.example.clementime.ui.screens.matter

import androidx.lifecycle.SavedStateHandle
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.ScheduleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

class FakeScheduleDao : ScheduleDao {
    val matters = mutableListOf<Matter>()
    val slots = mutableListOf<ClassSlot>()

    override fun getActiveMattersWithSlots(): Flow<List<MatterWithSlots>> = flowOf(emptyList())

    override suspend fun updateMatterActiveStatus(matterId: Long, isActive: Boolean) {}

    override fun getAllMattersWithSlots(): Flow<List<MatterWithSlots>> = flowOf(emptyList())

    override fun getMatterWithSlotsById(matterId: Long): Flow<MatterWithSlots?> {
        val m = matters.find { it.id == matterId }
        val s = slots.filter { it.matterId == matterId }
        return flowOf(m?.let { MatterWithSlots(it, s) })
    }

    override suspend fun insertMatter(matter: Matter): Long {
        val id = if (matter.id == 0L) (matters.size + 1).toLong() else matter.id
        val newMatter = matter.copy(id = id)
        matters.add(newMatter)
        return id
    }

    override suspend fun updateMatter(matter: Matter) {
        val index = matters.indexOfFirst { it.id == matter.id }
        if (index >= 0) matters[index] = matter
    }

    override suspend fun deleteMatterById(matterId: Long) {
        matters.removeAll { it.id == matterId }
    }

    override suspend fun insertSlot(slot: ClassSlot): Long = 1L

    override suspend fun insertSlots(slotsToInsert: List<ClassSlot>) {
        slots.addAll(slotsToInsert)
    }

    override suspend fun updateSlot(slot: ClassSlot) {}

    override suspend fun deleteSlot(slot: ClassSlot) {}

    override suspend fun deleteSlotById(slotId: Long) {}

    override suspend fun deleteSlotsForMatter(matterId: Long) {
        slots.removeAll { it.matterId == matterId }
    }

    override suspend fun deleteAllMatters() {
        matters.clear()
        slots.clear()
    }

    override suspend fun deleteMattersByIds(matterIds: List<Long>) {
        matters.removeAll { it.id in matterIds }
        slots.removeAll { it.matterId in matterIds }
    }

    override suspend fun updateMattersActiveStatus(matterIds: List<Long>, isActive: Boolean) {
        for (i in matters.indices) {
            val m = matters[i]
            if (m.id in matterIds) {
                matters[i] = m.copy(isActive = isActive)
            }
        }
    }
}

class AddEditMatterViewModelTest {

    private lateinit var fakeDao: FakeScheduleDao

    @Before
    fun setUp() {
        fakeDao = FakeScheduleDao()
    }

    @Test
    fun addSlot_initializesUnsetTimesAndAutofillsDetails() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

        viewModel.addSlot()
        val slot1 = viewModel.uiState.value.slots.first()
        assertNull(slot1.startTime)
        assertNull(slot1.endTime)
    }

    @Test
    fun addSlot_autofillsFromPreviousSlot() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

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
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

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
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

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
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

        viewModel.updateDefaultDuration(90)
        viewModel.addSlot()

        val end = LocalTime.of(16, 0)
        viewModel.onEndTimeSelected(0, end)

        val updatedSlot = viewModel.uiState.value.slots.first()
        assertEquals(LocalTime.of(14, 30), updatedSlot.startTime)
        assertEquals(LocalTime.of(16, 0), updatedSlot.endTime)
    }

    @Test
    fun endTimeSelection_updatesDefaultDuration() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditMatterViewModel(savedStateHandle, fakeDao)

        viewModel.addSlot()
        val start = LocalTime.of(10, 0)
        viewModel.onStartTimeSelected(0, start)

        val end = LocalTime.of(12, 0) // 120 minutes
        viewModel.onEndTimeSelected(0, end)

        assertEquals(120, viewModel.uiState.value.defaultDurationMinutes)
        assertEquals(end, viewModel.uiState.value.slots.first().endTime)
    }
}
