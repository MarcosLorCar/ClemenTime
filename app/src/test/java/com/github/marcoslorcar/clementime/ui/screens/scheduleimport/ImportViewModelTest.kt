package com.github.marcoslorcar.clementime.ui.screens.scheduleimport

import com.github.marcoslorcar.clementime.data.importing.model.ImportFile
import com.github.marcoslorcar.clementime.data.importing.model.JsonGroup
import com.github.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.github.marcoslorcar.clementime.data.importing.model.JsonYear
import com.github.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.github.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.github.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

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

    private val fakeSettingsRepository = object : com.github.marcoslorcar.clementime.data.SettingsRepository(context = null) {}

    @Before
    fun setUp() {
        mockRepository = ImportRepository(dao = FakeScheduleDaoForImport())
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
            selectedFile = ImportFile("bundled", "Test Bundled", true)
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
            selectedFile = ImportFile("bundled", "Test Bundled", true)
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
            selectedFile = ImportFile("bundled", "Test Bundled", true)
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
            selectedFile = ImportFile("bundled", "Test Bundled", true)
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
        val theorySlot = com.github.marcoslorcar.clementime.data.importing.model.JsonTimeSlot(
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
            selectedFile = ImportFile("bundled", "Test", true),
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
        val theorySlot = com.github.marcoslorcar.clementime.data.importing.model.JsonTimeSlot(
            dayOfWeek = "MONDAY", startTime = "09:00", endTime = "11:00", entryType = "THEORY"
        )
        val s1 = JsonSubject(code = "S1", name = "Sub 1", theorySlots = listOf(theorySlot))
        
        val existingSubject = com.github.marcoslorcar.clementime.data.Subject(
            id = 100L, code = "S2", name = "Existing Active Sub", color = 0, isActive = true
        )
        val existingSlot = com.github.marcoslorcar.clementime.data.ClassSlot(
            id = 200L, subjectId = 100L, dayOfWeek = java.time.DayOfWeek.MONDAY,
            startTime = java.time.LocalTime.of(9, 0), endTime = java.time.LocalTime.of(11, 0),
            entryType = com.github.marcoslorcar.clementime.data.EntryType.THEORY
        )
        val existing = listOf(com.github.marcoslorcar.clementime.data.SubjectWithSlots(existingSubject, listOf(existingSlot)))

        val viewModel = ImportViewModel(mockRepository, JsonScheduleParser(), null, fakeSettingsRepository)
        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = ScheduleJsonSchema(subjects = listOf(s1)),
            selectedSubjects = emptySet(),
            selectedFile = ImportFile("bundled", "Test", true),
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
}

class FakeScheduleDaoForImport : com.github.marcoslorcar.clementime.data.ScheduleDao {
    override fun getAllSubjectsWithSlots() = kotlinx.coroutines.flow.flowOf(emptyList<com.github.marcoslorcar.clementime.data.SubjectWithSlots>())
    override fun getActiveSubjectsWithSlots() = kotlinx.coroutines.flow.flowOf(emptyList<com.github.marcoslorcar.clementime.data.SubjectWithSlots>())
    override fun getSubjectWithSlotsById(subjectId: Long) = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean) {}
    override suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?) {}
    override suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {}
    override suspend fun insertSubject(subject: com.github.marcoslorcar.clementime.data.Subject): Long = 1L
    override suspend fun updateSubject(subject: com.github.marcoslorcar.clementime.data.Subject) {}
    override suspend fun deleteSubjectById(subjectId: Long) {}
    override suspend fun insertSlot(slot: com.github.marcoslorcar.clementime.data.ClassSlot): Long = 1L
    override suspend fun insertSlots(slots: List<com.github.marcoslorcar.clementime.data.ClassSlot>) {}
    override suspend fun updateSlot(slot: com.github.marcoslorcar.clementime.data.ClassSlot) {}
    override suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean) {}
    override suspend fun deleteSlot(slot: com.github.marcoslorcar.clementime.data.ClassSlot) {}
    override suspend fun deleteSlotById(slotId: Long) {}
    override suspend fun deleteSlotsForSubject(subjectId: Long) {}
    override suspend fun deleteAllSubjects() {}
    override suspend fun deleteSubjectsByIds(subjectIds: List<Long>) {}
    override suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean) {}
}
