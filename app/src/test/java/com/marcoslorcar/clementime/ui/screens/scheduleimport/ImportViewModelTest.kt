package com.marcoslorcar.clementime.ui.screens.scheduleimport

import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.ImportSourceType
import com.marcoslorcar.clementime.data.importing.model.JsonGroup
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.JsonYear
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: ImportRepository

    private val subject1 = JsonSubject(code = "SO", name = "Sistemas Operativos")
    private val subject2 = JsonSubject(code = "RED", name = "Redes de Computadores")
    private val subject3 = JsonSubject(code = "ALGB", name = "Álgebra Lineal")

    private val selected1 = SelectedSubject(subject1, "1º A")
    private val selected2 = SelectedSubject(subject2, "1º A")
    private val selected3 = SelectedSubject(subject3, "1º B")

    private val subjectRoot = JsonSubject(code = "ROOT", name = "Common Subject")
    private val selectedRoot = SelectedSubject(subjectRoot, "General")

    private val subjectYearCommon = JsonSubject(code = "YCOM", name = "Year Common")
    private val selectedYearCommon = SelectedSubject(subjectYearCommon, "1º Common")

    private val sampleSchema = ScheduleJsonSchema(
        title = "Test Schema",
        subjects = listOf(subjectRoot),
        years = listOf(
            JsonYear(
                name = "1º",
                subjects = listOf(subjectYearCommon),
                groups = listOf(
                    JsonGroup(name = "A", subjects = listOf(subject1, subject2)),
                    JsonGroup(name = "B", subjects = listOf(subject3))
                )
            )
        )
    )

    private val fakeSettingsRepository = object : com.marcoslorcar.clementime.data.SettingsRepository(context = null) {}

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = ImportRepository(dao = FakeScheduleDaoForImport())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectAllSubjects_selectsAllLevels() {
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedSubjects = emptySet(),
            selectedFile = ImportFile("test_file", "Test Schedule")
        )

        viewModel.selectAllSubjects()
        val currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        
        assertEquals(5, currentSelection.size)
        assertTrue(currentSelection.contains(selectedRoot))
        assertTrue(currentSelection.contains(selectedYearCommon))
        assertTrue(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))
        assertTrue(currentSelection.contains(selected3))
    }

    @Test
    fun toggleSubjectSelection_togglesIndividualSelection() {
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedSubjects = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("test_file", "Test Schedule")
        )

        // Deselect subject1
        viewModel.toggleSubjectSelection(subject1, "1º A")
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertFalse(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))

        // Re-select subject1
        viewModel.toggleSubjectSelection(subject1, "1º A")
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertTrue(currentSelection.contains(selected1))
    }

    @Test
    fun toggleSectionSubjects_togglesAllSubjectsInSection() {
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedSubjects = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("test_file", "Test Schedule")
        )

        val sectionA = listOf(selected1, selected2)

        // All in section A selected -> toggling deselects section A
        viewModel.toggleSectionSubjects(sectionA)
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertFalse(currentSelection.contains(selected1))
        assertFalse(currentSelection.contains(selected2))
        assertTrue(currentSelection.contains(selected3))

        // Toggling section A again selects all in section A
        viewModel.toggleSectionSubjects(sectionA)
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertTrue(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))
    }

    @Test
    fun toggleAllSubjects_togglesEntireTargetCollection() {
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedSubjects = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("test_file", "Test Schedule")
        )

        val allFlattened = listOf(selected1, selected2, selected3)

        // All selected -> toggle all deselects all
        viewModel.toggleAllSubjects(allFlattened)
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertTrue(currentSelection.isEmpty())

        // All deselected -> toggle all selects all
        viewModel.toggleAllSubjects(allFlattened)
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedSubjects
        assertEquals(3, currentSelection.size)
    }

    @Test
    fun conflictCheck_detectsTheoryOverlap() {
        val theorySlot = com.marcoslorcar.clementime.data.importing.model.JsonTimeSlot(
            dayOfWeek = "MONDAY", startTime = "09:00", endTime = "11:00", entryType = "THEORY"
        )
        val s1 = JsonSubject(code = "S1", name = "Sub 1", theorySlots = listOf(theorySlot))
        val s2 = JsonSubject(code = "S2", name = "Sub 2", theorySlots = listOf(theorySlot))
        
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)
        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = ScheduleJsonSchema(subjects = listOf(s1, s2)),
            selectedSubjects = emptySet(),
            selectedFile = ImportFile("test_file", "Test"),
            conflictStatus = ConflictStatus.None
        )

        viewModel.toggleSubjectSelection(s1, "General")
        assertTrue(viewModel.uiState.value is ImportUiState.Selection)
        assertEquals(ConflictStatus.Valid, (viewModel.uiState.value as ImportUiState.Selection).conflictStatus)

        viewModel.toggleSubjectSelection(s2, "General")
        val state = viewModel.uiState.value as ImportUiState.Selection
        assertTrue(state.conflictStatus is ConflictStatus.Conflict)
        val conflict = state.conflictStatus as ConflictStatus.Conflict
        assertTrue(conflict.detail.theoryOverlaps.isNotEmpty())
    }

    @Test
    fun conflictCheck_detectsConflictWithExistingActiveSubject() {
        val theorySlot = com.marcoslorcar.clementime.data.importing.model.JsonTimeSlot(
            dayOfWeek = "MONDAY", startTime = "09:00", endTime = "11:00", entryType = "THEORY"
        )
        val s1 = JsonSubject(code = "S1", name = "Sub 1", theorySlots = listOf(theorySlot))
        
        val existingSubject = com.marcoslorcar.clementime.data.Subject(
            id = 100L, code = "S2", name = "Existing Active Sub", color = 0, isActive = true
        )
        val existingSlot = com.marcoslorcar.clementime.data.ClassSlot(
            id = 200L, subjectId = 100L, dayOfWeek = java.time.DayOfWeek.MONDAY,
            startTime = java.time.LocalTime.of(9, 0), endTime = java.time.LocalTime.of(11, 0),
            entryType = com.marcoslorcar.clementime.data.EntryType.THEORY
        )
        val existing = listOf(com.marcoslorcar.clementime.data.SubjectWithSlots(existingSubject, listOf(existingSlot)))

        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)
        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = ScheduleJsonSchema(subjects = listOf(s1)),
            selectedSubjects = emptySet(),
            selectedFile = ImportFile("test_file", "Test"),
            conflictStatus = ConflictStatus.None,
            existingSubjects = existing
        )

        viewModel.toggleSubjectSelection(s1, "General")
        val state = viewModel.uiState.value as ImportUiState.Selection
        assertTrue(state.conflictStatus is ConflictStatus.Conflict)
        val conflict = state.conflictStatus as ConflictStatus.Conflict
        assertTrue(conflict.detail.theoryOverlaps.isNotEmpty())
        assertEquals("S1", conflict.detail.theoryOverlaps[0].subject1Code)
        assertEquals("S2", conflict.detail.theoryOverlaps[0].subject2Code)
    }

    @Test
    fun confirmImport_remoteFileLinksFacultyInSettings() = runTest {
        val recordingSettings = RecordingSettingsRepositoryForImport()
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, recordingSettings)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema.copy(semester = 1),
            selectedSubjects = setOf(selected1),
            selectedFile = ImportFile(
                id = "uclm_esi_1C",
                title = "1er Cuatrimestre",
                sourceType = ImportSourceType.REMOTE,
                remotePath = "https://example.com/1C.json",
                remoteHash = "hash_1c",
                universityId = "uclm",
                universityName = "UCLM",
                facultyId = "esi",
                facultyName = "Escuela Superior de Informática (ESI)",
                fcmTopicPrefix = "uclm_esi",
                termId = "1C"
            )
        )

        viewModel.confirmImport()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ImportUiState.Success)
        assertEquals(0, recordingSettings.resetHashesCount)
        assertEquals(1, recordingSettings.setFacultyCalls.size)
        val call = recordingSettings.setFacultyCalls.single()
        assertEquals("uclm", call.universityId)
        assertEquals("esi", call.facultyId)
        assertEquals("UCLM", call.universityName)
        assertEquals("Escuela Superior de Informática (ESI)", call.facultyName)
        assertEquals("uclm_esi", call.fcmTopicPrefix)
    }

    @Test
    fun confirmImport_switchingFacultyResetsHashesBeforeSavingNewFaculty() = runTest {
        val recordingSettings = RecordingSettingsRepositoryForImport(
            initialUniversityId = "uclm",
            initialFacultyId = "esi"
        )
        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, recordingSettings)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema.copy(semester = 1),
            selectedSubjects = setOf(selected1),
            selectedFile = ImportFile(
                id = "uclm_eii_1C",
                title = "1er Cuatrimestre",
                sourceType = ImportSourceType.REMOTE,
                remotePath = "https://example.com/eii/1C.json",
                remoteHash = "hash_eii_1c",
                universityId = "uclm",
                universityName = "UCLM",
                facultyId = "eii",
                facultyName = "EII Toledo",
                fcmTopicPrefix = "uclm_eii",
                termId = "1C"
            )
        )

        viewModel.confirmImport()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ImportUiState.Success)
        assertEquals(1, recordingSettings.resetHashesCount)
        val resetIdx = recordingSettings.events.indexOf("resetHashes")
        val setFacultyIdx = recordingSettings.events.indexOf("setFaculty:uclm:eii")
        val setHashIdx = recordingSettings.events.indexOf("setHash:1:hash_eii_1c")
        assertTrue(resetIdx >= 0)
        assertTrue(resetIdx < setFacultyIdx)
        assertTrue(resetIdx < setHashIdx)
    }
}

