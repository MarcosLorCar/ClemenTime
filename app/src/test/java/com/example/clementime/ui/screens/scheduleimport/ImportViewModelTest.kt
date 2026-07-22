package com.example.clementime.ui.screens.scheduleimport

import com.example.clementime.data.importing.model.ImportFile
import com.example.clementime.data.importing.model.JsonGroup
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.JsonYear
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.model.SelectedMatter
import com.example.clementime.data.importing.repository.ImportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

    private lateinit var mockRepository: ImportRepository

    private val matter1 = JsonMatter(code = "SO", name = "Sistemas Operativos")
    private val matter2 = JsonMatter(code = "RED", name = "Redes de Computadores")
    private val matter3 = JsonMatter(code = "ALGB", name = "Álgebra Lineal")

    private val selected1 = SelectedMatter(matter1, "1º A")
    private val selected2 = SelectedMatter(matter2, "1º A")
    private val selected3 = SelectedMatter(matter3, "1º B")

    private val matterRoot = JsonMatter(code = "ROOT", name = "Common Subject")
    private val selectedRoot = SelectedMatter(matterRoot, "General")

    private val matterYearCommon = JsonMatter(code = "YCOM", name = "Year Common")
    private val selectedYearCommon = SelectedMatter(matterYearCommon, "1º Common")

    private val sampleSchema = ScheduleJsonSchema(
        title = "Test Schema",
        matters = listOf(matterRoot),
        years = listOf(
            JsonYear(
                name = "1º",
                matters = listOf(matterYearCommon),
                groups = listOf(
                    JsonGroup(name = "A", matters = listOf(matter1, matter2)),
                    JsonGroup(name = "B", matters = listOf(matter3))
                )
            )
        )
    )

    @Before
    fun setUp() {
        mockRepository = ImportRepository(dao = FakeScheduleDaoForImport())
    }

    @Test
    fun selectAllMatters_selectsAllLevels() {
        val viewModel = ImportViewModel(mockRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedMatters = emptySet(),
            selectedFile = ImportFile("bundled", "Test Bundled", true)
        )

        viewModel.selectAllMatters()
        val currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        
        assertEquals(5, currentSelection.size)
        assertTrue(currentSelection.contains(selectedRoot))
        assertTrue(currentSelection.contains(selectedYearCommon))
        assertTrue(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))
        assertTrue(currentSelection.contains(selected3))
    }

    @Test
    fun toggleMatterSelection_togglesIndividualSelection() {
        val viewModel = ImportViewModel(mockRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedMatters = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("bundled", "Test Bundled", true)
        )

        // Deselect matter1
        viewModel.toggleMatterSelection(matter1, "1º A")
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertFalse(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))

        // Re-select matter1
        viewModel.toggleMatterSelection(matter1, "1º A")
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertTrue(currentSelection.contains(selected1))
    }

    @Test
    fun toggleSectionMatters_togglesAllMattersInSection() {
        val viewModel = ImportViewModel(mockRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedMatters = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("bundled", "Test Bundled", true)
        )

        val sectionA = listOf(selected1, selected2)

        // All in section A selected -> toggling deselects section A
        viewModel.toggleSectionMatters(sectionA)
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertFalse(currentSelection.contains(selected1))
        assertFalse(currentSelection.contains(selected2))
        assertTrue(currentSelection.contains(selected3))

        // Toggling section A again selects all in section A
        viewModel.toggleSectionMatters(sectionA)
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertTrue(currentSelection.contains(selected1))
        assertTrue(currentSelection.contains(selected2))
    }

    @Test
    fun toggleAllMatters_togglesEntireTargetCollection() {
        val viewModel = ImportViewModel(mockRepository)

        val stateField = ImportViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportUiState>
        stateFlow.value = ImportUiState.Selection(
            schema = sampleSchema,
            selectedMatters = setOf(selected1, selected2, selected3),
            selectedFile = ImportFile("bundled", "Test Bundled", true)
        )

        val allFlattened = listOf(selected1, selected2, selected3)

        // All selected -> toggle all deselects all
        viewModel.toggleAllMatters(allFlattened)
        var currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertTrue(currentSelection.isEmpty())

        // All deselected -> toggle all selects all
        viewModel.toggleAllMatters(allFlattened)
        currentSelection = (viewModel.uiState.value as ImportUiState.Selection).selectedMatters
        assertEquals(3, currentSelection.size)
    }
}

class FakeScheduleDaoForImport : com.example.clementime.data.ScheduleDao {
    override fun getAllMattersWithSlots() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.clementime.data.MatterWithSlots>())
    override fun getActiveMattersWithSlots() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.clementime.data.MatterWithSlots>())
    override fun getMatterWithSlotsById(matterId: Long) = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun updateMatterActiveStatus(matterId: Long, isActive: Boolean) {}
    override suspend fun insertMatter(matter: com.example.clementime.data.Matter): Long = 1L
    override suspend fun updateMatter(matter: com.example.clementime.data.Matter) {}
    override suspend fun deleteMatterById(matterId: Long) {}
    override suspend fun insertSlot(slot: com.example.clementime.data.ClassSlot): Long = 1L
    override suspend fun insertSlots(slots: List<com.example.clementime.data.ClassSlot>) {}
    override suspend fun updateSlot(slot: com.example.clementime.data.ClassSlot) {}
    override suspend fun deleteSlot(slot: com.example.clementime.data.ClassSlot) {}
    override suspend fun deleteSlotById(slotId: Long) {}
    override suspend fun deleteSlotsForMatter(matterId: Long) {}
    override suspend fun deleteAllMatters() {}
    override suspend fun deleteMattersByIds(matterIds: List<Long>) {}
    override suspend fun updateMattersActiveStatus(matterIds: List<Long>, isActive: Boolean) {}
}
