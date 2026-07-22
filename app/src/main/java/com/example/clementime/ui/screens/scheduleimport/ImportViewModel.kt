package com.example.clementime.ui.screens.scheduleimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.importing.model.ImportFile
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.model.SelectedMatter
import com.example.clementime.data.importing.repository.ImportRepository
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
        val selectedMatters: Set<SelectedMatter>,
        val searchQuery: String = "",
        val selectedFile: ImportFile
    ) : ImportUiState
    object Importing : ImportUiState
    object Success : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ImportRepository
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
                            selectedMatters = emptySet(),
                            selectedFile = file
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

    fun loadJsonFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            val result = repository.saveJsonFile(context, uri)
            result.fold(
                onSuccess = { importFile ->
                    loadFile(context, importFile)
                },
                onFailure = { error ->
                    _uiState.value = ImportUiState.Error("Failed to parse file: ${error.localizedMessage}")
                }
            )
        }
    }

    fun toggleMatterSelection(matter: JsonMatter, groupName: String) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val selectedMatter = SelectedMatter(matter, groupName)
            val updatedSelection = currentState.selectedMatters.toMutableSet()
            if (updatedSelection.contains(selectedMatter)) {
                updatedSelection.remove(selectedMatter)
            } else {
                updatedSelection.add(selectedMatter)
            }
            _uiState.value = currentState.copy(selectedMatters = updatedSelection)
        }
    }

    fun toggleAllMatters(targetMatters: Collection<SelectedMatter>) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val updatedSelection = currentState.selectedMatters.toMutableSet()
            val allSelected = targetMatters.isNotEmpty() && targetMatters.all { updatedSelection.contains(it) }
            if (allSelected) {
                updatedSelection.removeAll(targetMatters.toSet())
            } else {
                updatedSelection.addAll(targetMatters)
            }
            _uiState.value = currentState.copy(selectedMatters = updatedSelection)
        }
    }

    fun toggleSectionMatters(sectionMatters: Collection<SelectedMatter>) {
        toggleAllMatters(sectionMatters)
    }

    fun selectAllMatters(matters: Collection<SelectedMatter>? = null) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val toSelect = matters ?: run {
                val fromRoot = currentState.schema.matters.map { SelectedMatter(it, "General") }
                val fromYears = currentState.schema.years.flatMap { year ->
                    val yearCommon = year.matters.map { SelectedMatter(it, "${year.name} Common") }
                    val fromGroups = year.groups.flatMap { group ->
                        group.matters.map { SelectedMatter(it, "${year.name} ${group.name}") }
                    }
                    yearCommon + fromGroups
                }
                fromRoot + fromYears
            }
            val updatedSelection = currentState.selectedMatters.toMutableSet()
            updatedSelection.addAll(toSelect)
            _uiState.value = currentState.copy(selectedMatters = updatedSelection)
        }
    }

    fun confirmImport() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            viewModelScope.launch {
                _uiState.value = ImportUiState.Importing
                try {
                    repository.importMatters(currentState.selectedMatters.toList())
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

    fun resetState() {
        _uiState.value = ImportUiState.LoadingLibrary
    }
}