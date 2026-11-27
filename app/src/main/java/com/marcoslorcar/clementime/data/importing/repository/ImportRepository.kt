package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.TermCodeParser
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleCatalog
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.parser.isLab
import com.marcoslorcar.clementime.data.importing.parser.normalizeGroup
import com.marcoslorcar.clementime.data.importing.parser.parseDayOfWeek
import com.marcoslorcar.clementime.data.importing.parser.parseRemoteTime
import com.marcoslorcar.clementime.utils.SlotDiff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser(),
    apiService: GitHubScheduleApiService? = null,
    json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
) {
    private val remoteCache = RemoteScheduleCache(json)
    private val remoteFetcher = RemoteScheduleFetcher(apiService, parser, json, remoteCache)

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
        } catch (e: CancellationException) {
            throw e
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
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
        periodType: PeriodType = PeriodType.SEMESTERS
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
        var downloadedSchedule: RemoteScheduleFetcher.FlatScheduleDownload? = null

        if (slotsToUse.isEmpty()) {
            val (effectiveBaseUrl, summaries) = fetchRemoteSchedules(
                rawBaseUrl = rawBaseUrl,
                facultyId = effectiveFacultyId,
                cacheBust = false,
                universityId = universityId
            ).getOrNull() ?: Pair(rawBaseUrl, emptyList())
            val summary = summaries.find { TermCodeParser.matchesSummary(it, semester) }

            if (summary != null) {
                val download = remoteFetcher.downloadFlatScheduleVerified(effectiveBaseUrl, summary).getOrNull()
                if (download != null) {
                    slotsToUse = download.slots
                    downloadedSchedule = download
                }
            }

            if (slotsToUse.isEmpty() && context != null) {
                slotsToUse = getCachedRemoteSlotsForSemester(
                    context = context,
                    semester = semester,
                    universityId = universityId,
                    facultyId = effectiveFacultyId,
                    periodType = periodType
                )
            }
        }

        if (slotsToUse.isEmpty()) return@withContext 0

        var appliedCount = 0
        val affectedKeys = diffsToApply.map { it.subjectCode.ifBlank { it.subjectName }.trim() }.distinct()
        val existingSubjects = dao.getAllSubjectsWithSlotsBySemester(semester).first()

        val semesterRemoteSlots = slotsToUse.filter { slot ->
            TermCodeParser.matchesSemester(slot.cuatrimestre, semester)
        }

        for (affectedKey in affectedKeys) {
            val existingSws = existingSubjects.find { sws ->
                sws.subject.code.trim().equals(affectedKey, ignoreCase = true) ||
                        sws.subject.name.trim().equals(affectedKey, ignoreCase = true)
            } ?: continue

            val subject = existingSws.subject
            val previouslyIgnored = existingSws.slots.filter { it.isIgnored }
            val subjectGroup = normalizeGroup(subject.courseGroup)

            val matchedJsonSlots = semesterRemoteSlots.filter { rSlot ->
                val asig = rSlot.asignatura.trim()
                val codeMatch = rSlot.codigo?.trim()?.equals(subject.code.trim(), ignoreCase = true) ?: false
                val asigMatch = asig.equals(subject.code.trim(), ignoreCase = true) || asig.equals(subject.name.trim(), ignoreCase = true)

                val groupMatch = subject.courseGroup.isNullOrBlank() || rSlot.grupo.isBlank() ||
                        subjectGroup == normalizeGroup(rSlot.grupo)
                (codeMatch || asigMatch) && groupMatch
            }

            val newClassSlots = matchedJsonSlots.map { rSlot ->
                val entryType = if (rSlot.isLab()) EntryType.LAB else EntryType.THEORY
                val dayOfWeek = parseDayOfWeek(rSlot.dia)
                val startTime = parseRemoteTime(rSlot.horaInicio)
                val endTime = parseRemoteTime(rSlot.horaFin)
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

        if (appliedCount > 0 && context != null && downloadedSchedule != null) {
            val summary = downloadedSchedule.summary
            val resolvedTermId = summary.id.ifBlank { TermCodeParser.formatTermCode(semester, periodType) }
            val scopedId = if (universityId.isNotBlank() && effectiveFacultyId.isNotBlank()) {
                "${universityId}_${effectiveFacultyId}_$resolvedTermId"
            } else {
                resolvedTermId
            }
            cacheRemoteSlots(
                context = context,
                fileId = scopedId,
                title = summary.title,
                description = summary.description,
                remotePath = downloadedSchedule.url,
                rawJson = downloadedSchedule.rawJson,
                updatedTime = summary.updatedTime,
                universityId = universityId.ifBlank { null },
                facultyId = effectiveFacultyId.ifBlank { null },
                termId = resolvedTermId
            )
        }

        appliedCount
    }

    suspend fun getExistingActiveSubjects(semester: Int): List<com.marcoslorcar.clementime.data.SubjectWithSlots> = withContext(Dispatchers.IO) {
        dao.getAllSubjectsWithSlotsBySemester(semester).first().filter { it.subject.isActive }
    }

    fun normalizeGitHubUrl(url: String): String = remoteFetcher.normalizeGitHubUrl(url)

    fun resolveScheduleUrl(baseUrl: String, relativePath: String): String =
        remoteFetcher.resolveScheduleUrl(baseUrl, relativePath)

    internal suspend fun downloadFlatScheduleVerified(
        baseUrl: String,
        summary: RemoteScheduleSummary,
        apiServiceOverride: GitHubScheduleApiService? = null
    ): Result<RemoteScheduleFetcher.FlatScheduleDownload> =
        remoteFetcher.downloadFlatScheduleVerified(baseUrl, summary, apiServiceOverride)

    suspend fun fetchRemoteSchedules(
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
        facultyId: String? = null,
        cacheBust: Boolean = false,
        universityId: String? = null
    ): Result<Pair<String, List<RemoteScheduleSummary>>> =
        remoteFetcher.fetchRemoteSchedules(rawBaseUrl, facultyId, cacheBust, universityId)

    suspend fun fetchRemoteCatalog(
        rawBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
        cacheBust: Boolean = false
    ): Result<Pair<String, ScheduleCatalog>> = remoteFetcher.fetchRemoteCatalog(rawBaseUrl, cacheBust)

    suspend fun fetchRemoteScheduleSchema(context: Context, file: ImportFile): Result<ScheduleJsonSchema> =
        remoteFetcher.fetchRemoteScheduleSchema(context, file)

    fun getCachedRemoteSchedules(context: Context): List<CacheMetadata> =
        remoteCache.readCacheMetadata(context)

    fun hasCachedRemoteSchedule(context: Context, fileId: String): Boolean =
        remoteCache.hasCachedSchedule(context, fileId)

    fun getCachedRemoteSlotsById(context: Context, fileId: String): List<JsonFlatSlot> =
        remoteCache.getCachedRemoteSlotsById(context, fileId)

    fun getCachedRemoteSlotsForSemester(
        context: Context?,
        semester: Int,
        universityId: String? = null,
        facultyId: String? = null,
        periodType: PeriodType = PeriodType.SEMESTERS
    ): List<JsonFlatSlot> = remoteCache.getCachedRemoteSlotsForSemester(
        context, semester, universityId, facultyId, periodType
    )

    fun clearRemoteCache(context: Context) = remoteCache.clearRemoteCache(context)

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
    ) = remoteCache.cacheRemoteSlots(
        context = context,
        fileId = fileId,
        title = title,
        description = description,
        remotePath = remotePath,
        rawJson = rawJson,
        updatedTime = updatedTime,
        universityId = universityId,
        universityName = universityName,
        facultyId = facultyId,
        facultyName = facultyName,
        fcmTopicPrefix = fcmTopicPrefix,
        termId = termId
    )

}

internal fun ByteArray.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun String.sha256(): String = this.toByteArray(Charsets.UTF_8).sha256()
