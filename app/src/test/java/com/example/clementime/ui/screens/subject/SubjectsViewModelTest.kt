package com.example.clementime.ui.screens.subject

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.Subject
import com.example.clementime.data.SubjectWithSlots
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

class SubjectsViewModelTestFakeDao : ScheduleDao {
    val subjectsFlow = MutableStateFlow<List<SubjectWithSlots>>(emptyList())
    val deletedSubjectIds = mutableListOf<Long>()
    val updatedActiveStatus = mutableMapOf<Long, Boolean>()
    var deleteAllSubjectsCalled = false
    var deleteSubjectsByIdsCalledWith: List<Long>? = null

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = subjectsFlow

    override fun getActiveSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = flowOf(emptyList())

    override fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?> = flowOf(null)

    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {
        updatedActiveStatus[subjectId] = isActive
    }

    override suspend fun insertSubject(subject: Subject): Long = 1L

    override suspend fun updateSubject(subject: Subject) {}

    override suspend fun deleteSubjectById(subjectId: Long) {
        deletedSubjectIds.add(subjectId)
    }

    override suspend fun insertSlot(slot: ClassSlot): Long = 1L

    override suspend fun insertSlots(slots: List<ClassSlot>) {}

    override suspend fun updateSlot(slot: ClassSlot) {}

    override suspend fun deleteSlot(slot: ClassSlot) {}

    override suspend fun deleteSlotById(slotId: Long) {}

    override suspend fun deleteSlotsForSubject(subjectId: Long) {}

    override suspend fun deleteAllSubjects() {
        deleteAllSubjectsCalled = true
    }

    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {
        deleteSubjectsByIdsCalledWith = subjectIds
    }

    val bulkUpdatedActiveStatus = mutableMapOf<List<Long>, Boolean>()

    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {
        bulkUpdatedActiveStatus[subjectIds] = isActive
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: SubjectsViewModelTestFakeDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = SubjectsViewModelTestFakeDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchQueryFiltering_filtersByNameAndCode() = runTest {
        val subjects = listOf(
            SubjectWithSlots(
                subject = Subject(id = 1L, name = "Sistemas Operativos", code = "SO", color = 0, isActive = true),
                slots = emptyList()
            ),
            SubjectWithSlots(
                subject = Subject(id = 2L, name = "Redes de Computadores", code = "RED", color = 0, isActive = true),
                slots = emptyList()
            )
        )
        fakeDao.subjectsFlow.value = subjects

        val viewModel = SubjectsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.filteredSubjects.size)

        // Filter by code "SO"
        viewModel.onEvent(SubjectsUiEvent.SearchQueryChanged("SO"))
        assertEquals(1, viewModel.uiState.value.filteredSubjects.size)
        assertEquals("SO", viewModel.uiState.value.filteredSubjects.first().subject.code)

        // Filter by name "redes" (case insensitive)
        viewModel.onEvent(SubjectsUiEvent.SearchQueryChanged("redes"))
        assertEquals(1, viewModel.uiState.value.filteredSubjects.size)
        assertEquals("RED", viewModel.uiState.value.filteredSubjects.first().subject.code)

        // Filter non-matching string
        viewModel.onEvent(SubjectsUiEvent.SearchQueryChanged("Math"))
        assertEquals(0, viewModel.uiState.value.filteredSubjects.size)
    }

    @Test
    fun toggleSubjectActive_invokesDaoUpdate() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectActive(10L, false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, fakeDao.updatedActiveStatus[10L])
    }

    @Test
    fun deleteSubject_invokesDaoDelete() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.DeleteSubject(5L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(5L), fakeDao.deletedSubjectIds)
    }

    @Test
    fun nukeAllSubjects_invokesDaoDeleteAll() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.NukeAllSubjects)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, fakeDao.deleteAllSubjectsCalled)
    }

    @Test
    fun toggleSubjectSelection_updatesState() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(42L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(42L), viewModel.uiState.value.selectedSubjectIds)
        assertEquals(true, viewModel.uiState.value.isInSelectionMode)

        // Toggle again to remove
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(42L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedSubjectIds)
        assertEquals(false, viewModel.uiState.value.isInSelectionMode)
    }

    @Test
    fun deleteSelectedSubjects_invokesDaoDeleteSelected() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(10L))
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(20L))
        viewModel.onEvent(SubjectsUiEvent.DeleteSelectedSubjects)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(10L, 20L), fakeDao.deleteSubjectsByIdsCalledWith)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedSubjectIds)
    }

    @Test
    fun enterSelectionMode_forcesSelectionMode() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.EnterSelectionMode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isSelectionModeForced)
        assertEquals(true, viewModel.uiState.value.isInSelectionMode)
    }

    @Test
    fun toggleGroupSelection_updatesSelectedIds() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        val ids = listOf(1L, 2L, 3L)

        // Select all
        viewModel.onEvent(SubjectsUiEvent.ToggleGroupSelection(ids))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedSubjectIds)

        // Toggle again (deselect all, since all are selected)
        viewModel.onEvent(SubjectsUiEvent.ToggleGroupSelection(ids))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedSubjectIds)
    }

    @Test
    fun disableSelectedSubjects_updatesDaoAndClearsSelection() = runTest {
        val viewModel = SubjectsViewModel(fakeDao)
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(10L))
        viewModel.onEvent(SubjectsUiEvent.ToggleSubjectSelection(20L))
        viewModel.onEvent(SubjectsUiEvent.DisableSelectedSubjects)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, fakeDao.bulkUpdatedActiveStatus[listOf(10L, 20L)] == false)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedSubjectIds)
        assertEquals(false, viewModel.uiState.value.isSelectionModeForced)
    }
}
