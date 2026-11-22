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
}

private class FakeSettingsRepositoryForMore : SettingsRepository(context = null) {
    val uniIdFlow = MutableStateFlow("")
    val facIdFlow = MutableStateFlow("")
    val uniNameFlow = MutableStateFlow("")
    val facNameFlow = MutableStateFlow("")
    val topicPrefixFlow = MutableStateFlow("")

    override val selectedUniversityIdFlow: Flow<String> = uniIdFlow
    override val selectedFacultyIdFlow: Flow<String> = facIdFlow
    override val selectedUniversityNameFlow: Flow<String> = uniNameFlow
    override val selectedFacultyNameFlow: Flow<String> = facNameFlow
    override val selectedFcmTopicPrefixFlow: Flow<String> = topicPrefixFlow
}

private class FakeScheduleDaoForMore : ScheduleDao {
    override fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>> = flowOf(emptyList())
    override fun getAllSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> = flowOf(emptyList())
    override fun getActiveSubjectsWithSlotsBySemester(semester: Int): Flow<List<SubjectWithSlots>> = flowOf(emptyList())
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
