package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser(),
    private val apiService: GitHubScheduleApiService? = null
) {

    fun parseJsonString(jsonContent: String): Result<ScheduleJsonSchema> {
        return parser.parseJson(jsonContent)
    }

    suspend fun listAvailableImportFiles(context: Context): List<ImportFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ImportFile>()

        val dir = File(context.filesDir, "imports")
        if (dir.exists()) {
            dir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val schema = parser.parseJson(jsonString).getOrNull()
                    val title = schema?.title ?: file.name
                    list.add(ImportFile(id = file.name, title = title, fileUri = file.absolutePath))
                } catch (_: Exception) {
                    // Skip invalid files
                }
            }
        }

        list
    }

    suspend fun saveJsonFile(context: Context, uri: Uri): Result<ImportFile> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw Exception("Could not open file stream")

            val schema = parser.parseJson(jsonString).getOrThrow()
            val title = schema.title ?: "Custom Import"

            val filename = "import_${System.currentTimeMillis()}.json"
            val dir = File(context.filesDir, "imports")
            if (!dir.exists()) dir.mkdirs()

            val destFile = File(dir, filename)
            destFile.writeText(jsonString)

            Result.success(ImportFile(id = filename, title = title, fileUri = destFile.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomImportFile(context: Context, filename: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, "imports"), filename)
        if (file.exists() && !filename.contains("/") && !filename.contains("..")) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun importSubjects(selectedSubjects: List<SelectedSubject>) {
        val existingSubjects = dao.getAllSubjectsWithSlots().first()
        val usedColors = existingSubjects.map { it.subject.color }.toMutableSet()
        
        val availableColors = Subject.PRESET_COLORS.filter { it !in usedColors }.toMutableList()
        availableColors.shuffle()

        selectedSubjects.forEach { selected ->
            val jsonSubject = selected.subject
            
            // Find existing subject with same code (or name) to replace it
            val existing = existingSubjects.find { 
                val localCode = it.subject.code.trim()
                val remoteCode = jsonSubject.code.trim()
                val localName = it.subject.name.trim()
                val remoteName = jsonSubject.name.trim()
                
                (remoteCode.isNotBlank() && localCode.equals(remoteCode, ignoreCase = true)) ||
                (remoteCode.isBlank() && localName.equals(remoteName, ignoreCase = true))
            }?.subject

            // Auto-select lab group if only one variant exists
            val labGroups = jsonSubject.labVariants.keys
            val autoSelectedLabGroup = if (labGroups.size == 1) labGroups.first() else existing?.selectedLabGroup

            val chosenColor = existing?.color ?: when {
                jsonSubject.color != null -> jsonSubject.color
                availableColors.isNotEmpty() -> availableColors.removeAt(0)
                else -> Subject.PRESET_COLORS.random()
            }
            if (existing == null) {
                usedColors.add(chosenColor)
            }

            val subject = Subject(
                id = existing?.id ?: 0L,
                code = jsonSubject.code,
                name = jsonSubject.name,
                color = chosenColor,
                courseGroup = selected.courseGroup,
                isActive = true,
                selectedLabGroup = autoSelectedLabGroup,
                semester = jsonSubject.semester ?: 1,
                isDummy = jsonSubject.isDummy
            )

            val theorySlots = jsonSubject.theorySlots.map {
                with(parser) { it.toClassSlot() }
            }

            val labSlots = jsonSubject.labVariants.flatMap { (groupName, variantSlots) ->
                variantSlots.map { slot ->
                    with(parser) {
                        slot.toClassSlot().copy(
                            labGroupName = groupName,
                            entryType = EntryType.LAB
                        )
                    }
                }
            }

            dao.upsertSubjectWithSlots(subject, theorySlots + labSlots)
        }
     }

    suspend fun getExistingActiveSubjects(semester: Int): List<com.marcoslorcar.clementime.data.SubjectWithSlots> = withContext(Dispatchers.IO) {
        dao.getAllSubjectsWithSlotsBySemester(semester).first().filter { it.subject.isActive }
    }

    fun normalizeGitHubUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.startsWith("https://github.com/")) {
            trimmed = trimmed
                .replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("/tree/", "/")
                .replace("/blob/", "/")
        }
        return trimmed
    }

    suspend fun fetchRemoteSchedules(rawBaseUrl: String, forceRefresh: Boolean = false): Result<List<RemoteScheduleSummary>> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) return@withContext Result.failure(Exception("Network service unavailable"))
            val baseUrl = normalizeGitHubUrl(rawBaseUrl)
            var indexUrl = when {
                baseUrl.endsWith("schedules_index.json") -> baseUrl
                baseUrl.endsWith("/") -> "${baseUrl}schedules_index.json"
                else -> "$baseUrl/schedules_index.json"
            }
            
            if (forceRefresh) {
                indexUrl += if (indexUrl.contains("?")) "&" else "?"
                indexUrl += "t=${System.currentTimeMillis()}"
            }

            val response = apiService.getScheduleIndex(indexUrl)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch remote index: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "remote_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun fetchRemoteScheduleSchema(
        context: Context,
        file: ImportFile,
        forceRefresh: Boolean = false
    ): Result<ScheduleJsonSchema> = withContext(Dispatchers.IO) {
        val cacheDir = getCacheDir(context)
        val cacheFile = File(cacheDir, "cache_${file.id}.json")

        try {
            if (apiService == null) throw Exception("Network service unavailable")
            val fullUrl = normalizeGitHubUrl(file.remotePath ?: throw Exception("Remote path is null"))
            val response = apiService.getRawScheduleSchema(fullUrl)
            if (response.isSuccessful) {
                val jsonBody = response.body()?.string() ?: throw Exception("Response body is empty")
                val schema = parser.parseJson(jsonBody).getOrThrow()
                
                // Save to cache for offline availability
                cacheFile.writeText(jsonBody)
                
                Result.success(schema)
            } else {
                throw Exception("Failed to fetch remote schema: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            if (!forceRefresh && cacheFile.exists()) {
                try {
                    val jsonBody = cacheFile.readText()
                    val schema = parser.parseJson(jsonBody).getOrThrow()
                    Result.success(schema)
                } catch (_: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }


}
