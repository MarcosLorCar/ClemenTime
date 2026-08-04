package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.utils.SlotDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject

@Serializable
data class CacheMetadata(
    val id: String,
    val title: String,
    val description: String?,
    val remotePath: String,
    val lastUsed: Long,
    val hash: String,
    val updatedTime: String? = null
)

@Serializable
data class RemoteCacheList(
    val entries: List<CacheMetadata> = emptyList()
)

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser(),
    private val apiService: GitHubScheduleApiService? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
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
            
            // Find existing subject with same code (or name) AND semester to replace it
            val targetSemester = jsonSubject.semester ?: 1
            val existing = existingSubjects.find { 
                it.subject.semester == targetSemester && (
                    (it.subject.code.isNotBlank() && it.subject.code.equals(jsonSubject.code, ignoreCase = true)) ||
                    (it.subject.code.isBlank() && it.subject.name.equals(jsonSubject.name, ignoreCase = true))
                )
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

    /** Returns how many subjects were actually updated. */
    suspend fun applySlotDiffs(
        diffs: List<SlotDiff>,
        remoteSlots: List<JsonFlatSlot> = emptyList(),
        semester: Int
    ): Int = withContext(Dispatchers.IO) {
        if (diffs.isEmpty()) return@withContext 0

        var slotsToUse = remoteSlots
        if (slotsToUse.isEmpty()) {
            val baseUrl = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL
            val summaries = fetchRemoteSchedules(baseUrl).getOrNull().orEmpty()
            val targetSemesterId = "${semester}C"
            val summary = summaries.find {
                it.id.equals(targetSemesterId, ignoreCase = true) ||
                        it.path.contains(targetSemesterId, ignoreCase = true)
            } ?: summaries.firstOrNull()

            if (summary != null && apiService != null) {
                val fullUrl = normalizeGitHubUrl(
                    if (baseUrl.endsWith("/")) "${baseUrl}${summary.path}" else "${baseUrl}/${summary.path}"
                )
                val resp = apiService.getRawScheduleSchema(fullUrl)
                if (resp.isSuccessful) {
                    val jsonString = resp.body()?.string().orEmpty()
                    slotsToUse = runCatching { json.decodeFromString<List<JsonFlatSlot>>(jsonString) }.getOrDefault(emptyList())
                }
            }
        }

        if (slotsToUse.isEmpty()) return@withContext 0

        var appliedCount = 0
        val affectedKeys = diffs.map { it.subjectCode.ifBlank { it.subjectName }.trim() }.distinct()
        val existingSubjects = dao.getAllSubjectsWithSlotsBySemester(semester).first()

        val targetSemesterCode = "${semester}C"
        val semesterRemoteSlots = slotsToUse.filter { slot ->
            val slotCuat = slot.cuatrimestre.trim()
            slotCuat.equals(targetSemesterCode, ignoreCase = true) || slotCuat == "$semester"
        }

        for (affectedKey in affectedKeys) {
            val existingSws = existingSubjects.find { sws ->
                sws.subject.code.trim().equals(affectedKey, ignoreCase = true) ||
                        sws.subject.name.trim().equals(affectedKey, ignoreCase = true)
            } ?: continue

            val subject = existingSws.subject

            val matchedJsonSlots = semesterRemoteSlots.filter { rSlot ->
                val asig = rSlot.asignatura.trim()
                val codeMatch = rSlot.codigo?.trim()?.equals(subject.code.trim(), ignoreCase = true) ?: false
                val asigMatch = asig.equals(subject.code.trim(), ignoreCase = true) || asig.equals(subject.name.trim(), ignoreCase = true)
                
                val groupMatch = subject.courseGroup.isNullOrBlank() || rSlot.grupo.isBlank() ||
                        normalizeGroup(subject.courseGroup) == normalizeGroup(rSlot.grupo)
                (codeMatch || asigMatch) && groupMatch
            }

            val newClassSlots = matchedJsonSlots.map { rSlot ->
                val isLab = rSlot.esLaboratorio ||
                        rSlot.tipo.contains("laboratorio", ignoreCase = true) ||
                        rSlot.tipo.contains("lab", ignoreCase = true)

                ClassSlot(
                    subjectId = subject.id,
                    dayOfWeek = parser.parseDayOfWeek(rSlot.dia),
                    startTime = parseLocalTime(rSlot.horaInicio),
                    endTime = parseLocalTime(rSlot.horaFin),
                    classroom = rSlot.aula.trim().ifEmpty { null },
                    labGroupName = rSlot.grupoPracticas.trim().ifEmpty { null },
                    entryType = if (isLab) EntryType.LAB else EntryType.THEORY,
                    professor = rSlot.profesor.trim().ifEmpty { null }
                )
            }

            // upsertSubjectWithSlots deletes every existing slot before inserting. If matching
            // produced nothing (renamed subject, changed group code, parse failure) applying
            // would silently leave the user with an empty subject, so keep what they have.
            if (newClassSlots.isEmpty()) continue

            dao.upsertSubjectWithSlots(subject, newClassSlots)
            appliedCount++
        }

        appliedCount
    }

    private fun normalizeGroup(group: String?): String {
        if (group.isNullOrBlank()) return ""
        return group.replace("º", "").replace("ª", "").replace(" ", "").uppercase()
    }

    private fun parseLocalTime(timeStr: String): LocalTime {
        val trimmed = timeStr.trim()
        if (trimmed.isBlank()) return LocalTime.MIDNIGHT
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return LocalTime.of(h, m)
        }
        return runCatching { LocalTime.parse(trimmed) }.getOrDefault(LocalTime.MIDNIGHT)
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

    fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "remote_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMetadataFile(context: Context): File {
        return File(getCacheDir(context), "metadata.json")
    }

    fun getCachedRemoteSchedules(context: Context): List<CacheMetadata> {
        return readCacheMetadata(context)
    }

    private fun readCacheMetadata(context: Context): List<CacheMetadata> {
        val file = getMetadataFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText()
            json.decodeFromString<RemoteCacheList>(jsonString).entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeCacheMetadata(context: Context, entries: List<CacheMetadata>) {
        val file = getMetadataFile(context)
        try {
            val jsonString = json.encodeToString(RemoteCacheList(entries))
            file.writeText(jsonString)
        } catch (_: Exception) {
            // Ignore
        }
    }

    suspend fun fetchRemoteScheduleSchema(
        context: Context,
        file: ImportFile
    ): Result<ScheduleJsonSchema> = withContext(Dispatchers.IO) {
        val cacheDir = getCacheDir(context)
        val cacheFile = File(cacheDir, "cache_${file.id}.json")
        val metadataList = readCacheMetadata(context).toMutableList()
        val existingEntry = metadataList.find { it.id == file.id }

        try {
            if (apiService == null) throw Exception("Network service unavailable")
            val fullUrl = normalizeGitHubUrl(file.remotePath ?: throw Exception("Remote path is null"))
            val response = apiService.getRawScheduleSchema(fullUrl)
            if (response.isSuccessful) {
                val jsonBody = response.body()?.string() ?: throw Exception("Response body is empty")
                val schema = parser.parseJson(jsonBody).getOrThrow()
                val newHash = jsonBody.sha256()

                val updatedEntry = CacheMetadata(
                    id = file.id,
                    title = schema.title ?: file.title,
                    description = file.description,
                    remotePath = file.remotePath,
                    lastUsed = System.currentTimeMillis(),
                    hash = newHash,
                    updatedTime = file.updatedTime
                )

                if (existingEntry == null || existingEntry.hash != newHash || !cacheFile.exists()) {
                    cacheFile.writeText(jsonBody)
                    metadataList.removeAll { it.id == file.id }
                    metadataList.add(updatedEntry)

                    if (metadataList.size > 5) {
                        metadataList.sortBy { it.lastUsed }
                        while (metadataList.size > 5) {
                            val oldest = metadataList.removeAt(0)
                            val oldestFile = File(cacheDir, "cache_${oldest.id}.json")
                            if (oldestFile.exists()) {
                                oldestFile.delete()
                            }
                        }
                    }
                } else {
                    metadataList.removeAll { it.id == file.id }
                    metadataList.add(updatedEntry)
                }

                writeCacheMetadata(context, metadataList)
                Result.success(schema)
            } else {
                throw Exception("Failed to fetch remote schema: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            if (existingEntry != null && cacheFile.exists()) {
                try {
                    val jsonBody = cacheFile.readText()
                    val schema = parser.parseJson(jsonBody).getOrThrow()
                    metadataList.removeAll { it.id == file.id }
                    metadataList.add(existingEntry.copy(lastUsed = System.currentTimeMillis()))
                    writeCacheMetadata(context, metadataList)
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