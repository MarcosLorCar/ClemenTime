package com.marcoslorcar.clementime.data.importing.repository

import android.content.Context
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.FacultyCatalogEntry
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleCatalog
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.TermCatalogEntry
import com.marcoslorcar.clementime.data.importing.model.UniversityCatalogEntry
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal class RemoteScheduleFetcher(
    private val apiService: GitHubScheduleApiService?,
    private val parser: JsonScheduleParser,
    private val json: Json,
    private val cache: RemoteScheduleCache
) {
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
                val indexBody = response.body()
                if (response.isSuccessful && indexBody != null) {
                    return@withContext Result.success(Pair(baseUrl, indexBody))
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
        } catch (e: CancellationException) {
            throw e
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
            val catalogBody = response?.body()
            if (response != null && response.isSuccessful && catalogBody != null) {
                return@withContext Result.success(Pair(baseUrl, catalogBody))
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
            val indexBody = indexResponse?.body()
            if (indexResponse != null && indexResponse.isSuccessful && indexBody != null) {
                val terms = indexBody.map { summary ->
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallbackUrl = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL
            if (normalizeGitHubUrl(rawBaseUrl) != normalizeGitHubUrl(fallbackUrl)) {
                fetchRemoteCatalog(fallbackUrl, cacheBust)
            } else {
                Result.failure(e)
            }
        }
    }

    data class FlatScheduleDownload(
        val summary: RemoteScheduleSummary,
        val url: String,
        val rawJson: String,
        val slots: List<JsonFlatSlot>
    )

    suspend fun downloadFlatSchedule(
        baseUrl: String,
        summary: RemoteScheduleSummary
    ): FlatScheduleDownload? {
        if (apiService == null) return null
        val fullUrl = normalizeGitHubUrl(
            if (baseUrl.endsWith("/")) "${baseUrl}${summary.path}" else "$baseUrl/${summary.path}"
        )
        val cacheBustedUrl = if (!summary.hash.isNullOrBlank()) {
            if (fullUrl.contains("?")) "$fullUrl&v=${summary.hash}" else "$fullUrl?v=${summary.hash}"
        } else {
            fullUrl
        }
        val response = apiService.getRawScheduleSchema(cacheBustedUrl)
        if (!response.isSuccessful) return null
        val rawJson = response.body()?.string().orEmpty()
        val slots = runCatching { json.decodeFromString<List<JsonFlatSlot>>(rawJson) }.getOrDefault(emptyList())
        return if (slots.isEmpty()) null else FlatScheduleDownload(summary, fullUrl, rawJson, slots)
    }

    suspend fun fetchRemoteScheduleSchema(
        context: Context,
        file: ImportFile
    ): Result<ScheduleJsonSchema> = withContext(Dispatchers.IO) {
        val metadataList = cache.readCacheMetadata(context).toMutableList()
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

                cache.cacheRemoteSlots(
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (existingEntry != null) {
                try {
                    val jsonBody = cache.readCachedSchedule(context, file.id)
                        ?: return@withContext Result.failure(e)
                    val schema = parser.parseJson(jsonBody).getOrThrow()
                    metadataList.removeAll { it.id == file.id }
                    metadataList.add(existingEntry.copy(lastUsed = System.currentTimeMillis()))
                    cache.writeCacheMetadata(context, metadataList)
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
