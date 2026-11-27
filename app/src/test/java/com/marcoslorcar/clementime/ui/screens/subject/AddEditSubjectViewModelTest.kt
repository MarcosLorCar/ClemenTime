package com.marcoslorcar.clementime.ui.screens.subject

import androidx.lifecycle.SavedStateHandle
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.testing.FakeScheduleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditSubjectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeScheduleDao
    private lateinit var fakeSettingsRepository: com.marcoslorcar.clementime.data.SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeScheduleDao()
        fakeSettingsRepository = com.marcoslorcar.clementime.data.SettingsRepository(context = null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addSlot_initializesUnsetTimesAndAutofillsDetails() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.addSlot()
        val slot1 = viewModel.uiState.value.slots.first()
        assertNull(slot1.startTime)
        assertNull(slot1.endTime)
    }

    @Test
    fun addSlot_autofillsFromPreviousSlot() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

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
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

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
    fun saveSubject_persistsIgnoredStatus() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)
        
        viewModel.updateCode("TEST")
        viewModel.updateName("Test Subject")
        viewModel.addSlot()
        
        val slot = viewModel.uiState.value.slots.first().copy(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            isIgnored = true
        )
        viewModel.updateSlot(0, slot)
        
        viewModel.saveSubject()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(true, fakeDao.slots.first().isIgnored)
    }

    @Test
    fun updateActive_inViewMode_autoSaves() {
        val subject = Subject(id = 1L, code = "TEST", name = "Test Subject", color = 0, isActive = true)
        fakeDao.subjects.add(subject)

        val savedStateHandle = SavedStateHandle(mapOf("subjectId" to 1L))
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isEditMode)

        viewModel.updateActive(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, fakeDao.subjects.first().isActive)
        assertEquals(false, viewModel.uiState.value.isSaved)
    }

    @Test
    fun saveSubjectWithoutExit_savesSubjectWithoutSettingIsSaved() {
        val subject = Subject(id = 1L, code = "TEST", name = "Test Subject", color = 0, isActive = true)
        fakeDao.subjects.add(subject)

        val savedStateHandle = SavedStateHandle(mapOf("subjectId" to 1L))
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateNotesText("New note from sheet")
        viewModel.saveSubjectWithoutExit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("New note from sheet", fakeDao.subjects.first().notes)
        assertEquals(false, viewModel.uiState.value.isSaved)
    }

    @Test
    fun addAttachedFile_addsFileToUiState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.addAttachedFile(
            name = "notes.pdf",
            fileType = "PDF",
            uriString = "/path/to/notes.pdf",
            fileSizeBytes = 1024L
        )

        val files = viewModel.uiState.value.attachedFiles
        assertEquals(1, files.size)
        assertEquals("notes.pdf", files.first().name)
        assertEquals("PDF", files.first().fileType)
        assertEquals(1024L, files.first().fileSizeBytes)
    }

    @Test
    fun removeAttachedFile_removesFileFromUiState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.addAttachedFile("notes.pdf", "PDF", "/path/to/notes.pdf")
        val fileId = viewModel.uiState.value.attachedFiles.first().id

        viewModel.removeAttachedFile(fileId)
        assertEquals(0, viewModel.uiState.value.attachedFiles.size)
    }

    @Test
    fun renameAttachedFile_updatesFileNameInUiState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.addAttachedFile("old_name.pdf", "PDF", "/path/to/old_name.pdf")
        val fileId = viewModel.uiState.value.attachedFiles.first().id

        viewModel.renameAttachedFile(fileId, "new_renamed.pdf")
        assertEquals("new_renamed.pdf", viewModel.uiState.value.attachedFiles.first().name)
    }

    @Test
    fun dismissDeleteOriginalPrompt_clearsPromptState() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.dismissDeleteOriginalPrompt()
        assertNull(viewModel.uiState.value.deleteOriginalPrompt)
    }

    @Test
    fun confirmDeleteOriginal_whenPromptNull_doesNothing() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = AddEditSubjectViewModel(savedStateHandle, fakeDao, fakeSettingsRepository)

        viewModel.confirmDeleteOriginal()
        assertNull(viewModel.uiState.value.deleteOriginalPrompt)
    }
}
