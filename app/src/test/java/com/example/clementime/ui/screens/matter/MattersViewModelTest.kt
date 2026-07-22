package com.example.clementime.ui.screens.matter

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.ScheduleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MattersViewModelTestFakeDao : ScheduleDao {
    val mattersFlow = MutableStateFlow<List<MatterWithSlots>>(emptyList())
    val deletedMatterIds = mutableListOf<Long>()
    val updatedActiveStatus = mutableMapOf<Long, Boolean>()
    var deleteAllMattersCalled = false
    var deleteMattersByIdsCalledWith: List<Long>? = null

    override fun getAllMattersWithSlots(): Flow<List<MatterWithSlots>> = mattersFlow

    override fun getActiveMattersWithSlots(): Flow<List<MatterWithSlots>> = flowOf(emptyList())

    override fun getMatterWithSlotsById(matterId: Long): Flow<MatterWithSlots?> = flowOf(null)

    override suspend fun updateMatterActiveStatus(matterId: Long, isActive: Boolean) {
        updatedActiveStatus[matterId] = isActive
    }

    override suspend fun insertMatter(matter: Matter): Long = 1L

    override suspend fun updateMatter(matter: Matter) {}

    override suspend fun deleteMatterById(matterId: Long) {
        deletedMatterIds.add(matterId)
    }

    override suspend fun insertSlot(slot: ClassSlot): Long = 1L

    override suspend fun insertSlots(slots: List<ClassSlot>) {}

    override suspend fun updateSlot(slot: ClassSlot) {}

    override suspend fun deleteSlot(slot: ClassSlot) {}

    override suspend fun deleteSlotById(slotId: Long) {}

    override suspend fun deleteSlotsForMatter(matterId: Long) {}

    override suspend fun deleteAllMatters() {
        deleteAllMattersCalled = true
    }

    override suspend fun deleteMattersByIds(matterIds: List<Long>) {
        deleteMattersByIdsCalledWith = matterIds
    }

    val bulkUpdatedActiveStatus = mutableMapOf<List<Long>, Boolean>()

    override suspend fun updateMattersActiveStatus(matterIds: List<Long>, isActive: Boolean) {
        bulkUpdatedActiveStatus[matterIds] = isActive
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MattersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: MattersViewModelTestFakeDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = MattersViewModelTestFakeDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchQueryFiltering_filtersByNameAndCode() = runTest {
        val matters = listOf(
            MatterWithSlots(
                matter = Matter(id = 1L, name = "Sistemas Operativos", code = "SO", color = 0, isActive = true),
                slots = emptyList()
            ),
            MatterWithSlots(
                matter = Matter(id = 2L, name = "Redes de Computadores", code = "RED", color = 0, isActive = true),
                slots = emptyList()
            )
        )
        fakeDao.mattersFlow.value = matters

        val viewModel = MattersViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.filteredMatters.size)

        // Filter by code "SO"
        viewModel.onEvent(MattersUiEvent.SearchQueryChanged("SO"))
        assertEquals(1, viewModel.uiState.value.filteredMatters.size)
        assertEquals("SO", viewModel.uiState.value.filteredMatters.first().matter.code)

        // Filter by name "redes" (case insensitive)
        viewModel.onEvent(MattersUiEvent.SearchQueryChanged("redes"))
        assertEquals(1, viewModel.uiState.value.filteredMatters.size)
        assertEquals("RED", viewModel.uiState.value.filteredMatters.first().matter.code)

        // Filter non-matching string
        viewModel.onEvent(MattersUiEvent.SearchQueryChanged("Math"))
        assertEquals(0, viewModel.uiState.value.filteredMatters.size)
    }

    @Test
    fun toggleMatterActive_invokesDaoUpdate() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.ToggleMatterActive(10L, false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, fakeDao.updatedActiveStatus[10L])
    }

    @Test
    fun deleteMatter_invokesDaoDelete() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.DeleteMatter(5L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(5L), fakeDao.deletedMatterIds)
    }

    @Test
    fun nukeAllMatters_invokesDaoDeleteAll() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.NukeAllMatters)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, fakeDao.deleteAllMattersCalled)
    }

    @Test
    fun toggleMatterSelection_updatesState() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(42L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(42L), viewModel.uiState.value.selectedMatterIds)
        assertEquals(true, viewModel.uiState.value.isInSelectionMode)

        // Toggle again to remove
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(42L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedMatterIds)
        assertEquals(false, viewModel.uiState.value.isInSelectionMode)
    }

    @Test
    fun deleteSelectedMatters_invokesDaoDeleteSelected() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(10L))
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(20L))
        viewModel.onEvent(MattersUiEvent.DeleteSelectedMatters)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(10L, 20L), fakeDao.deleteMattersByIdsCalledWith)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedMatterIds)
    }

    @Test
    fun enterSelectionMode_forcesSelectionMode() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.EnterSelectionMode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isSelectionModeForced)
        assertEquals(true, viewModel.uiState.value.isInSelectionMode)
    }

    @Test
    fun toggleGroupSelection_updatesSelectedIds() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        val ids = listOf(1L, 2L, 3L)

        // Select all
        viewModel.onEvent(MattersUiEvent.ToggleGroupSelection(ids))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedMatterIds)

        // Toggle again (deselect all, since all are selected)
        viewModel.onEvent(MattersUiEvent.ToggleGroupSelection(ids))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedMatterIds)
    }

    @Test
    fun disableSelectedMatters_updatesDaoAndClearsSelection() = runTest {
        val viewModel = MattersViewModel(fakeDao)
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(10L))
        viewModel.onEvent(MattersUiEvent.ToggleMatterSelection(20L))
        viewModel.onEvent(MattersUiEvent.DisableSelectedMatters)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, fakeDao.bulkUpdatedActiveStatus[listOf(10L, 20L)] == false)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedMatterIds)
        assertEquals(false, viewModel.uiState.value.isSelectionModeForced)
    }
}
