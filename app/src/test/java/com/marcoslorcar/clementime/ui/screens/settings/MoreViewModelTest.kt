package com.marcoslorcar.clementime.ui.screens.settings

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettings: FakeSettingsRepositoryForMore
    private lateinit var fakeDao: FakeScheduleDaoForMore
    private lateinit var importRepository: ImportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeSettingsRepositoryForMore()
        fakeDao = FakeScheduleDaoForMore()
        importRepository = ImportRepository(dao = fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun linkedFacultyLabel_isEmptyWhenUnlinkedAndFormattedWhenLinked() = runTest {
        val viewModel = MoreViewModel(
            settingsRepository = fakeSettings,
            scheduleDao = fakeDao,
            jsonScheduleParser = JsonScheduleParser(),
            importRepository = importRepository,
            context = null
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.selectedUniversityId)
        assertEquals("", viewModel.uiState.value.selectedFacultyId)
        assertEquals("", viewModel.uiState.value.linkedFacultyLabel)

        fakeSettings.uniIdFlow.value = "uclm"
        fakeSettings.facIdFlow.value = "esi"
        fakeSettings.uniNameFlow.value = "UCLM"
        fakeSettings.facNameFlow.value = "Escuela Superior de Informática (ESI)"

        advanceUntilIdle()
        assertEquals("uclm", viewModel.uiState.value.selectedUniversityId)
        assertEquals("esi", viewModel.uiState.value.selectedFacultyId)
        assertEquals(
            "UCLM · Escuela Superior de Informática (ESI)",
            viewModel.uiState.value.linkedFacultyLabel
        )

        collectJob.cancel()
    }

    @Test
    fun checkScheduleUpdatesNow_invokesNoFacultyLinkedWhenUnlinked() = runTest {
        val viewModel = MoreViewModel(
            settingsRepository = fakeSettings,
            scheduleDao = fakeDao,
            jsonScheduleParser = JsonScheduleParser(),
            importRepository = importRepository,
            context = null
        )

        var noFacultyCalled = false
        var noUpdatesCalled = false

        viewModel.checkScheduleUpdatesNow(
            onNoUpdatesFound = { noUpdatesCalled = true },
            onNoFacultyLinked = { noFacultyCalled = true }
        )
        advanceUntilIdle()

        assertTrue(noFacultyCalled)
        assertFalse(noUpdatesCalled)
        assertFalse(viewModel.uiState.value.isCheckingUpdates)
    }

    @Test
    fun setSemestersPerYear_updatesCountAndClampsCurrentSemesterWhenHigher() = runTest {
        fakeSettings.currentSemFlow.value = 4
        fakeSettings.semestersPerYearState.value = 4

        val viewModel = MoreViewModel(
            settingsRepository = fakeSettings,
            scheduleDao = fakeDao,
            jsonScheduleParser = JsonScheduleParser(),
            importRepository = importRepository,
            context = null
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.semestersPerYear)
        assertEquals(4, viewModel.uiState.value.currentSemester)

        viewModel.setSemestersPerYear(2)
        advanceUntilIdle()

        assertEquals(2, fakeSettings.semestersPerYearState.value)
        assertEquals(2, fakeSettings.currentSemFlow.value)
        assertEquals(2, viewModel.uiState.value.semestersPerYear)
        assertEquals(2, viewModel.uiState.value.currentSemester)

        collectJob.cancel()
    }

    @Test
    fun populatedSemesters_andBuildIcsExportData_supportsActiveOnlyAndAllPopulatedSemesters() = runTest {
        val slot = ClassSlot(
            id = 1L,
            subjectId = 1L,
            dayOfWeek = java.time.DayOfWeek.MONDAY,
            startTime = java.time.LocalTime.of(9, 0),
            endTime = java.time.LocalTime.of(10, 30)
        )
        val s1 = SubjectWithSlots(
            Subject(id = 1L, code = "S1", name = "Sem 1 Active", color = 0, isActive = true, semester = 1),
            listOf(slot.copy(id = 1L, subjectId = 1L))
        )
        val s2Inactive = SubjectWithSlots(
            Subject(id = 2L, code = "S2", name = "Sem 2 Inactive", color = 0, isActive = false, semester = 2),
            listOf(slot.copy(id = 2L, subjectId = 2L))
        )
        val s3 = SubjectWithSlots(
            Subject(id = 3L, code = "S3", name = "Sem 3 Active", color = 0, isActive = true, semester = 3),
            listOf(slot.copy(id = 3L, subjectId = 3L))
        )
        fakeDao.subjectsFlow.value = listOf(s1, s2Inactive, s3)

        val viewModel = MoreViewModel(
            settingsRepository = fakeSettings,
            scheduleDao = fakeDao,
            jsonScheduleParser = JsonScheduleParser(),
            importRepository = importRepository,
            context = null
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(listOf(1, 3), viewModel.uiState.value.populatedSemesters)

        val activeOnlyData = viewModel.buildIcsExportData(
            listOf(SemesterDateRange(1, java.time.LocalDate.of(2026, 9, 7), java.time.LocalDate.of(2026, 12, 18)))
        )
        assertEquals(1, activeOnlyData.size)
        assertEquals("S1", activeOnlyData.single().subjectsWithSlots.single().subject.code)

        val allPopulatedData = viewModel.buildIcsExportData(
            listOf(
                SemesterDateRange(1, java.time.LocalDate.of(2026, 9, 7), java.time.LocalDate.of(2026, 12, 18)),
                SemesterDateRange(3, java.time.LocalDate.of(2027, 4, 5), java.time.LocalDate.of(2027, 6, 25))
            )
        )
        assertEquals(2, allPopulatedData.size)
        assertEquals("S1", allPopulatedData[0].subjectsWithSlots.single().subject.code)
        assertEquals("S3", allPopulatedData[1].subjectsWithSlots.single().subject.code)

        collectJob.cancel()
    }
}

private class FakeSettingsRepositoryForMore : SettingsRepository(context = null) {
    val uniIdFlow = MutableStateFlow("")
    val facIdFlow = MutableStateFlow("")
    val uniNameFlow = MutableStateFlow("")
    val facNameFlow = MutableStateFlow("")
    val topicPrefixFlow = MutableStateFlow("")
    val semestersPerYearState = MutableStateFlow(DEFAULT_SEMESTERS_PER_YEAR)
    val currentSemFlow = MutableStateFlow(1)

    override val selectedUniversityIdFlow: Flow<String> = uniIdFlow
    override val selectedFacultyIdFlow: Flow<String> = facIdFlow
    override val selectedUniversityNameFlow: Flow<String> = uniNameFlow
    override val selectedFacultyNameFlow: Flow<String> = facNameFlow
    override val selectedFcmTopicPrefixFlow: Flow<String> = topicPrefixFlow
    override val semestersPerYearFlow: Flow<Int> = semestersPerYearState
    override val currentSemesterFlow: Flow<Int> = currentSemFlow

    override suspend fun setSemestersPerYear(count: Int) {
        semestersPerYearState.value = count
    }

    override suspend fun setCurrentSemester(semester: Int) {
        currentSemFlow.value = semester
    }
}

private class FakeScheduleDaoForMore : ScheduleDao {
    val subjectsFlow = MutableStateFlow<List<SubjectWithSlots>>(emptyList())

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = subjectsFlow
    override fun getAllSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        MutableStateFlow(subjectsFlow.value.filter { it.subject.semester == semester })
    override fun getActiveSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        MutableStateFlow(subjectsFlow.value.filter { it.subject.isActive && it.subject.semester == semester })
    override fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?> = flowOf(null)
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
}
