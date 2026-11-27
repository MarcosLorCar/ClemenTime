package com.marcoslorcar.clementime.data.importing.repository

import com.marcoslorcar.clementime.data.AttachedFileItem
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.api.GitHubScheduleApiService
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleCatalog
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.testing.FakeScheduleDao
import com.marcoslorcar.clementime.utils.ScheduleDiffChecker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ImportRepositoryTest {

    @Test
    fun importSubjects_replacesExistingSubjectWithSameCode() = runTest {
        val existingSubject = Subject(
            id = 42L,
            code = "SO",
            name = "Sistemas Operativos Old",
            color = 0xFF123456.toInt(),
            isActive = true
        )
        val fakeDao = FakeScheduleDao(
            initialSubjects = listOf(SubjectWithSlots(existingSubject, emptyList()))
        )
        val repository = ImportRepository(dao = fakeDao)

        val newJsonSubject = JsonSubject(
            code = "SO",
            name = "Sistemas Operativos Updated"
        )
        val selected = SelectedSubject(newJsonSubject, "1º A")

        repository.importSubjects(listOf(selected))

        assertEquals(1, fakeDao.upsertedSubjects.size)
        val (upsertedSubject, _) = fakeDao.upsertedSubjects[0]
        // Should preserve the existing ID (42L) and color (0xFF123456.toInt())
        assertEquals(42L, upsertedSubject.id)
        assertEquals("SO", upsertedSubject.code)
        assertEquals("Sistemas Operativos Updated", upsertedSubject.name)
        assertEquals(0xFF123456.toInt(), upsertedSubject.color)
    }

    @Test
    fun importSubjects_preservesUserAuthoredFieldsOnExistingSubject() = runTest {
        val existingSubject = Subject(
            id = 7L,
            code = "SO",
            name = "Sistemas Operativos",
            color = 0xFF123456.toInt(),
            isActive = true,
            defaultDurationMinutes = 120,
            notes = "Aula cambiada, preguntar al profesor",
            attachedFiles = listOf(
                AttachedFileItem(name = "apuntes.pdf", uriString = "content://doc/1")
            )
        )
        val fakeDao = FakeScheduleDao(
            initialSubjects = listOf(SubjectWithSlots(existingSubject, emptyList()))
        )
        val repository = ImportRepository(dao = fakeDao)

        repository.importSubjects(
            listOf(SelectedSubject(JsonSubject(code = "SO", name = "Sistemas Operativos"), "1º A"))
        )

        // The import carries schedule data only; it must not wipe what the user wrote.
        val (upserted, _) = fakeDao.upsertedSubjects.single()
        assertEquals("Aula cambiada, preguntar al profesor", upserted.notes)
        assertEquals(1, upserted.attachedFiles.size)
        assertEquals("apuntes.pdf", upserted.attachedFiles[0].name)
        assertEquals(120, upserted.defaultDurationMinutes)
    }

    @Test
    fun applySlotDiffs_updatesSubjectWithNewClassSlots() = runTest {
        val existingSubject = Subject(
            id = 10L,
            code = "FunProg1",
            name = "Fundamentos de Programación 1",
            color = 0xFF4CAF50.toInt(),
            courseGroup = "1º A",
            isActive = true,
            semester = 1
        )
        val fakeDao = FakeScheduleDao(
            initialSubjects = listOf(SubjectWithSlots(existingSubject, emptyList()))
        )
        val repository = ImportRepository(dao = fakeDao)

        val remoteSlot = JsonFlatSlot(
            grupo = "Grado 1.-A Bilingüe",
            cuatrimestre = "1C",
            dia = "Miercoles",
            horaInicio = "9:00",
            horaFin = "11:50",
            asignatura = "FunProg1",
            tipo = "Clase de laboratorio",
            aula = "A1.1",
            profesor = "Prof. Serrano",
            grupoPracticas = "A1"
        )
        val remoteSlots = listOf(remoteSlot, remoteSlot.copy(grupo = "Grado 1.-B Bilingüe"))
        val diffs = ScheduleDiffChecker.findDiffs(
            listOf(SubjectWithSlots(existingSubject, emptyList())), remoteSlots, currentSemester = 1
        )

        repository.applySlotDiffs(diffs = diffs, remoteSlots = remoteSlots, semester = 1)

        assertEquals(1, fakeDao.upsertedSubjects.size)
        val (upsertedSubject, newSlots) = fakeDao.upsertedSubjects[0]
        assertEquals(10L, upsertedSubject.id)
        assertEquals("FunProg1", upsertedSubject.code)
        assertEquals(1, newSlots.size)
        val slot = newSlots[0]
        assertEquals(java.time.DayOfWeek.WEDNESDAY, slot.dayOfWeek)
        assertEquals(java.time.LocalTime.of(9, 0), slot.startTime)
        assertEquals(java.time.LocalTime.of(11, 50), slot.endTime)
        assertEquals("A1.1", slot.classroom)
        assertEquals("Prof. Serrano", slot.professor)
        assertEquals(EntryType.LAB, slot.entryType)
        assertEquals("A1", slot.labGroupName)

        val preview = requireNotNull(diffs.single().newSlot)
        assertEquals(preview.dayOfWeek, slot.dayOfWeek)
        assertEquals(preview.startTime, slot.startTime)
        assertEquals(preview.endTime, slot.endTime)
        assertEquals(preview.entryType, slot.entryType)
    }

    @Test
    fun applySlotDiffs_respectsSelectedSubjectKeysAndPreservesIsIgnored() = runTest {
        val ignoredSlot = ClassSlot(
            id = 100L,
            subjectId = 10L,
            dayOfWeek = java.time.DayOfWeek.TUESDAY,
            startTime = java.time.LocalTime.of(10, 0),
            endTime = java.time.LocalTime.of(11, 30),
            entryType = com.marcoslorcar.clementime.data.EntryType.THEORY,
            isIgnored = true
        )
        val subj1 = Subject(
            id = 10L,
            code = "FunProg1",
            name = "Fundamentos de Programación 1",
            color = 0xFF4CAF50.toInt(),
            isActive = true,
            semester = 1
        )
        val subj2 = Subject(
            id = 20L,
            code = "Cálculo",
            name = "Cálculo",
            color = 0xFF2196F3.toInt(),
            isActive = true,
            semester = 1
        )
        val fakeDao = FakeScheduleDao(
            initialSubjects = listOf(
                SubjectWithSlots(subj1, listOf(ignoredSlot)),
                SubjectWithSlots(subj2, emptyList())
            )
        )
        val repository = ImportRepository(dao = fakeDao)

        val diffs = listOf(
            com.marcoslorcar.clementime.utils.SlotDiff(
                subjectCode = "FunProg1",
                subjectName = "Fundamentos de Programación 1",
                changeType = com.marcoslorcar.clementime.utils.DiffType.MODIFIED,
                subjectKey = "FUNPROG1__"
            ),
            com.marcoslorcar.clementime.utils.SlotDiff(
                subjectCode = "Cálculo",
                subjectName = "Cálculo",
                changeType = com.marcoslorcar.clementime.utils.DiffType.MODIFIED,
                subjectKey = "CALCULO__"
            )
        )

        val remoteSlots = listOf(
            com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Martes",
                horaInicio = "10:00",
                horaFin = "11:30",
                codigo = "FunProg1",
                asignatura = "Fundamentos de Programación 1",
                tipo = "teoría",
                aula = "A1.1"
            ),
            com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot(
                grupo = "1A",
                cuatrimestre = "1C",
                dia = "Viernes",
                horaInicio = "10:00",
                horaFin = "11:30",
                codigo = "Cálculo",
                asignatura = "Cálculo",
                tipo = "teoría",
                aula = "B2.2"
            )
        )

        // Only apply FunProg1; leave Cálculo untouched
        val applied = repository.applySlotDiffs(
            diffs = diffs,
            remoteSlots = remoteSlots,
            semester = 1,
            selectedSubjectKeys = setOf("FUNPROG1__")
        )

        assertEquals(1, applied)
        assertEquals(1, fakeDao.upsertedSubjects.size)
        val (upserted, slots) = fakeDao.upsertedSubjects.single()
        assertEquals("FunProg1", upserted.code)
        assertEquals(1, slots.size)
        assertEquals(true, slots.single().isIgnored)
    }

    @Test
    fun cacheMetadata_deserializesLegacyEntriesWithoutFacultyFields() {
        val legacyJson = """
            {
              "entries": [
                {
                  "id": "1C",
                  "title": "1st Semester",
                  "description": "ESI 1C",
                  "remotePath": "https://example.com/1C.json",
                  "lastUsed": 1700000000000,
                  "hash": "abc123",
                  "updatedTime": "2026-01-01"
                }
              ]
            }
        """.trimIndent()
        val parser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val decoded = parser.decodeFromString<RemoteCacheList>(legacyJson)
        assertEquals(1, decoded.entries.size)
        val entry = decoded.entries.single()
        assertEquals("1C", entry.id)
        assertNull(entry.universityId)
        assertNull(entry.facultyId)
        assertNull(entry.fcmTopicPrefix)
        assertNull(entry.termId)
    }

    @Test
    fun cacheMetadata_roundTripsWithFacultyMetadata() {
        val parser = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
        val list = RemoteCacheList(
            entries = listOf(
                CacheMetadata(
                    id = "uclm_esi_1C",
                    title = "1st Semester",
                    description = "ESI 1C",
                    remotePath = "https://example.com/1C.json",
                    lastUsed = 1700000000000L,
                    hash = "hash123",
                    updatedTime = "2026-09-01",
                    universityId = "uclm",
                    universityName = "UCLM",
                    facultyId = "esi",
                    facultyName = "Escuela Superior de Informática (ESI)",
                    fcmTopicPrefix = "uclm_esi",
                    termId = "1C"
                )
            )
        )
        val encoded = parser.encodeToString(list)
        val decoded = parser.decodeFromString<RemoteCacheList>(encoded)
        assertEquals(list, decoded)
    }

    @Test
    fun getCachedRemoteSlotsForSemester_returnsEmptyWhenContextIsNull() {
        val repository = ImportRepository(dao = FakeScheduleDao())
        val slots = repository.getCachedRemoteSlotsForSemester(
            context = null,
            semester = 1,
            universityId = "uclm",
            facultyId = "esi"
        )
        assertTrue(slots.isEmpty())
    }

    @Test
    fun fetchRemoteCatalog_retriesFallbackBaseAndWrapsLegacyIndex() = runTest {
        val customBase = "https://example.test/schedules"
        val fallbackBase = SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL.trimEnd('/')
        val term = RemoteScheduleSummary(id = "2C", title = "Second period", path = "2C.json", hash = "hash")
        val requestedUrls = mutableListOf<String>()
        val api = object : GitHubScheduleApiService {
            override suspend fun getScheduleCatalog(url: String): Response<ScheduleCatalog> {
                requestedUrls.add(url)
                return Response.error(404, "".toResponseBody())
            }

            override suspend fun getScheduleIndex(url: String): Response<List<RemoteScheduleSummary>> {
                requestedUrls.add(url)
                return if (url == "$fallbackBase/schedules_index.json") {
                    Response.success(listOf(term))
                } else {
                    Response.error(404, "".toResponseBody())
                }
            }

            override suspend fun getScheduleSchema(url: String): Response<ScheduleJsonSchema> =
                error("The catalog should not download a schedule")

            override suspend fun getRawScheduleSchema(url: String): Response<okhttp3.ResponseBody> =
                error("The catalog should not download a schedule")
        }
        val repository = ImportRepository(dao = FakeScheduleDao(), apiService = api)

        val (effectiveBaseUrl, catalog) = repository.fetchRemoteCatalog(customBase).getOrThrow()

        assertEquals(SettingsRepository.FALLBACK_GITHUB_REPO_BASE_URL, effectiveBaseUrl)
        val university = catalog.universities.single()
        assertEquals(SettingsRepository.LEGACY_FALLBACK_UNIVERSITY_ID, university.id)
        val faculty = university.faculties.single()
        assertEquals(SettingsRepository.LEGACY_FALLBACK_FACULTY_ID, faculty.id)
        assertEquals(term, faculty.terms.single().toRemoteSummary())
        assertEquals(
            listOf(
                "$customBase/schedules_catalog.json",
                "$customBase/schedules_index.json",
                "$fallbackBase/schedules_catalog.json",
                "$fallbackBase/schedules_index.json"
            ),
            requestedUrls
        )
    }

    @Test
    fun applySlotDiffs_doesNotDownloadWrongSemesterWhenTargetSummaryMissing() = runTest {
        val subject = Subject(
            id = 10L,
            code = "OPT3",
            name = "Optativa III",
            color = 0xFF4CAF50.toInt(),
            isActive = true,
            semester = 3
        )
        val fakeDao = FakeScheduleDao(
            initialSubjects = listOf(SubjectWithSlots(subject, emptyList()))
        )
        var rawFetchCount = 0
        val fakeApi = object : com.marcoslorcar.clementime.data.api.GitHubScheduleApiService {
            override suspend fun getScheduleCatalog(url: String) = retrofit2.Response.success(
                com.marcoslorcar.clementime.data.importing.model.ScheduleCatalog(
                    universities = listOf(
                        com.marcoslorcar.clementime.data.importing.model.UniversityCatalogEntry(
                            id = "uclm",
                            name = "UCLM",
                            faculties = listOf(
                                com.marcoslorcar.clementime.data.importing.model.FacultyCatalogEntry(
                                    id = "esi",
                                    name = "ESI",
                                    terms = listOf(
                                        com.marcoslorcar.clementime.data.importing.model.TermCatalogEntry(
                                            id = "1C",
                                            name = "1C",
                                            path = "1C.json",
                                            hash = "h1"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            override suspend fun getScheduleIndex(url: String) =
                retrofit2.Response.success(emptyList<com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary>())

            override suspend fun getScheduleSchema(url: String) =
                retrofit2.Response.success(com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema())

            override suspend fun getRawScheduleSchema(url: String): retrofit2.Response<okhttp3.ResponseBody> {
                rawFetchCount++
                return retrofit2.Response.success(
                    with(okhttp3.ResponseBody.Companion) { "[]".toResponseBody(null) }
                )
            }
        }
        val repository = ImportRepository(dao = fakeDao, apiService = fakeApi)
        val diffs = listOf(
            com.marcoslorcar.clementime.utils.SlotDiff(
                subjectCode = "OPT3",
                subjectName = "Optativa III",
                changeType = com.marcoslorcar.clementime.utils.DiffType.MODIFIED
            )
        )

        val applied = repository.applySlotDiffs(
            diffs = diffs,
            remoteSlots = emptyList(),
            semester = 3,
            facultyId = "esi",
            universityId = "uclm"
        )

        assertEquals(0, applied)
        assertEquals(0, rawFetchCount)
    }

    @Test
    fun cacheRemoteSlotsInDir_retainsAll8AcademicPeriodsAndEvictsBeyondCap() {
        val tempDir = java.nio.file.Files.createTempDirectory("clementime_cache_test").toFile()
        try {
            val cache = RemoteScheduleCache(Json)
            val totalEntries = RemoteScheduleCache.MAX_CACHED_REMOTE_SCHEDULES + 2
            for (i in 1..totalEntries) {
                val termCode = "${i}P"
                val rawJson = """[{"cuatrimestre":"$termCode","dia":"Lunes","hora_inicio":"09:00","hora_fin":"10:00","asignatura":"Sub $i","codigo":"S$i","tipo":"teoría","grupo":"1A"}]"""
                cache.cacheRemoteSlotsInDir(
                    cacheDir = tempDir,
                    fileId = "uni_fac_$termCode",
                    title = "Period $i",
                    description = null,
                    remotePath = "https://example.com/$termCode.json",
                    rawJson = rawJson,
                    universityId = "uni",
                    facultyId = "fac",
                    termId = termCode,
                    nowMillis = 1000L * i
                )
            }

            val metadata = cache.readCacheMetadataInDir(tempDir)
            assertEquals(RemoteScheduleCache.MAX_CACHED_REMOTE_SCHEDULES, metadata.size)
            // Periods 3..14 remain, while oldest two (1, 2) were evicted
            assertTrue(metadata.none { it.id == "uni_fac_1P" || it.id == "uni_fac_2P" })
            assertTrue(metadata.any { it.id == "uni_fac_8P" })
            assertTrue(RemoteScheduleCache.MAX_CACHED_REMOTE_SCHEDULES > com.marcoslorcar.clementime.data.PeriodConfig.MAX_PERIOD_COUNT)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun getCachedRemoteSlotsForSemesterInDir_resolvesTrimesterAndCustomTermCodesWithoutMetadata() {
        val tempDir = java.nio.file.Files.createTempDirectory("clementime_term_cache_test").toFile()
        try {
            val cache = RemoteScheduleCache(Json)
            val trimesterJson = """[{"cuatrimestre":"3T","dia":"Miércoles","hora_inicio":"10:00","hora_fin":"11:30","asignatura":"Optativa III","codigo":"OPT3","tipo":"teoría","grupo":"1A"}]"""
            java.io.File(tempDir, "cache_uni_fac_3T.json").writeText(trimesterJson)

            val slots = cache.getCachedRemoteSlotsForSemesterInDir(
                cacheDir = tempDir,
                semester = 3,
                universityId = "uni",
                facultyId = "fac",
                periodType = com.marcoslorcar.clementime.data.PeriodType.TRIMESTERS
            )
            assertEquals(1, slots.size)
            assertEquals("OPT3", slots.single().codigo)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
