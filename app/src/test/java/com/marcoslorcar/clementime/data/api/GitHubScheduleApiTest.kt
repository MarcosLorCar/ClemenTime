package com.marcoslorcar.clementime.data.api

import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.repository.FakeScheduleDaoForRepositoryTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitHubScheduleApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testParseSchedulesIndexJson() {
        val jsonPayload = """
            [
                {
                    "id": "primer_cuatrimestre_2026",
                    "title": "Primer Cuatrimestre 2026/27",
                    "description": "Horario oficial Grado en Ingeniería Informática",
                    "path": "schedules/primer_cuatrimestre_2026.json",
                    "updatedTime": "2026-07-24"
                }
            ]
        """.trimIndent()

        val list = json.decodeFromString<List<RemoteScheduleSummary>>(jsonPayload)
        assertEquals(1, list.size)
        assertEquals("primer_cuatrimestre_2026", list[0].id)
        assertEquals("Primer Cuatrimestre 2026/27", list[0].title)
        assertEquals("schedules/primer_cuatrimestre_2026.json", list[0].path)
    }

    @Test
    fun testParseRemoteScheduleSchemaJson() {
        val jsonPayload = """
            {
                "version": 1,
                "title": "Primer Cuatrimestre 2026/27",
                "matters": [
                    {
                        "code": "SO",
                        "name": "Sistemas Operativos",
                        "theorySlots": [
                            {
                                "dayOfWeek": "MONDAY",
                                "startTime": "08:30",
                                "endTime": "10:00",
                                "classroom": "A1.1",
                                "entryType": "THEORY"
                            }
                        ],
                        "labVariants": {
                            "Lab-A1": [
                                {
                                    "dayOfWeek": "TUESDAY",
                                    "startTime": "10:00",
                                    "endTime": "11:30",
                                    "classroom": "L1.2",
                                    "entryType": "LAB"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val schema = json.decodeFromString<ScheduleJsonSchema>(jsonPayload)
        assertEquals(1, schema.version)
        assertEquals("Primer Cuatrimestre 2026/27", schema.title)
        assertEquals(1, schema.subjects.size)
        val subject = schema.subjects[0]
        assertEquals("SO", subject.code)
        assertEquals("Sistemas Operativos", subject.name)
        assertEquals(1, subject.theorySlots.size)
        assertEquals("MONDAY", subject.theorySlots[0].dayOfWeek)
        assertNotNull(subject.labVariants["Lab-A1"])
        assertEquals(1, subject.labVariants["Lab-A1"]?.size)
    }

    @Test
    fun testNormalizeGitHubUrl() {
        val repo = com.marcoslorcar.clementime.data.importing.repository.ImportRepository(
            dao = FakeScheduleDaoForRepositoryTest()
        )

        val webUrl = "https://github.com/MarcosLorCar/ClemenTime/tree/master/schedules/dist"
        val expectedRaw = "https://raw.githubusercontent.com/MarcosLorCar/ClemenTime/master/schedules/dist"

        assertEquals(expectedRaw, repo.normalizeGitHubUrl(webUrl))
    }
}
