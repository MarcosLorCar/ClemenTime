package com.marcoslorcar.clementime.data.importing.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScheduleCatalogTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `test deserializes 3-level hierarchical catalog schema v2`() {
        val jsonString = """
            {
              "version": 2,
              "universities": [
                {
                  "id": "uclm",
                  "name": "Universidad de Castilla-La Mancha",
                  "faculties": [
                    {
                      "id": "esi",
                      "name": "Escuela Superior de Informática (Ciudad Real)",
                      "fcm_topic_prefix": "uclm_esi",
                      "subgroups_label": "Laboratorios",
                      "terms": [
                        {
                          "id": "1C",
                          "name": "Primer Cuatrimestre",
                          "description": "Horario oficial ESI UCLM - 1º Cuatrimestre",
                          "path": "1C.json",
                          "hash": "hash_1c",
                          "updatedTime": "2026-10-06"
                        },
                        {
                          "id": "2C",
                          "name": "Segundo Cuatrimestre",
                          "description": "Horario oficial ESI UCLM - 2º Cuatrimestre",
                          "path": "2C.json",
                          "hash": "hash_2c",
                          "updatedTime": "2026-10-06"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val catalog = json.decodeFromString<ScheduleCatalog>(jsonString)

        assertEquals(2, catalog.version)
        assertEquals(1, catalog.universities.size)
        val uni = catalog.universities[0]
        assertEquals("uclm", uni.id)
        assertEquals("Universidad de Castilla-La Mancha", uni.name)

        assertEquals(1, uni.faculties.size)
        val faculty = uni.faculties[0]
        assertEquals("esi", faculty.id)
        assertEquals("Escuela Superior de Informática (Ciudad Real)", faculty.name)
        assertEquals("uclm_esi", faculty.fcmTopicPrefix)
        assertEquals("Laboratorios", faculty.subgroupsLabel)

        assertEquals(2, faculty.terms.size)
        val term1 = faculty.terms[0]
        assertEquals("1C", term1.id)
        assertEquals("Primer Cuatrimestre", term1.name)
        assertEquals("1C.json", term1.path)
        assertEquals("hash_1c", term1.hash)

        // Verify conversion to RemoteScheduleSummary for backward compatibility
        val summary = term1.toRemoteSummary()
        assertEquals("1C", summary.id)
        assertEquals("Primer Cuatrimestre", summary.title)
        assertEquals("1C.json", summary.path)
        assertEquals("hash_1c", summary.hash)
        assertEquals("2026-10-06", summary.updatedTime)
    }

    @Test
    fun `test serializes catalog schema v2 roundtrip`() {
        val original = ScheduleCatalog(
            version = 2,
            universities = listOf(
                UniversityCatalogEntry(
                    id = "uclm",
                    name = "UCLM",
                    faculties = listOf(
                        FacultyCatalogEntry(
                            id = "derecho",
                            name = "Facultad de Derecho",
                            terms = listOf(
                                TermCatalogEntry(
                                    id = "1C",
                                    name = "1º Cuatrimestre",
                                    path = "derecho/1C.json"
                                )
                            )
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(ScheduleCatalog.serializer(), original)
        val decoded = json.decodeFromString<ScheduleCatalog>(encoded)

        assertEquals(original.version, decoded.version)
        assertEquals("derecho", decoded.universities[0].faculties[0].id)
        assertEquals(null, decoded.universities[0].faculties[0].subgroupsLabel)
    }
}
