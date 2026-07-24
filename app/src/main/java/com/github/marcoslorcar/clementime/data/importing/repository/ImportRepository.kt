package com.github.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.ScheduleDao
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.importing.model.ImportFile
import com.github.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.github.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import com.github.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.github.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser(),
    private val apiService: GitHubScheduleApiService? = null
) {

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun parseJsonString(jsonContent: String): Result<ScheduleJsonSchema> {
        return parser.parseJson(jsonContent)
    }

    suspend fun listAvailableImportFiles(context: Context): List<ImportFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ImportFile>()

        // 1. Check bundled asset
        var bundledHash = ""
        try {
            context.assets.open("schedules/primer_cuatrimestre.json").use { stream ->
                val jsonString = stream.bufferedReader().readText()
                bundledHash = jsonString.sha256()
                val schema = parser.parseJson(jsonString).getOrNull()
                val title = schema?.title ?: "Horarios 2026/2027 - 1º Cuatrimestre"
                list.add(ImportFile(id = "bundled", title = title, isBundled = true, fileUri = null))
            }
        } catch (_: Exception) {
            // Asset not found or failed to parse
        }

        // 2. Check local imports directory
        val dir = File(context.filesDir, "imports")
        if (dir.exists()) {
            dir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val fileHash = jsonString.sha256()
                    // Deduplicate against bundled file
                    if (fileHash == bundledHash) {
                        file.delete() // Clean up local duplicate
                        return@forEach
                    }
                    val schema = parser.parseJson(jsonString).getOrNull()
                    val title = schema?.title ?: file.name
                    list.add(ImportFile(id = file.name, title = title, isBundled = false, fileUri = file.absolutePath))
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
            val fileHash = jsonString.sha256()

            // Check if it matches bundled file hash
            var bundledHash = ""
            try {
                context.assets.open("schedules/primer_cuatrimestre.json").use { stream ->
                    bundledHash = stream.bufferedReader().readText().sha256()
                }
            } catch (_: Exception) {
                // Ignore
            }

            if (fileHash == bundledHash) {
                // It's the bundled file, return reference to bundled representation instead of duplicating
                return@withContext Result.success(ImportFile(id = "bundled", title = title, isBundled = true, fileUri = null))
            }

            val filename = "import_$fileHash.json"
            val dir = File(context.filesDir, "imports")
            if (!dir.exists()) dir.mkdirs()

            val destFile = File(dir, filename)
            destFile.writeText(jsonString)

            Result.success(ImportFile(id = filename, title = title, isBundled = false, fileUri = destFile.absolutePath))
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
                (it.subject.code.isNotBlank() && it.subject.code.equals(jsonSubject.code, ignoreCase = true)) ||
                (it.subject.code.isBlank() && it.subject.name.equals(jsonSubject.name, ignoreCase = true))
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

    suspend fun getExistingActiveSubjects(): List<com.github.marcoslorcar.clementime.data.SubjectWithSlots> = withContext(Dispatchers.IO) {
        dao.getAllSubjectsWithSlots().first().filter { it.subject.isActive }
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

    suspend fun fetchRemoteSchedules(rawBaseUrl: String): Result<List<RemoteScheduleSummary>> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) return@withContext Result.failure(Exception("Network service unavailable"))
            val baseUrl = normalizeGitHubUrl(rawBaseUrl)
            val indexUrl = when {
                baseUrl.endsWith("schedules_index.json") -> baseUrl
                baseUrl.endsWith("/") -> "${baseUrl}schedules_index.json"
                else -> "$baseUrl/schedules_index.json"
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

    suspend fun fetchRemoteScheduleSchema(rawFullUrl: String): Result<ScheduleJsonSchema> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) return@withContext Result.failure(Exception("Network service unavailable"))
            val fullUrl = normalizeGitHubUrl(rawFullUrl)
            val response = apiService.getScheduleSchema(fullUrl)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch remote schema: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}