private class RecordingSettingsRepositoryForImport(
    initialUniversityId: String = "",
    initialFacultyId: String = ""
) : com.marcoslorcar.clementime.data.SettingsRepository(context = null) {
    data class FacultyCall(
        val universityId: String,
        val facultyId: String,
        val universityName: String?,
        val facultyName: String?,
        val fcmTopicPrefix: String?
    )

    private val uniIdFlow = MutableStateFlow(initialUniversityId)
    private val facIdFlow = MutableStateFlow(initialFacultyId)

    override val selectedUniversityIdFlow: Flow<String> = uniIdFlow
    override val selectedFacultyIdFlow: Flow<String> = facIdFlow

    val events = mutableListOf<String>()
    val setFacultyCalls = mutableListOf<FacultyCall>()
    var resetHashesCount = 0

    override suspend fun resetScheduleHashes() {
        resetHashesCount++
        events.add("resetHashes")
    }

    override suspend fun setSelectedFaculty(
        universityId: String,
        facultyId: String,
        universityName: String?,
        facultyName: String?,
        fcmTopicPrefix: String?
    ) {
        uniIdFlow.value = universityId
        facIdFlow.value = facultyId
        setFacultyCalls.add(FacultyCall(universityId, facultyId, universityName, facultyName, fcmTopicPrefix))
        events.add("setFaculty:$universityId:$facultyId")
    }

    override suspend fun setLastKnownScheduleHash(semester: Int, hash: String) {
        events.add("setHash:$semester:$hash")
    }
}

