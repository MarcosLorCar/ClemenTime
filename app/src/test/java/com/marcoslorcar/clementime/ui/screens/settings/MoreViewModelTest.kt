package com.marcoslorcar.clementime.ui.screens.settings

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.utils.IcsExporter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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
    fun setPeriodConfig_updatesUiStateAndSubjectCountsBySemester() = runTest {
        fakeDao.subjectsFlow.value = listOf(
            SubjectWithSlots(Subject(1L, "S1", "Sub 1", 0, isActive = true, semester = 1), emptyList()),
            SubjectWithSlots(Subject(2L, "S3A", "Sub 3A", 0, isActive = true, semester = 3), emptyList()),
            SubjectWithSlots(Subject(3L, "S3B", "Sub 3B", 0, isActive = false, semester = 3), emptyList())
        )
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
        assertEquals(PeriodConfig.DEFAULT, viewModel.uiState.value.periodConfig)
        assertEquals(mapOf(1 to 1, 3 to 2), viewModel.uiState.value.subjectCountsBySemester)

        viewModel.setPeriodConfig(PeriodConfig(PeriodType.TRIMESTERS, 3))
        advanceUntilIdle()
        assertEquals(PeriodConfig(PeriodType.TRIMESTERS, 3), viewModel.uiState.value.periodConfig)

        // Lowering back to 2 clamps currentSemester without deleting period 3 subjects
        fakeSettings.semesterFlow.value = 3
        viewModel.setPeriodConfig(PeriodConfig(PeriodType.SEMESTERS, 2))
        advanceUntilIdle()
        assertEquals(2, fakeSettings.semesterFlow.value)
        assertEquals(2, viewModel.uiState.value.subjectCountsBySemester[3])

        collectJob.cancel()
    }

    @Test
    fun buildIcsExportData_supports1ToNBoundaries() = runTest {
        val slot1 = ClassSlot(1L, 1L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val slot2 = ClassSlot(2L, 2L, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), entryType = EntryType.THEORY)
        val slot3 = ClassSlot(3L, 3L, DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), LocalTime.of(12, 0), entryType = EntryType.THEORY)
        fakeDao.subjectsFlow.value = listOf(
            SubjectWithSlots(Subject(1L, "P1", "Period 1", 0, isActive = true, semester = 1), listOf(slot1)),
            SubjectWithSlots(Subject(2L, "P2", "Period 2", 0, isActive = true, semester = 2), listOf(slot2)),
            SubjectWithSlots(Subject(3L, "P3", "Period 3", 0, isActive = true, semester = 3), listOf(slot3))
        )

        val viewModel = MoreViewModel(
            settingsRepository = fakeSettings,
            scheduleDao = fakeDao,
            jsonScheduleParser = JsonScheduleParser(),
            importRepository = importRepository,
            context = null
        )

        val annual = viewModel.buildIcsExportData(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 15))
        )
        assertEquals(1, annual.size)
        assertEquals(LocalDate.of(2026, 9, 1), annual[0].startDate)
        assertEquals(LocalDate.of(2027, 6, 15), annual[0].endDate)

        val trimesters = viewModel.buildIcsExportData(
            listOf(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 3, 1),
                LocalDate.of(2027, 6, 15)
            )
        )
        assertEquals(3, trimesters.size)
        assertEquals(LocalDate.of(2026, 11, 30), trimesters[0].endDate)
        assertEquals(LocalDate.of(2026, 12, 1), trimesters[1].startDate)
        assertEquals(LocalDate.of(2027, 2, 28), trimesters[1].endDate)
        assertEquals(LocalDate.of(2027, 3, 1), trimesters[2].startDate)
        assertEquals(LocalDate.of(2027, 6, 15), trimesters[2].endDate)

        val ics = IcsExporter.generateIcsContent(trimesters)
        assertTrue(ics.contains("SUMMARY:Period 1"))
        assertTrue(ics.contains("SUMMARY:Period 2"))
        assertTrue(ics.contains("SUMMARY:Period 3"))
    }
}

private class FakeSettingsRepositoryForMore : SettingsRepository(context = null) {
    val uniIdFlow = MutableStateFlow("")
    val facIdFlow = MutableStateFlow("")
    val uniNameFlow = MutableStateFlow("")
    val facNameFlow = MutableStateFlow("")
    val topicPrefixFlow = MutableStateFlow("")
    val periodFlow = MutableStateFlow(PeriodConfig.DEFAULT)
    val semesterFlow = MutableStateFlow(1)

    override val selectedUniversityIdFlow: Flow<String> = uniIdFlow
    override val selectedFacultyIdFlow: Flow<String> = facIdFlow
    override val selectedUniversityNameFlow: Flow<String> = uniNameFlow
    override val selectedFacultyNameFlow: Flow<String> = facNameFlow
    override val selectedFcmTopicPrefixFlow: Flow<String> = topicPrefixFlow
    override val periodConfigFlow: Flow<PeriodConfig> = periodFlow
    override val currentSemesterFlow: Flow<Int> = semesterFlow

    override suspend fun setPeriodConfig(config: PeriodConfig) {
        val normalized = PeriodConfig.normalized(config.type, config.count)
        periodFlow.value = normalized
        if (semesterFlow.value > normalized.count) {
            semesterFlow.value = normalized.count
        }
    }
}

private class FakeScheduleDaoForMore : ScheduleDao {
    val subjectsFlow = MutableStateFlow<List<SubjectWithSlots>>(emptyList())

    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = subjectsFlow
    override fun getAllSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        flowOf(subjectsFlow.value.filter { it.subject.semester == semester })
    override fun getActiveSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> =
        flowOf(subjectsFlow.value.filter { it.subject.semester == semester && it.subject.isActive })
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
