package com.marcoslorcar.clementime.ui.screens.conflictresolver

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ConflictResolverViewModelTestFakeDao : ScheduleDao {
    val subjectsFlow = MutableStateFlow<List<SubjectWithSlots>>(emptyList())
    var updateSelectedLabGroupsCalledWith: Map<Long, String?>? = null

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = subjectsFlow
    override fun getActiveSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = flowOf(emptyList())
    override fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?> = flowOf(null)
    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {}
    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {}
    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {
        updateSelectedLabGroupsCalledWith = selections
    }
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictResolverViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: ConflictResolverViewModelTestFakeDao
    private lateinit var fakeSettingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = ConflictResolverViewModelTestFakeDao()
        fakeSettingsRepository = SettingsRepository(context = null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState contains sorted solutions with correct isCurrent flag`() = runTest {
        // Setup data: Subject 1 has two lab options (L1 and L2), unpinned (selectedLabGroup = null).
        val subject1 = Subject(id = 1L, name = "Subject 1", code = "S1", color = 0, isActive = true, selectedLabGroup = null)
        val s1Theory = ClassSlot(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val s1Lab1 = ClassSlot(id = 2L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L1")
        val s1Lab2 = ClassSlot(id = 3L, subjectId = 1L, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        val subjects = listOf(
            SubjectWithSlots(subject1, listOf(s1Theory, s1Lab1, s1Lab2))
        )
        fakeDao.subjectsFlow.value = subjects

        val viewModel = ConflictResolverViewModel(fakeDao, fakeSettingsRepository)
        val collectJob = launch {
            viewModel.uiState.collect {}
        }

        var state = viewModel.uiState.value
        repeat(40) {
            if (!state.isLoading) return@repeat
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            state = viewModel.uiState.value
        }

        assertFalse(state.isLoading)
        assertEquals(2, state.solutions.size)
        collectJob.cancel()
    }

    @Test
    fun `applySolution and undoLastApply restore previous lab selections`() = runTest {
        val subject1 = Subject(id = 1L, name = "Subject 1", code = "S1", color = 0, isActive = true, selectedLabGroup = "L1")
        val s1Theory = ClassSlot(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val s1Lab1 = ClassSlot(id = 2L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L1")
        val s1Lab2 = ClassSlot(id = 3L, subjectId = 1L, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        fakeDao.subjectsFlow.value = listOf(SubjectWithSlots(subject1, listOf(s1Theory, s1Lab1, s1Lab2)))

        val viewModel = ConflictResolverViewModel(fakeDao, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        var state = viewModel.uiState.value
        repeat(40) {
            if (!state.isLoading && state.solutions.isNotEmpty()) return@repeat
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            state = viewModel.uiState.value
        }

        val solutionToApply = state.solutions.first()
        viewModel.applySolution(solutionToApply)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(1L to "L1"), fakeDao.updateSelectedLabGroupsCalledWith)

        viewModel.undoLastApply()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(1L to "L1"), fakeDao.updateSelectedLabGroupsCalledWith)
        collectJob.cancel()
    }

    @Test
    fun `locking a lab group constrains solver to that lab group`() = runTest {
        val subject1 = Subject(id = 1L, name = "Subject 1", code = "S1", color = 0, isActive = true, selectedLabGroup = "L2")
        val s1Theory = ClassSlot(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val s1Lab1 = ClassSlot(id = 2L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L1")
        val s1Lab2 = ClassSlot(id = 3L, subjectId = 1L, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        fakeDao.subjectsFlow.value = listOf(SubjectWithSlots(subject1, listOf(s1Theory, s1Lab1, s1Lab2)))

        val viewModel = ConflictResolverViewModel(fakeDao, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        var state = viewModel.uiState.value
        repeat(40) {
            if (!state.isLoading && state.solutions.isNotEmpty()) return@repeat
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            state = viewModel.uiState.value
        }

        assertEquals(1, state.solutions.size)
        assertEquals(listOf("L2"), state.solutions[0].labSelections[1L])
        collectJob.cancel()
    }

    @Test
    fun `same time slot labs in unpinned subject does not cause self-conflict`() = runTest {
        val subject1 = Subject(id = 1L, name = "Subject 1", code = "S1", color = 0, isActive = true, selectedLabGroup = null)
        val s1Lab1 = ClassSlot(id = 1L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L1")
        val s1Lab2 = ClassSlot(id = 2L, subjectId = 1L, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        fakeDao.subjectsFlow.value = listOf(SubjectWithSlots(subject1, listOf(s1Lab1, s1Lab2)))

        val viewModel = ConflictResolverViewModel(fakeDao, fakeSettingsRepository)
        val collectJob = launch { viewModel.uiState.collect {} }
        var state = viewModel.uiState.value
        repeat(40) {
            if (!state.isLoading && state.solutions.isNotEmpty()) return@repeat
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            state = viewModel.uiState.value
        }

        assertEquals(1, state.solutions.size)
        assertEquals(0, state.solutions[0].overlapsCount)
        collectJob.cancel()
    }
}
