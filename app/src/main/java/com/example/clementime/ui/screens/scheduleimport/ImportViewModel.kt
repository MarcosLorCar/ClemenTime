package com.example.clementime.ui.screens.scheduleimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.repository.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    object Idle : ImportUiState
    object Parsing : ImportUiState
    data class Selection(
        val schema: ScheduleJsonSchema,
        val selectedMatters: Set<JsonMatter>
    ) : ImportUiState
    object Importing : ImportUiState
    object Success : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun loadJsonFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw Exception("Could not open file stream")

                val result = repository.parseJsonString(jsonString)
                result.fold(
                    onSuccess = { schema ->
                        // By default, pre-select all matters in the JSON
                        _uiState.value = ImportUiState.Selection(
                            schema = schema,
                            selectedMatters = schema.matters.toSet()
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

    fun toggleMatterSelection(matter: JsonMatter) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val updatedSelection = currentState.selectedMatters.toMutableSet()
            if (updatedSelection.contains(matter)) {
                updatedSelection.remove(matter)
            } else {
                updatedSelection.add(matter)
            }
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

    fun resetState() {
        _uiState.value = ImportUiState.Idle
    }
}