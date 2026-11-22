package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.FacultyCatalogEntry
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleCatalog
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.model.TermCatalogEntry
import com.marcoslorcar.clementime.data.importing.model.UniversityCatalogEntry
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
    val updatedTime: String? = null,
    val universityId: String? = null,
    val universityName: String? = null,
    val facultyId: String? = null,
    val facultyName: String? = null,
    val fcmTopicPrefix: String? = null,
    val termId: String? = null
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

        // Check local imports directory
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
            val fileHash = jsonString.sha256()

            val filename = "import_$fileHash.json"
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

            // upsertSubjectWithSlots runs an @Update over every column, so any field left at
            // its default here silently overwrites what the user had. Carry the
            // user-authored ones across; only schedule data comes from the import.
            val subject = Subject(
                id = existing?.id ?: 0L,
                code = jsonSubject.code,
                name = jsonSubject.name,
                color = chosenColor,
                courseGroup = selected.courseGroup,
                isActive = true,
                defaultDurationMinutes = existing?.defaultDurationMinutes ?: 90,
                notes = existing?.notes ?: "",
                attachedFiles = existing?.attachedFiles ?: emptyList(),
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
        semester: Int,
        selectedSubjectKeys: Set<String>? = null,
        facultyId: String? = null,
        universityId: String = SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID,
        context: Context? = null,
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL
    ): Int = withContext(Dispatchers.IO) {
        val diffsToApply = if (selectedSubjectKeys != null) {
            diffs.filter { diff ->
                val groupKey = diff.subjectKey.ifBlank { diff.subjectCode.ifBlank { diff.subjectName } }
                groupKey in selectedSubjectKeys || diff.subjectCode in selectedSubjectKeys
            }
        } else {
            diffs
        }
        if (diffsToApply.isEmpty()) return@withContext 0

        val effectiveFacultyId = facultyId ?: SettingsRepository.LEGACY_FALLBACK_FACULTY_ID
        var slotsToUse = remoteSlots
        var downloadedSummary: RemoteScheduleSummary? = null
        var downloadedUrl: String? = null
        var downloadedJson: String? = null

        if (slotsToUse.isEmpty()) {
            val (effectiveBaseUrl, summaries) = fetchRemoteSchedules(
                rawBaseUrl = rawBaseUrl,
                facultyId = effectiveFacultyId,
                cacheBust = false,
                universityId = universityId
            ).getOrNull() ?: Pair(rawBaseUrl, emptyList())
            val targetSemesterId = "${semester}C"
            val summary = summaries.find {
                it.id.equals(targetSemesterId, ignoreCase = true) ||
                        it.path.contains(targetSemesterId, ignoreCase = true)
            } ?: summaries.firstOrNull()

            if (summary != null && apiService != null) {
                val fullUrl = normalizeGitHubUrl(
                    if (effectiveBaseUrl.endsWith("/")) "${effectiveBaseUrl}${summary.path}" else "${effectiveBaseUrl}/${summary.path}"
                )
                val cacheBustedUrl = if (!summary.hash.isNullOrBlank()) {
                    if (fullUrl.contains("?")) "$fullUrl&v=${summary.hash}" else "$fullUrl?v=${summary.hash}"
                } else {
                    fullUrl
                }
                val resp = apiService.getRawScheduleSchema(cacheBustedUrl)
                if (resp.isSuccessful) {
                    val jsonString = resp.body()?.string().orEmpty()
                    val parsed = runCatching { json.decodeFromString<List<JsonFlatSlot>>(jsonString) }.getOrDefault(emptyList())
                    if (parsed.isNotEmpty()) {
                        slotsToUse = parsed
                        downloadedSummary = summary
                        downloadedUrl = fullUrl
                        downloadedJson = jsonString
                    }
                }
            }

            if (slotsToUse.isEmpty() && context != null) {
                slotsToUse = getCachedRemoteSlotsForSemester(
                    context = context,
                    semester = semester,
                    universityId = universityId,
                    facultyId = effectiveFacultyId
                )
            }
        }

        if (slotsToUse.isEmpty()) return@withContext 0

        var appliedCount = 0
        val affectedKeys = diffsToApply.map { it.subjectCode.ifBlank { it.subjectName }.trim() }.distinct()
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
            val previouslyIgnored = existingSws.slots.filter { it.isIgnored }

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
                val entryType = if (isLab) EntryType.LAB else EntryType.THEORY
                val dayOfWeek = parser.parseDayOfWeek(rSlot.dia)
                val startTime = parseLocalTime(rSlot.horaInicio)
                val endTime = parseLocalTime(rSlot.horaFin)
                val wasIgnored = previouslyIgnored.any { old ->
                    old.dayOfWeek == dayOfWeek &&
                            old.startTime == startTime &&
                            old.endTime == endTime &&
                            old.entryType == entryType
                }

                ClassSlot(
                    subjectId = subject.id,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    classroom = rSlot.aula.trim().ifEmpty { null },
                    labGroupName = rSlot.grupoPracticas.trim().ifEmpty { null },
                    entryType = entryType,
                    professor = rSlot.profesor.trim().ifEmpty { null },
                    isIgnored = wasIgnored
                )
            }

            // upsertSubjectWithSlots deletes every existing slot before inserting. If matching
            // produced nothing (renamed subject, changed group code, parse failure) applying
            // would silently leave the user with an empty subject, so keep what they have.
            if (newClassSlots.isEmpty()) continue

            dao.upsertSubjectWithSlots(subject, newClassSlots)
            appliedCount++
        }

        if (appliedCount > 0 && context != null && downloadedJson != null && downloadedSummary != null && downloadedUrl != null) {
            val scopedId = if (universityId.isNotBlank() && effectiveFacultyId.isNotBlank()) {
                "${universityId}_${effectiveFacultyId}_${semester}C"
            } else {
                "${semester}C"
            }
            cacheRemoteSlots(
                context = context,
                fileId = scopedId,
                title = downloadedSummary.title,
                description = downloadedSummary.description,
                remotePath = downloadedUrl,
                rawJson = downloadedJson,
                updatedTime = downloadedSummary.updatedTime,
                universityId = universityId.ifBlank { null },
                facultyId = effectiveFacultyId.ifBlank { null },
                termId = "${semester}C"
            )
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

    suspend fun fetchRemoteSchedules(
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
        facultyId: String? = null,
        cacheBust: Boolean = false,
        universityId: String? = null
    ): Result<Pair<String, List<RemoteScheduleSummary>>> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) return@withContext Result.failure(Exception("Network service unavailable"))
            val baseUrl = normalizeGitHubUrl(rawBaseUrl)
            val cacheParam = if (cacheBust) "t=${System.currentTimeMillis() / 10_000L}" else null
            fun withCacheParam(url: String): String {
                if (cacheParam == null) return url
                return if (url.contains("?")) "$url&$cacheParam" else "$url?$cacheParam"
            }

            // 1. Try fetching 3-level schedules_catalog.json first
            val catalogUrl = withCacheParam(
                when {
                    baseUrl.endsWith("schedules_catalog.json") -> baseUrl
                    baseUrl.endsWith("/") -> "${baseUrl}schedules_catalog.json"
                    else -> "$baseUrl/schedules_catalog.json"
                }
            )
            val catalogResponse = runCatching { apiService.getScheduleCatalog(catalogUrl) }.getOrNull()
            if (catalogResponse != null && catalogResponse.isSuccessful && catalogResponse.body() != null) {
                val catalog = catalogResponse.body()!!
                val targetFaculty = if (!facultyId.isNullOrBlank()) {
                    catalog.universities
                        .filter { universityId.isNullOrBlank() || it.id.equals(universityId, ignoreCase = true) }
                        .flatMap { it.faculties }
                        .firstOrNull { it.id.equals(facultyId, ignoreCase = true) }
                } else {
                    null
                }

                if (targetFaculty == null) {
                    return@withContext Result.failure(
                        NoSuchElementException("Faculty $universityId/$facultyId not found in remote catalog")
                    )
                }

                val summaries = targetFaculty.terms.map { it.toRemoteSummary() }
                return@withContext Result.success(Pair(baseUrl, summaries))
            }

            // 2. Fall back to legacy flat schedules_index.json only when catalog failed and ESI is requested
            val canFallbackToLegacyIndex =
                facultyId.equals(SettingsRepository.LEGACY_FALLBACK_FACULTY_ID, ignoreCase = true) &&
                    (universityId.isNullOrBlank() || universityId.equals(SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID, ignoreCase = true))

            if (canFallbackToLegacyIndex) {
                val indexUrl = withCacheParam(
                    when {
                        baseUrl.endsWith("schedules_index.json") -> baseUrl
                        baseUrl.endsWith("/") -> "${baseUrl}schedules_index.json"
                        else -> "$baseUrl/schedules_index.json"
                    }
                )
                val response = apiService.getScheduleIndex(indexUrl)
                if (response.isSuccessful && response.body() != null) {
                    return@withContext Result.success(Pair(baseUrl, response.body()!!))
                }
            }

            val fallbackUrl = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL
            if (normalizeGitHubUrl(rawBaseUrl) != normalizeGitHubUrl(fallbackUrl)) {
                fetchRemoteSchedules(fallbackUrl, facultyId, cacheBust, universityId)
            } else {
                val code = catalogResponse?.code() ?: 0
                val msg = catalogResponse?.message() ?: "Unknown error"
                Result.failure(Exception("Failed to fetch remote schedules: $code $msg"))
            }
        } catch (e: Exception) {
            val fallbackUrl = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL
            if (normalizeGitHubUrl(rawBaseUrl) != normalizeGitHubUrl(fallbackUrl)) {
                fetchRemoteSchedules(fallbackUrl, facultyId, cacheBust, universityId)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchRemoteCatalog(
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
        cacheBust: Boolean = false
    ): Result<Pair<String, ScheduleCatalog>> = withContext(Dispatchers.IO) {
        try {
            if (apiService == null) return@withContext Result.failure(Exception("Network service unavailable"))
            val baseUrl = normalizeGitHubUrl(rawBaseUrl)
            val cacheParam = if (cacheBust) "t=${System.currentTimeMillis() / 10_000L}" else null
            fun withCacheParam(url: String): String {
                if (cacheParam == null) return url
                return if (url.contains("?")) "$url&$cacheParam" else "$url?$cacheParam"
            }

            val catalogUrl = withCacheParam(
                when {
                    baseUrl.endsWith("schedules_catalog.json") -> baseUrl
                    baseUrl.endsWith("/") -> "${baseUrl}schedules_catalog.json"
                    else -> "$baseUrl/schedules_catalog.json"
                }
            )
            val response = runCatching { apiService.getScheduleCatalog(catalogUrl) }.getOrNull()
            if (response != null && response.isSuccessful && response.body() != null) {
                return@withContext Result.success(Pair(baseUrl, response.body()!!))
            }

            // Fall back to legacy schedules_index.json wrapped into a synthetic ScheduleCatalog
            val indexUrl = withCacheParam(
                when {
                    baseUrl.endsWith("schedules_index.json") -> baseUrl
                    baseUrl.endsWith("/") -> "${baseUrl}schedules_index.json"
                    else -> "$baseUrl/schedules_index.json"
                }
            )
            val indexResponse = runCatching { apiService.getScheduleIndex(indexUrl) }.getOrNull()
            if (indexResponse != null && indexResponse.isSuccessful && indexResponse.body() != null) {
                val terms = indexResponse.body()!!.map { summary ->
                    TermCatalogEntry(
                        id = summary.id,
                        name = summary.title,
                        description = summary.description,
                        path = summary.path,
                        hash = summary.hash,
                        updatedTime = summary.updatedTime
                    )
                }
                val syntheticCatalog = ScheduleCatalog(
                    universities = listOf(
                        UniversityCatalogEntry(
                            id = SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID,
                            name = "UCLM",
                            faculties = listOf(
                                FacultyCatalogEntry(
                                    id = SettingsRepository.LEGACY_FALLBACK_FACULTY_ID,
                                    name = "Escuela Superior de Informática (ESI)",
                                    fcmTopicPrefix = "${SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID}_${SettingsRepository.LEGACY_FALLBACK_FACULTY_ID}",
                                    terms = terms
                                )
                            )
                        )
                    )
                )
                return@withContext Result.success(Pair(baseUrl, syntheticCatalog))
            }

            val fallbackUrl = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL
            if (normalizeGitHubUrl(rawBaseUrl) != normalizeGitHubUrl(fallbackUrl)) {
                fetchRemoteCatalog(fallbackUrl, cacheBust)
            } else {
                val code = response?.code() ?: indexResponse?.code() ?: 0
                val msg = response?.message() ?: indexResponse?.message() ?: "Unknown error"
                Result.failure(Exception("Failed to fetch catalog: $code $msg"))
            }
        } catch (e: Exception) {
            val fallbackUrl = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL
            if (normalizeGitHubUrl(rawBaseUrl) != normalizeGitHubUrl(fallbackUrl)) {
                fetchRemoteCatalog(fallbackUrl, cacheBust)
            } else {
                Result.failure(e)
            }
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

    fun getCachedRemoteSlotsById(context: Context, fileId: String): List<JsonFlatSlot> {
        val cacheFile = File(getCacheDir(context), "cache_${fileId}.json")
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<JsonFlatSlot>>(cacheFile.readText())
        }.getOrDefault(emptyList())
    }

    fun getCachedRemoteSlotsForSemester(
        context: Context?,
        semester: Int,
        universityId: String? = null,
        facultyId: String? = null
    ): List<JsonFlatSlot> {
        if (context == null) return emptyList()
        val termId = "${semester}C"

        if (!universityId.isNullOrBlank() && !facultyId.isNullOrBlank()) {
            val scopedSlots = getCachedRemoteSlotsById(context, "${universityId}_${facultyId}_${termId}")
            if (scopedSlots.isNotEmpty()) return scopedSlots
        }

        if (!facultyId.isNullOrBlank()) {
            val facultyScopedSlots = getCachedRemoteSlotsById(context, "${facultyId}_${termId}")
            if (facultyScopedSlots.isNotEmpty()) return facultyScopedSlots
        }

        val legacySlots = getCachedRemoteSlotsById(context, termId)
        if (legacySlots.isNotEmpty()) return legacySlots

        if (universityId.isNullOrBlank() && facultyId.isNullOrBlank()) {
            val matchingEntry = readCacheMetadata(context).firstOrNull { entry ->
                entry.termId.equals(termId, ignoreCase = true) ||
                    entry.id.endsWith("_$termId", ignoreCase = true)
            }
            if (matchingEntry != null) {
                return getCachedRemoteSlotsById(context, matchingEntry.id)
            }
        }

        return emptyList()
    }

    fun clearRemoteCache(context: Context) {
        val dir = getCacheDir(context)
        dir.listFiles()?.forEach { it.delete() }
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

    fun cacheRemoteSlots(
        context: Context,
        fileId: String,
        title: String,
        description: String?,
        remotePath: String,
        rawJson: String,
        updatedTime: String? = null,
        universityId: String? = null,
        universityName: String? = null,
        facultyId: String? = null,
        facultyName: String? = null,
        fcmTopicPrefix: String? = null,
        termId: String? = null
    ) {
        val cacheDir = getCacheDir(context)
        val cacheFile = File(cacheDir, "cache_${fileId}.json")
        val metadataList = readCacheMetadata(context).toMutableList()
        val existingEntry = metadataList.find { it.id == fileId }
        val newHash = rawJson.sha256()

        val updatedEntry = CacheMetadata(
            id = fileId,
            title = title,
            description = description ?: existingEntry?.description,
            remotePath = remotePath,
            lastUsed = System.currentTimeMillis(),
            hash = newHash,
            updatedTime = updatedTime ?: existingEntry?.updatedTime,
            universityId = universityId ?: existingEntry?.universityId,
            universityName = universityName ?: existingEntry?.universityName,
            facultyId = facultyId ?: existingEntry?.facultyId,
            facultyName = facultyName ?: existingEntry?.facultyName,
            fcmTopicPrefix = fcmTopicPrefix ?: existingEntry?.fcmTopicPrefix,
            termId = termId ?: existingEntry?.termId
        )

        if (existingEntry == null || existingEntry.hash != newHash || !cacheFile.exists()) {
            cacheFile.writeText(rawJson)
            metadataList.removeAll { it.id == fileId }
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
            metadataList.removeAll { it.id == fileId }
            metadataList.add(updatedEntry)
        }

        writeCacheMetadata(context, metadataList)
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
            var response = apiService.getRawScheduleSchema(fullUrl)
            if (!response.isSuccessful) {
                val fallbackUrl = normalizeGitHubUrl(SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL)
                val defaultUrl = normalizeGitHubUrl(SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL)
                if (defaultUrl != fallbackUrl && fullUrl.contains(defaultUrl)) {
                    val retryUrl = fullUrl.replace(defaultUrl, fallbackUrl)
                    val retryResponse = apiService.getRawScheduleSchema(retryUrl)
                    if (retryResponse.isSuccessful) {
                        response = retryResponse
                    }
                }
            }
            if (response.isSuccessful) {
                val jsonBody = response.body()?.string() ?: throw Exception("Response body is empty")
                val schema = parser.parseJson(jsonBody).getOrThrow()

                cacheRemoteSlots(
                    context = context,
                    fileId = file.id,
                    title = schema.title ?: file.title,
                    description = file.description,
                    remotePath = file.remotePath,
                    rawJson = jsonBody,
                    updatedTime = file.updatedTime,
                    universityId = file.universityId,
                    universityName = file.universityName,
                    facultyId = file.facultyId,
                    facultyName = file.facultyName,
                    fcmTopicPrefix = file.fcmTopicPrefix,
                    termId = file.termId
                )
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