package com.marcoslorcar.clementime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marcoslorcar.clementime.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
open class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context?
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val scrollableTabsKey = booleanPreferencesKey("scrollable_tabs")
    private val showNowLineKey = booleanPreferencesKey("show_now_line")
    private val nowLineStyleKey = stringPreferencesKey("now_line_style")
    private val highContrastKey = booleanPreferencesKey("high_contrast")
    private val selectedThemeKey = stringPreferencesKey("selected_theme")
    private val githubRepoBaseUrlKey = stringPreferencesKey("github_repo_base_url")
    private val isOnboardingCompletedKey = booleanPreferencesKey("is_onboarding_completed")
    private val onboardingTooltipsEnabledKey = booleanPreferencesKey("onboarding_tooltips_enabled")
    private val hasSeenImportConflictTooltipKey = booleanPreferencesKey("has_seen_import_conflict_tooltip")
    private val hasSeenOptimizerTooltipKey = booleanPreferencesKey("has_seen_optimizer_tooltip")
    private val hasSeenResolverPrioritiesTooltipKey = booleanPreferencesKey("has_seen_resolver_priorities_tooltip")
    private val hasSeenResolverApplyTooltipKey = booleanPreferencesKey("has_seen_resolver_apply_tooltip")
    private val hasSeenAddSlotTooltipKey = booleanPreferencesKey("has_seen_add_slot_tooltip")
    private val hasSeenImportPreviewTooltipKey = booleanPreferencesKey("has_seen_import_preview_tooltip")
    private val hasSeenLabSelectionTooltipKey = booleanPreferencesKey("has_seen_lab_selection_tooltip")
    private val currentSemesterKey = intPreferencesKey("current_semester")
    private val periodTypeKey = stringPreferencesKey("period_type")
    private val periodCountKey = intPreferencesKey("period_count")
    private val hasManuallyChangedSemesterKey = booleanPreferencesKey("has_manually_changed_semester")
    private val wasSemesterAutoChangedKey = booleanPreferencesKey("was_semester_auto_changed")
    private val dayStartHourKey = intPreferencesKey("day_start_hour")
    private val dayStartMinuteKey = intPreferencesKey("day_start_minute")
    private val dayEndHourKey = intPreferencesKey("day_end_hour")
    private val dayEndMinuteKey = intPreferencesKey("day_end_minute")
    // Legacy v1.3.3 keys retained read-only for one-time SyncMode migration in syncModeFlow.
    private val legacyAutoUpdateIntervalHoursKey = intPreferencesKey("auto_update_interval_hours")
    private val legacyAutoUpdateIntervalMinutesKey = intPreferencesKey("auto_update_interval_minutes")
    private val lastScheduleSyncTimestampKey = longPreferencesKey("last_schedule_sync_timestamp")
    private val selectedUniversityIdKey = stringPreferencesKey("selected_university_id")
    private val selectedFacultyIdKey = stringPreferencesKey("selected_faculty_id")
    private val selectedUniversityNameKey = stringPreferencesKey("selected_university_name")
    private val selectedFacultyNameKey = stringPreferencesKey("selected_faculty_name")
    private val selectedFcmTopicPrefixKey = stringPreferencesKey("selected_fcm_topic_prefix")
    private val syncModeKey = stringPreferencesKey("sync_mode")
    private val subscribedFcmTopicKey = stringPreferencesKey("subscribed_fcm_topic")

    private fun lastKnownScheduleHashKey(semester: Int) = stringPreferencesKey("last_known_schedule_hash_$semester")
    private fun lastNotifiedScheduleHashKey(semester: Int) = stringPreferencesKey("last_notified_schedule_hash_$semester")
    private fun lastRemoteSubjectHashesKey(semester: Int) = stringPreferencesKey("last_remote_subject_hashes_$semester")

    @Suppress("TooGenericExceptionCaught")
    private fun <T> readPreference(
        defaultValue: T,
        mapper: (Preferences) -> T
    ): Flow<T> = try {
        context?.dataStore?.data?.map(mapper) ?: flowOf(defaultValue)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Fallback to default in unit tests or headless environments without Android DataStore.
        flowOf(defaultValue)
    }

    private fun <T> readPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        readPreference(defaultValue) { preferences -> preferences[key] ?: defaultValue }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun editPreferences(block: (MutablePreferences) -> Unit) {
        try {
            context?.dataStore?.edit(block)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Gracefully ignore DataStore write failures in headless/test environments.
        }
    }

    private suspend fun <T> writePreference(key: Preferences.Key<T>, value: T) {
        editPreferences { preferences -> preferences[key] = value }
    }

    open val themeFlow: Flow<String>
        get() = readPreference(themeKey, "system")

    open val scrollableTabsFlow: Flow<Boolean>
        get() = readPreference(scrollableTabsKey, false)

    open val showNowLineFlow: Flow<Boolean>
        get() = readPreference(showNowLineKey, true)

    open val nowLineStyleFlow: Flow<String>
        get() = readPreference(nowLineStyleKey, "discrete")

    open val highContrastFlow: Flow<Boolean>
        get() = readPreference(highContrastKey, false)

    open val selectedThemeFlow: Flow<String>
        get() = readPreference(selectedThemeKey, "clementine")

    open val githubRepoBaseUrlFlow: Flow<String>
        get() = readPreference(githubRepoBaseUrlKey, DEFAULT_GITHUB_REPO_BASE_URL)

    open val isOnboardingCompletedFlow: Flow<Boolean>
        get() = readPreference(isOnboardingCompletedKey, false)

    open val onboardingTooltipsEnabledFlow: Flow<Boolean>
        get() = readPreference(onboardingTooltipsEnabledKey, true)

    open val hasSeenImportConflictTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenImportConflictTooltipKey, false)

    open val hasSeenOptimizerTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenOptimizerTooltipKey, false)

    open val hasSeenResolverPrioritiesTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenResolverPrioritiesTooltipKey, false)

    open val hasSeenResolverApplyTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenResolverApplyTooltipKey, false)

    open val hasSeenAddSlotTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenAddSlotTooltipKey, false)

    open val hasSeenImportPreviewTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenImportPreviewTooltipKey, false)

    open val hasSeenLabSelectionTooltipFlow: Flow<Boolean>
        get() = readPreference(hasSeenLabSelectionTooltipKey, false)

    open val currentSemesterFlow: Flow<Int>
        get() = readPreference(currentSemesterKey, 1)

    open val periodConfigFlow: Flow<PeriodConfig>
        get() = readPreference(PeriodConfig.DEFAULT) { preferences ->
            val type = preferences[periodTypeKey]?.let { raw ->
                runCatching { PeriodType.valueOf(raw) }.getOrNull()
            } ?: PeriodType.SEMESTERS
            val count = preferences[periodCountKey] ?: type.defaultCount
            PeriodConfig.normalized(type, count)
        }

    open val hasManuallyChangedSemesterFlow: Flow<Boolean>
        get() = readPreference(hasManuallyChangedSemesterKey, false)

    open val wasSemesterAutoChangedFlow: Flow<Boolean>
        get() = readPreference(wasSemesterAutoChangedKey, false)

    open val dayStartHourFlow: Flow<Int>
        get() = readPreference(dayStartHourKey, 8)

    open val dayStartMinuteFlow: Flow<Int>
        get() = readPreference(dayStartMinuteKey, 30)

    open val dayEndHourFlow: Flow<Int>
        get() = readPreference(dayEndHourKey, 21)

    open val dayEndMinuteFlow: Flow<Int>
        get() = readPreference(dayEndMinuteKey, 30)

    open fun getLastKnownScheduleHashFlow(semester: Int): Flow<String> =
        readPreference(lastKnownScheduleHashKey(semester), "")

    /**
     * The remote hash we have already posted a notification for. Kept separate from
     * [getLastKnownScheduleHashFlow], which records the version the user has *accepted* or
     * ignored: the diff sheet re-runs the sync to repopulate itself, so marking a version
     * "seen" on notify would leave the sheet empty when the user taps through.
     */
    open fun getLastNotifiedScheduleHashFlow(semester: Int): Flow<String> =
        readPreference(lastNotifiedScheduleHashKey(semester), "")

    open val lastScheduleSyncTimestampFlow: Flow<Long>
        get() = readPreference(lastScheduleSyncTimestampKey, 0L)

    open suspend fun setThemeMode(theme: String) = writePreference(themeKey, theme)

    open suspend fun setGithubRepoBaseUrl(baseUrl: String) = writePreference(githubRepoBaseUrlKey, baseUrl)

    open suspend fun setScrollableTabs(scrollable: Boolean) = writePreference(scrollableTabsKey, scrollable)

    open suspend fun setShowNowLine(show: Boolean) = writePreference(showNowLineKey, show)

    open suspend fun setNowLineStyle(style: String) = writePreference(nowLineStyleKey, style)

    open suspend fun setHighContrast(enabled: Boolean) = writePreference(highContrastKey, enabled)

    open suspend fun setSelectedTheme(theme: String) = writePreference(selectedThemeKey, theme)

    open suspend fun setOnboardingCompleted(completed: Boolean) =
        writePreference(isOnboardingCompletedKey, completed)

    open suspend fun setOnboardingTooltipsEnabled(enabled: Boolean) =
        writePreference(onboardingTooltipsEnabledKey, enabled)

    open suspend fun setHasSeenImportConflictTooltip(seen: Boolean) =
        writePreference(hasSeenImportConflictTooltipKey, seen)

    open suspend fun setHasSeenOptimizerTooltip(seen: Boolean) =
        writePreference(hasSeenOptimizerTooltipKey, seen)

    open suspend fun setHasSeenResolverPrioritiesTooltip(seen: Boolean) =
        writePreference(hasSeenResolverPrioritiesTooltipKey, seen)

    open suspend fun setHasSeenResolverApplyTooltip(seen: Boolean) =
        writePreference(hasSeenResolverApplyTooltipKey, seen)

    open suspend fun setHasSeenAddSlotTooltip(seen: Boolean) =
        writePreference(hasSeenAddSlotTooltipKey, seen)

    open suspend fun setHasSeenImportPreviewTooltip(seen: Boolean) =
        writePreference(hasSeenImportPreviewTooltipKey, seen)

    open suspend fun setHasSeenLabSelectionTooltip(seen: Boolean) =
        writePreference(hasSeenLabSelectionTooltipKey, seen)

    open suspend fun setCurrentSemester(semester: Int) = writePreference(currentSemesterKey, semester)

    open suspend fun setPeriodConfig(config: PeriodConfig) {
        val normalized = PeriodConfig.normalized(config.type, config.count)
        editPreferences { preferences ->
            preferences[periodTypeKey] = normalized.type.name
            preferences[periodCountKey] = normalized.count
            val current = preferences[currentSemesterKey] ?: 1
            if (current > normalized.count || current < PeriodConfig.MIN_PERIOD_COUNT) {
                preferences[currentSemesterKey] = current.coerceIn(PeriodConfig.MIN_PERIOD_COUNT, normalized.count)
            }
        }
    }

    open suspend fun setHasManuallyChangedSemester(hasChanged: Boolean) =
        writePreference(hasManuallyChangedSemesterKey, hasChanged)

    open suspend fun setWasSemesterAutoChanged(autoChanged: Boolean) =
        writePreference(wasSemesterAutoChangedKey, autoChanged)

    open suspend fun setDayStartTime(hour: Int, minute: Int) = editPreferences { preferences ->
        preferences[dayStartHourKey] = hour
        preferences[dayStartMinuteKey] = minute
    }

    open suspend fun setDayEndTime(hour: Int, minute: Int) = editPreferences { preferences ->
        preferences[dayEndHourKey] = hour
        preferences[dayEndMinuteKey] = minute
    }

    open suspend fun resetScheduleHashes() = editPreferences { preferences ->
        for (semester in PeriodConfig.MIN_PERIOD_COUNT..PeriodConfig.MAX_PERIOD_COUNT) {
            preferences.remove(lastKnownScheduleHashKey(semester))
            preferences.remove(lastNotifiedScheduleHashKey(semester))
            preferences.remove(lastRemoteSubjectHashesKey(semester))
        }
        preferences.remove(lastScheduleSyncTimestampKey)
    }

    open suspend fun resetOnboardingAndTooltips() = editPreferences { preferences ->
        preferences.remove(isOnboardingCompletedKey)
        preferences.remove(onboardingTooltipsEnabledKey)
        preferences.remove(hasSeenImportConflictTooltipKey)
        preferences.remove(hasSeenOptimizerTooltipKey)
        preferences.remove(hasSeenResolverPrioritiesTooltipKey)
        preferences.remove(hasSeenResolverApplyTooltipKey)
        preferences.remove(hasSeenAddSlotTooltipKey)
        preferences.remove(hasSeenImportPreviewTooltipKey)
        preferences.remove(hasSeenLabSelectionTooltipKey)
    }

    open suspend fun setLastKnownScheduleHash(semester: Int, hash: String) =
        writePreference(lastKnownScheduleHashKey(semester), hash)

    open suspend fun setLastNotifiedScheduleHash(semester: Int, hash: String) =
        writePreference(lastNotifiedScheduleHashKey(semester), hash)

    open suspend fun setLastScheduleSyncTimestamp(timestamp: Long) =
        writePreference(lastScheduleSyncTimestampKey, timestamp)

    open val selectedUniversityIdFlow: Flow<String>
        get() = readPreference("") { preferences ->
            resolveSelectedUniversityId(
                storedUniversityId = preferences[selectedUniversityIdKey],
                legacyHash1 = preferences[lastKnownScheduleHashKey(1)],
                legacyHash2 = preferences[lastKnownScheduleHashKey(2)]
            )
        }

    open val selectedFacultyIdFlow: Flow<String>
        get() = readPreference("") { preferences ->
            resolveSelectedFacultyId(
                storedFacultyId = preferences[selectedFacultyIdKey],
                legacyHash1 = preferences[lastKnownScheduleHashKey(1)],
                legacyHash2 = preferences[lastKnownScheduleHashKey(2)]
            )
        }

    open val selectedUniversityNameFlow: Flow<String>
        get() = readPreference("") { preferences ->
            val storedName = preferences[selectedUniversityNameKey]
            if (!storedName.isNullOrBlank()) {
                storedName
            } else {
                val storedId = preferences[selectedUniversityIdKey]
                val resolvedId = resolveSelectedUniversityId(
                    storedUniversityId = storedId,
                    legacyHash1 = preferences[lastKnownScheduleHashKey(1)],
                    legacyHash2 = preferences[lastKnownScheduleHashKey(2)]
                )
                if (storedId.isNullOrBlank() && resolvedId == LEGACY_FALLBACK_UNIVERSITY_ID) {
                    "UCLM"
                } else {
                    storedName ?: ""
                }
            }
        }

    open val selectedFacultyNameFlow: Flow<String>
        get() = readPreference("") { preferences ->
            val storedName = preferences[selectedFacultyNameKey]
            if (!storedName.isNullOrBlank()) {
                storedName
            } else {
                val storedId = preferences[selectedFacultyIdKey]
                val resolvedId = resolveSelectedFacultyId(
                    storedFacultyId = storedId,
                    legacyHash1 = preferences[lastKnownScheduleHashKey(1)],
                    legacyHash2 = preferences[lastKnownScheduleHashKey(2)]
                )
                if (storedId.isNullOrBlank() && resolvedId == LEGACY_FALLBACK_FACULTY_ID) {
                    "Escuela Superior de Informática (ESI)"
                } else {
                    storedName ?: ""
                }
            }
        }

    open val selectedFcmTopicPrefixFlow: Flow<String>
        get() = readPreference(selectedFcmTopicPrefixKey, "")

    open suspend fun setSelectedFaculty(
        universityId: String,
        facultyId: String,
        universityName: String? = null,
        facultyName: String? = null,
        fcmTopicPrefix: String? = null
    ) = editPreferences { preferences ->
        preferences[selectedUniversityIdKey] = universityId.trim()
        preferences[selectedFacultyIdKey] = facultyId.trim()
        if (universityName.isNullOrBlank()) {
            preferences.remove(selectedUniversityNameKey)
        } else {
            preferences[selectedUniversityNameKey] = universityName.trim()
        }
        if (facultyName.isNullOrBlank()) {
            preferences.remove(selectedFacultyNameKey)
        } else {
            preferences[selectedFacultyNameKey] = facultyName.trim()
        }
        if (fcmTopicPrefix.isNullOrBlank()) {
            preferences.remove(selectedFcmTopicPrefixKey)
        } else {
            preferences[selectedFcmTopicPrefixKey] = fcmTopicPrefix.trim()
        }
    }

    open val syncModeFlow: Flow<SyncMode>
        get() = readPreference(SyncMode.ONLINE) { preferences ->
            resolveLegacySyncMode(
                rawSyncMode = preferences[syncModeKey],
                explicitMinutes = preferences[legacyAutoUpdateIntervalMinutesKey],
                explicitHours = preferences[legacyAutoUpdateIntervalHoursKey]
            )
        }

    open suspend fun setSyncMode(mode: SyncMode) = writePreference(syncModeKey, mode.name)

    open val subscribedFcmTopicFlow: Flow<String?>
        get() = readPreference<String?>(null) { preferences -> preferences[subscribedFcmTopicKey] }

    open suspend fun setSubscribedFcmTopic(topic: String?) = editPreferences { preferences ->
        if (topic.isNullOrBlank()) {
            preferences.remove(subscribedFcmTopicKey)
        } else {
            preferences[subscribedFcmTopicKey] = topic
        }
    }

    open fun getLastRemoteSubjectHashesFlow(semester: Int): Flow<Map<String, String>> =
        readPreference(emptyMap()) { preferences ->
            val raw = preferences[lastRemoteSubjectHashesKey(semester)]
            if (raw.isNullOrBlank()) {
                emptyMap()
            } else {
                runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
            }
        }

    open suspend fun setLastRemoteSubjectHashes(semester: Int, hashes: Map<String, String>) {
        if (hashes.isEmpty()) return
        editPreferences { preferences ->
            val key = lastRemoteSubjectHashesKey(semester)
            val existing = preferences[key]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val merged = existing + hashes
            preferences[key] = Json.encodeToString(merged)
        }
    }

    companion object {
        const val DEFAULT_GITHUB_REPO_BASE_URL = BuildConfig.DEFAULT_GITHUB_REPO_BASE_URL
        const val FALLBACK_GITHUB_REPO_BASE_URL = BuildConfig.FALLBACK_GITHUB_REPO_BASE_URL

        const val LEGACY_FALLBACK_UNIVERSITY_ID = "uclm"
        const val LEGACY_FALLBACK_FACULTY_ID = "esi"

        internal fun resolveSelectedUniversityId(
            storedUniversityId: String?,
            legacyHash1: String?,
            legacyHash2: String?
        ): String {
            if (!storedUniversityId.isNullOrBlank()) return storedUniversityId
            val hasLegacyHashes = !legacyHash1.isNullOrBlank() || !legacyHash2.isNullOrBlank()
            return if (hasLegacyHashes) LEGACY_FALLBACK_UNIVERSITY_ID else ""
        }

        internal fun resolveSelectedFacultyId(
            storedFacultyId: String?,
            legacyHash1: String?,
            legacyHash2: String?
        ): String {
            if (!storedFacultyId.isNullOrBlank()) return storedFacultyId
            val hasLegacyHashes = !legacyHash1.isNullOrBlank() || !legacyHash2.isNullOrBlank()
            return if (hasLegacyHashes) LEGACY_FALLBACK_FACULTY_ID else ""
        }

        internal fun resolveLegacySyncMode(
            rawSyncMode: String?,
            explicitMinutes: Int?,
            explicitHours: Int?
        ): SyncMode {
            if (rawSyncMode != null) {
                return runCatching { SyncMode.valueOf(rawSyncMode) }.getOrDefault(SyncMode.ONLINE)
            }
            val explicitlyDisabled = (explicitMinutes != null && explicitMinutes <= 0) ||
                (explicitMinutes == null && explicitHours != null && explicitHours <= 0)
            return if (explicitlyDisabled) SyncMode.OFFLINE else SyncMode.ONLINE
        }
    }
}
