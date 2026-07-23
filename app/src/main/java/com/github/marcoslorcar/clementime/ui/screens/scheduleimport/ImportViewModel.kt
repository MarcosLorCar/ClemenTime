package com.github.marcoslorcar.clementime.ui.screens.scheduleimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import com.github.marcoslorcar.clementime.data.importing.model.ImportFile
import com.github.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.github.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.github.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.github.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictDetail
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model.TheoryOverlap
import com.github.marcoslorcar.clementime.utils.ConflictSolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ImportUiState {
    object LoadingLibrary : ImportUiState
    data class Library(
        val files: List<ImportFile>,
        val error: String? = null
    ) : ImportUiState
    object Parsing : ImportUiState
    data class Selection(
        val schema: ScheduleJsonSchema,
        val selectedSubjects: Set<SelectedSubject>,
        val searchQuery: String = "",
        val selectedFile: ImportFile,
        val conflictStatus: ConflictStatus = ConflictStatus.None
    ) : ImportUiState
    object Importing : ImportUiState
    object Success : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ImportRepository,
    private val parser: JsonScheduleParser
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.LoadingLibrary)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun loadLibrary(context: Context) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.LoadingLibrary
            try {
                val files = repository.listAvailableImportFiles(context)
                _uiState.value = ImportUiState.Library(files)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Library(emptyList(), "Failed to load library: ${e.localizedMessage}")
            }
        }
    }

    fun loadFile(context: Context, file: ImportFile) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            try {
                val jsonString = if (file.isBundled) {
                    withContext(Dispatchers.IO) {
                        context.assets.open("primer_cuatrimestre.json").use { stream ->
                            stream.bufferedReader().readText()
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        File(file.fileUri!!).readText()
                    }
                }

                val result = repository.parseJsonString(jsonString)
                result.fold(
                    onSuccess = { schema ->
                        _uiState.value = ImportUiState.Selection(
                            schema = schema,
                            selectedSubjects = emptySet(),
                            selectedFile = file,
                            conflictStatus = ConflictStatus.None
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = ImportUiState.Error("Invalid JSON format: ${error.localizedMessage}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    fun selectAndSaveNewFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            val result = repository.saveJsonFile(context, uri)
            result.fold(
                onSuccess = { importFile ->
                    loadFile(context, importFile)
                },
                onFailure = { error ->
                    _uiState.value = ImportUiState.Error("Failed to save and parse file: ${error.localizedMessage}")
                }
            )
        }
    }

    fun deleteFile(context: Context, file: ImportFile) {
        viewModelScope.launch {
            if (!file.isBundled) {
                repository.deleteCustomImportFile(context, file.id)
                loadLibrary(context)
            }
        }
    }

    fun resetToLibrary(context: Context) {
        loadLibrary(context)
    }

    fun toggleSubjectSelection(subject: JsonSubject, groupName: String) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val selectedSubject = SelectedSubject(subject, groupName)
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            if (updatedSelection.contains(selectedSubject)) {
                updatedSelection.remove(selectedSubject)
            } else {
                updatedSelection.add(selectedSubject)
            }
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection)
            )
        }
    }

    fun toggleAllSubjects(targetSubjects: Collection<SelectedSubject>) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            val allSelected = targetSubjects.isNotEmpty() && targetSubjects.all { updatedSelection.contains(it) }
            if (allSelected) {
                updatedSelection.removeAll(targetSubjects.toSet())
            } else {
                updatedSelection.addAll(targetSubjects)
            }
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection)
            )
        }
    }

    fun toggleSectionSubjects(sectionSubjects: Collection<SelectedSubject>) {
        toggleAllSubjects(sectionSubjects)
    }

    fun deselectAll() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            _uiState.value = currentState.copy(
                selectedSubjects = emptySet(),
                conflictStatus = ConflictStatus.None
            )
        }
    }

    fun selectAllSubjects(subjects: Collection<SelectedSubject>? = null) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val toSelect = subjects ?: run {
                val fromRoot = currentState.schema.subjects.map { SelectedSubject(it, "General") }
                val fromYears = currentState.schema.years.flatMap { year ->
                    val yearCommon = year.subjects.map { SelectedSubject(it, "${year.name} Common") }
                    val fromGroups = year.groups.flatMap { group ->
                        group.subjects.map { SelectedSubject(it, "${year.name} ${group.name}") }
                    }
                    yearCommon + fromGroups
                }
                fromRoot + fromYears
            }
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            updatedSelection.addAll(toSelect)
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection)
            )
        }
    }

    private fun calculateConflictStatus(selected: Set<SelectedSubject>): ConflictStatus {
        if (selected.isEmpty()) return ConflictStatus.None

        var slotIdCounter = 1L
        val subjectsWithSlots = selected.map { selectedSubject ->
            val jsonSubject = selectedSubject.subject
            val subjectId = jsonSubject.code.hashCode().toLong()
            val subject = Subject(
                id = subjectId,
                code = jsonSubject.code,
                name = jsonSubject.name,
                color = jsonSubject.color ?: Subject.PRESET_COLORS.indices.random(), // Random preset if null
                isActive = true
            )
            val theorySlots = jsonSubject.theorySlots.map { 
                with(parser) { it.toClassSlot(subjectId).copy(id = slotIdCounter++) }
            }
            val labSlots = jsonSubject.labVariants.flatMap { (groupName, variantSlots) ->
                variantSlots.map { slot ->
                    with(parser) {
                        slot.toClassSlot(subjectId).copy(
                            id = slotIdCounter++,
                            labGroupName = groupName,
                            entryType = EntryType.LAB
                        )
                    }
                }
            }
            SubjectWithSlots(subject, theorySlots + labSlots)
        }

        val solutions = ConflictSolver.findSolutions(subjectsWithSlots)
        val optimal = solutions.firstOrNull() ?: return ConflictStatus.None

        val theoryOverlaps = mutableListOf<TheoryOverlap>()
        val theorySlots = subjectsWithSlots.flatMap { s -> 
            s.slots.filter { it.entryType == EntryType.THEORY }.map { s.subject to it } 
        }
        
        for (i in theorySlots.indices) {
            for (j in i + 1 until theorySlots.size) {
                val (subj1, slot1) = theorySlots[i]
                val (subj2, slot2) = theorySlots[j]
                if (slot1.dayOfWeek == slot2.dayOfWeek && slot1.startTime < slot2.endTime && slot2.startTime < slot1.endTime) {
                    val sel1 = selected.find { it.subject.code == subj1.code }!!
                    val sel2 = selected.find { it.subject.code == subj2.code }!!
                    theoryOverlaps.add(TheoryOverlap(sel1, sel2, listOf(slot1 to slot2)))
                }
            }
        }

        val hasTheoryConflict = theoryOverlaps.isNotEmpty()
        val hasLabConflict = optimal.overlapsCount > 0

        return if (!hasTheoryConflict && !hasLabConflict) {
            ConflictStatus.Valid
        } else {
            val theoryOverlappingSlots = theoryOverlaps.flatMap { overlap ->
                overlap.slots.flatMap { pair ->
                    val s1 = subjectsWithSlots.find { it.subject.code == overlap.subject1.subject.code }!!.subject
                    val s2 = subjectsWithSlots.find { it.subject.code == overlap.subject2.subject.code }!!.subject
                    listOf(s1 to pair.first, s2 to pair.second)
                }
            }.distinctBy { it.second.id }

            ConflictStatus.Conflict(
                ConflictDetail(
                    selectedSubjects = selected.toList(),
                    theoryOverlaps = theoryOverlaps,
                    hasLabCombinationWithZeroOverlaps = !hasLabConflict,
                    theoryOverlappingSlots = theoryOverlappingSlots
                )
            )
        }
    }

    fun confirmImport() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            viewModelScope.launch {
                _uiState.value = ImportUiState.Importing
                try {
                    repository.importSubjects(currentState.selectedSubjects.toList())
                    _uiState.value = ImportUiState.Success
                } catch (e: Exception) {
                    _uiState.value = ImportUiState.Error("Import failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            _uiState.value = currentState.copy(searchQuery = query)
        }
    }

}