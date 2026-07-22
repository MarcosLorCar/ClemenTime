package com.example.clementime.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.SettingsRepository
import com.example.clementime.data.importing.parser.JsonScheduleParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val appLanguage: String = "en",
    val scrollableTabs: Boolean = false,
    val syncDirectoryUri: String? = null
)

sealed interface ExportStatus {
    object Idle : ExportStatus
    object Exporting : ExportStatus
    data class Success(val message: String) : ExportStatus
    data class Error(val error: String) : ExportStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleDao: ScheduleDao,
    private val jsonScheduleParser: JsonScheduleParser
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.languageFlow,
        settingsRepository.scrollableTabsFlow,
        settingsRepository.syncDirectoryFlow
    ) { theme, lang, scrollable, syncUri ->
        SettingsUiState(
            themeMode = theme,
            appLanguage = lang,
            scrollableTabs = scrollable,
            syncDirectoryUri = syncUri
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(theme: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(lang)
        }
    }

    fun setScrollableTabs(scrollable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScrollableTabs(scrollable)
        }
    }

    fun setSyncDirectoryUri(uriString: String?) {
        viewModelScope.launch {
            settingsRepository.setSyncDirectoryUri(uriString)
        }
    }

    fun exportData(context: Context, customUri: Uri? = null, onResult: (ExportStatus) -> Unit) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val subjects = scheduleDao.getAllSubjectsWithSlots().first()
                val jsonString = jsonScheduleParser.exportToJson("ClemenTime Export", subjects)
                
                val syncUriStr = settingsRepository.syncDirectoryFlow.first()
                var syncWritten = false
                if (syncUriStr != null) {
                    val treeUri = syncUriStr.toUri()
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val targetUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        docUri,
                        "application/json",
                        "clementime_export.json"
                    )
                    if (targetUri != null) {
                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                            out.write(jsonString.toByteArray())
                        }
                        syncWritten = true
                    }
                }

                if (customUri != null) {
                    context.contentResolver.openOutputStream(customUri)?.use { out ->
                        out.write(jsonString.toByteArray())
                    }
                    val msg = if (syncWritten) "Data exported locally and to Sync Folder" else "Data exported successfully"
                    onResult(ExportStatus.Success(msg))
                } else if (syncWritten) {
                    onResult(ExportStatus.Success("Data exported to Sync Folder"))
                } else {
                    onResult(ExportStatus.Error("Please configure a Sync Directory or choose a manual save location."))
                }
            } catch (e: Exception) {
                onResult(ExportStatus.Error("Export failed: ${e.localizedMessage}"))
            }
        }
    }
}