class FakeScheduleDaoForImport : com.marcoslorcar.clementime.data.ScheduleDao {
    override fun getAllSubjectsWithSlots() = kotlinx.coroutines.flow.flowOf(emptyList<com.marcoslorcar.clementime.data.SubjectWithSlots>())
    override fun getAllSubjectsWithSlotsBySemester(semester: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.marcoslorcar.clementime.data.SubjectWithSlots>())
    override fun getActiveSubjectsWithSlotsBySemester(semester: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.marcoslorcar.clementime.data.SubjectWithSlots>())
    override fun getSubjectWithSlotsById(subjectId: Long) = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {}
    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {}
    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {}
    override suspend fun insertSubject(subject: com.marcoslorcar.clementime.data.Subject): Long = 1L
    override suspend fun updateSubject(subject: com.marcoslorcar.clementime.data.Subject) {}
    override suspend fun deleteSubjectById(subjectId: Long) {}
    override suspend fun insertSlot(slot: com.marcoslorcar.clementime.data.ClassSlot): Long = 1L
    override suspend fun insertSlots(slots: List<com.marcoslorcar.clementime.data.ClassSlot>) {}
    override suspend fun updateSlot(slot: com.marcoslorcar.clementime.data.ClassSlot) {}
    override suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean) {}
    override suspend fun deleteSlot(slot: com.marcoslorcar.clementime.data.ClassSlot) {}
    override suspend fun deleteSlotById(slotId: Long) {}
    override suspend fun deleteSlotsForSubject(subjectId: Long) {}
    override suspend fun deleteAllSubjects() {}
    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {}
    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {}
}
