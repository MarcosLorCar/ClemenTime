package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.TermCodeParser
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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

internal class RemoteScheduleCache(private val json: Json) {
    companion object {
        // Must exceed PeriodConfig.MAX_PERIOD_COUNT (8) so all periods in an N-period academic year
        // stay cached without evicting earlier periods.
        const val MAX_CACHED_REMOTE_SCHEDULES = 12
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "remote_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun hasCachedSchedule(context: Context, fileId: String): Boolean =
        cacheFile(getCacheDir(context), fileId).exists()

    private fun cacheFile(cacheDir: File, fileId: String): File = File(cacheDir, "cache_${fileId}.json")

    fun getCachedRemoteSlotsById(context: Context, fileId: String): List<JsonFlatSlot> {
        return getCachedRemoteSlotsByIdInDir(getCacheDir(context), fileId)
    }

    private fun getCachedRemoteSlotsByIdInDir(cacheDir: File, fileId: String): List<JsonFlatSlot> {
        val cacheFile = cacheFile(cacheDir, fileId)
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<JsonFlatSlot>>(cacheFile.readText())
        }.getOrDefault(emptyList())
    }

    fun getCachedRemoteSlotsForSemester(
        context: Context?,
        semester: Int,
        universityId: String? = null,
        facultyId: String? = null,
        periodType: PeriodType = PeriodType.SEMESTERS
    ): List<JsonFlatSlot> {
        if (context == null) return emptyList()
        return getCachedRemoteSlotsForSemesterInDir(
            cacheDir = getCacheDir(context),
            semester = semester,
            universityId = universityId,
            facultyId = facultyId,
            periodType = periodType
        )
    }

    internal fun getCachedRemoteSlotsForSemesterInDir(
        cacheDir: File,
        semester: Int,
        universityId: String? = null,
        facultyId: String? = null,
        periodType: PeriodType = PeriodType.SEMESTERS
    ): List<JsonFlatSlot> {
        val metadataEntries = readCacheMetadataInDir(cacheDir)
        val metadataMatch = metadataEntries.firstOrNull { entry ->
            val termMatches = TermCodeParser.parseTermIndex(entry.termId ?: entry.id) == semester
            if (!termMatches) return@firstOrNull false
            val uniMatches = universityId.isNullOrBlank() || entry.universityId.isNullOrBlank() ||
                entry.universityId.equals(universityId, ignoreCase = true)
            val facMatches = facultyId.isNullOrBlank() || entry.facultyId.isNullOrBlank() ||
                entry.facultyId.equals(facultyId, ignoreCase = true)
            uniMatches && facMatches
        }
        if (metadataMatch != null) {
            val cached = getCachedRemoteSlotsByIdInDir(cacheDir, metadataMatch.id)
            if (cached.isNotEmpty()) return cached
        }

        val candidateTermIds = buildList {
            add(TermCodeParser.formatTermCode(semester, periodType))
            for (type in PeriodType.entries) {
                add(TermCodeParser.formatTermCode(semester, type))
            }
        }.distinct()

        for (termId in candidateTermIds) {
            if (!universityId.isNullOrBlank() && !facultyId.isNullOrBlank()) {
                val scopedSlots = getCachedRemoteSlotsByIdInDir(cacheDir, "${universityId}_${facultyId}_${termId}")
                if (scopedSlots.isNotEmpty()) return scopedSlots
            }

            if (!facultyId.isNullOrBlank()) {
                val facultyScopedSlots = getCachedRemoteSlotsByIdInDir(cacheDir, "${facultyId}_${termId}")
                if (facultyScopedSlots.isNotEmpty()) return facultyScopedSlots
            }

            val legacySlots = getCachedRemoteSlotsByIdInDir(cacheDir, termId)
            if (legacySlots.isNotEmpty()) return legacySlots
        }

        if (universityId.isNullOrBlank() && facultyId.isNullOrBlank()) {
            val matchingEntry = metadataEntries.firstOrNull { entry ->
                TermCodeParser.parseTermIndex(entry.termId ?: entry.id) == semester
            }
            if (matchingEntry != null) {
                return getCachedRemoteSlotsByIdInDir(cacheDir, matchingEntry.id)
            }
        }

        return emptyList()
    }

    fun clearRemoteCache(context: Context) {
        val dir = getCacheDir(context)
        dir.listFiles()?.forEach { it.delete() }
    }

    fun readCacheMetadata(context: Context): List<CacheMetadata> {
        return readCacheMetadataInDir(getCacheDir(context))
    }

    internal fun readCacheMetadataInDir(cacheDir: File): List<CacheMetadata> {
        val file = File(cacheDir, "metadata.json")
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText()
            json.decodeFromString<RemoteCacheList>(jsonString).entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun writeCacheMetadata(context: Context, entries: List<CacheMetadata>) {
        writeCacheMetadataInDir(getCacheDir(context), entries)
    }

    private fun writeCacheMetadataInDir(cacheDir: File, entries: List<CacheMetadata>) {
        val file = File(cacheDir, "metadata.json")
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
        cacheRemoteSlotsInDir(
            cacheDir = getCacheDir(context),
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

    internal fun cacheRemoteSlotsInDir(
        cacheDir: File,
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
        termId: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val cacheFile = cacheFile(cacheDir, fileId)
        val metadataList = readCacheMetadataInDir(cacheDir).toMutableList()
        val existingEntry = metadataList.find { it.id == fileId }
        val newHash = rawJson.sha256()

        val updatedEntry = CacheMetadata(
            id = fileId,
            title = title,
            description = description ?: existingEntry?.description,
            remotePath = remotePath,
            lastUsed = nowMillis,
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

            if (metadataList.size > MAX_CACHED_REMOTE_SCHEDULES) {
                metadataList.sortBy { it.lastUsed }
                while (metadataList.size > MAX_CACHED_REMOTE_SCHEDULES) {
                    val oldest = metadataList.removeAt(0)
                    val oldestFile = cacheFile(cacheDir, oldest.id)
                    if (oldestFile.exists()) {
                        oldestFile.delete()
                    }
                }
            }
        } else {
            metadataList.removeAll { it.id == fileId }
            metadataList.add(updatedEntry)
        }

        writeCacheMetadataInDir(cacheDir, metadataList)
    }

    fun readCachedSchedule(context: Context, fileId: String): String? {
        val file = cacheFile(getCacheDir(context), fileId)
        return if (file.exists()) file.readText() else null
    }
}
