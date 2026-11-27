package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class JsonScheduleParserTest {

    private val parser = JsonScheduleParser()

    @Test
    fun parseJson_flatArrayJson_parsesCorrectly() {
        val flatJson = """
            [
              {
                "grupo": "1A",
                "cuatrimestre": "1C",
                "dia": "Lunes",
                "hora_inicio": "8:30",
                "hora_fin": "10:00",
                "asignatura": "FunProg1",
                "tipo": "colaboración",
                "aula": "John von Neumann - A1.1",
                "profesor": "Jesus Serrano",
                "es_laboratorio": false,
                "grupo_practicas": ""
              },
              {
                "grupo": "1A",
                "cuatrimestre": "1C",
                "dia": "Lunes",
                "hora_inicio": "11:30",
                "hora_fin": "13:00",
                "asignatura": "Cálculo",
                "tipo": "Lab-A",
                "aula": "John von Neumann - A1.1",
                "profesor": "Maria Luz Lopez",
                "es_laboratorio": false,
                "grupo_practicas": "Lab-A1/A2"
              }
            ]
        """.trimIndent()

        val result = parser.parseJson(flatJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()

        assertEquals("Primer Cuatrimestre", schema.title)
        assertEquals(1, schema.semester)
        assertEquals(1, schema.years.size)

        val year = schema.years.first()
        assertEquals("1º", year.name)
        assertEquals(1, year.groups.size)

        val group = year.groups.first()
        assertEquals("A", group.name)
        assertEquals(2, group.subjects.size)

        val funProg = group.subjects.find { it.name == "FunProg1" }
        assertNotNull(funProg)
        assertEquals(1, funProg!!.theorySlots.size)
        assertEquals("MONDAY", funProg.theorySlots.first().dayOfWeek)
        assertEquals("08:30", funProg.theorySlots.first().startTime)

        val calculo = group.subjects.find { it.name == "Cálculo" }
        assertNotNull(calculo)
        assertTrue(calculo!!.labVariants.containsKey("Lab-A1/A2"))
    }

    @Test
    fun parseJson_secondSemesterFlatArray_infersSemesterAndTitle() {
        val flatJson = """
            [
              {
                "grupo": "2A",
                "cuatrimestre": "2C",
                "dia": "Martes",
                "hora_inicio": "10:00",
                "hora_fin": "11:30",
                "asignatura": "Redes II",
                "tipo": "teoría"
              }
            ]
        """.trimIndent()

        val result = parser.parseJson(flatJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()

        assertEquals("Segundo Cuatrimestre", schema.title)
        assertEquals(2, schema.semester)
    }

    @Test
    fun parseDayOfWeek_handlesSpanishAndEnglish() {
        assertEquals(DayOfWeek.MONDAY, parseDayOfWeek("Lunes"))
        assertEquals(DayOfWeek.TUESDAY, parseDayOfWeek("Martes"))
        assertEquals(DayOfWeek.WEDNESDAY, parseDayOfWeek("Miércoles"))
        assertEquals(DayOfWeek.WEDNESDAY, parseDayOfWeek("Miercoles"))
        assertEquals(DayOfWeek.THURSDAY, parseDayOfWeek("Jueves"))
        assertEquals(DayOfWeek.FRIDAY, parseDayOfWeek("Viernes"))
        assertEquals(DayOfWeek.SATURDAY, parseDayOfWeek("Sábado"))
        assertEquals(DayOfWeek.SUNDAY, parseDayOfWeek("Domingo"))
        assertEquals(DayOfWeek.MONDAY, parseDayOfWeek("MONDAY"))
    }

    @Test
    fun exportToJson_roundTripPreservesSubjectNameAndEndTime() {
        val subject = Subject(
            id = 1L,
            code = "FP1",
            name = "Universidad de Mayores",
            color = 0xFF4CAF50.toInt(),
            courseGroup = "1º A",
            isActive = true,
            semester = 1
        )
        val slot = ClassSlot(
            id = 1L,
            subjectId = 1L,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(9, 50),
            classroom = "A1.1",
            professor = "Jesus Serrano",
            entryType = EntryType.THEORY
        )

        val exported = parser.exportToJson(listOf(SubjectWithSlots(subject, listOf(slot))))
        assertTrue(exported.trim().startsWith("["))
        // Matches what the Python pipeline emits in schedules/dist/*.json:
        // "codigo" is the short code, "asignatura" the full display name.
        assertTrue(exported.contains("\"codigo\": \"FP1\""))
        assertTrue(exported.contains("\"asignatura\": \"Universidad de Mayores\""))
        assertTrue(exported.contains("\"dia\": \"Lunes\""))

        val schema = parser.parseJson(exported).getOrThrow()
        val imported = schema.years.single().groups.single().subjects.single()
        assertEquals(subject.name, imported.name)
        val importedSlot = with(parser) { imported.theorySlots.single().toClassSlot(subject.id) }
        assertEquals(slot.copy(id = 0), importedSlot)
    }

    @Test
    fun parseJson_globalEvents_deduplicatesSlots() {
        val flatJson = """
            [
              {
                "grupo": "1A",
                "cuatrimestre": "1C",
                "dia": "Lunes",
                "hora_inicio": "08:30",
                "hora_fin": "10:00",
                "asignatura": "Pruebas de Progreso",
                "tipo": "evento",
                "aula": "0.02-Charles Babbage",
                "profesor": ""
              },
              {
                "grupo": "1B",
                "cuatrimestre": "1C",
                "dia": "Lunes",
                "hora_inicio": "08:30",
                "hora_fin": "10:00",
                "asignatura": "Pruebas de Progreso",
                "tipo": "evento",
                "aula": "0.02-Charles Babbage",
                "profesor": "Charles"
              },
              {
                "grupo": "1A",
                "cuatrimestre": "1C",
                "dia": "Miércoles",
                "hora_inicio": "11:30",
                "hora_fin": "13:00",
                "asignatura": "Conferencias",
                "tipo": "evento",
                "aula": "Alan Turing",
                "profesor": ""
              },
              {
                "grupo": "1B",
                "cuatrimestre": "1C",
                "dia": "Miércoles",
                "hora_inicio": "11:30",
                "hora_fin": "13:00",
                "asignatura": "Conferencias",
                "tipo": "evento",
                "aula": "Alan Turing",
                "profesor": "ESI"
              }
            ]
        """.trimIndent()

        val result = parser.parseJson(flatJson)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()

        val pruebas = schema.subjects.find { it.name == "Pruebas de Progreso" }
        assertNotNull(pruebas)
        assertEquals(1, pruebas!!.theorySlots.size)
        assertEquals("0.02-Charles Babbage", pruebas.theorySlots.first().classroom)

        val conferencias = schema.subjects.find { it.name == "Conferencias" }
        assertNotNull(conferencias)
        assertEquals(1, conferencias!!.theorySlots.size)
        assertEquals("Alan Turing", conferencias.theorySlots.first().classroom)
    }

    @Test
    fun parseJson_distFiles_noDuplicateEventSlots() {
        val distDir = java.io.File("schedules/dist")
        if (!distDir.exists()) return

        for (file in listOf(java.io.File(distDir, "1C.json"), java.io.File(distDir, "2C.json"))) {
            if (!file.exists()) continue
            val jsonStr = file.readText()
            val result = parser.parseJson(jsonStr)
            assertTrue(result.isSuccess)
            val schema = result.getOrThrow()

            val pruebas = schema.subjects.find { it.name == "Pruebas de Progreso" }
            if (pruebas != null) {
                assertEquals("Expected exactly 2 slots for Pruebas de Progreso in ${file.name}", 2, pruebas.theorySlots.size)
            }

            val conferencias = schema.subjects.find { it.name == "Conferencias" }
            if (conferencias != null) {
                assertEquals("Expected exactly 1 slot for Conferencias in ${file.name}", 1, conferencias.theorySlots.size)
            }
        }
    }

    @Test
    fun parseJson_trimesterAndJsonNamesAliases_parseCorrectly() {
        val jsonStr = """
            [
              {
                "grupo": "1A",
                "trimestre": "3T",
                "dia": "Miércoles",
                "hora_inicio": "09:00",
                "hora_fin": "10:30",
                "codigo": "BIO3",
                "asignatura": "Bioquímica III",
                "tipo": "teoría"
              }
            ]
        """.trimIndent()

        val result = parser.parseJson(jsonStr)
        assertTrue(result.isSuccess)
        val schema = result.getOrThrow()
        assertEquals(3, schema.semester)
        assertEquals("3º Trimestre", schema.title)
        val subject = schema.years.single().groups.single().subjects.single()
        assertEquals(3, subject.semester)
        assertEquals("BIO3", subject.code)
    }

    @Test
    fun exportToJson_multiPeriodRoundTrip_preservesAllPeriods() {
        val s1 = Subject(
            id = 1L,
            code = "T1",
            name = "Term One Subject",
            color = 0xFF4CAF50.toInt(),
            courseGroup = "1º A",
            isActive = true,
            semester = 1
        )
        val s3 = Subject(
            id = 2L,
            code = "T3",
            name = "Term Three Subject",
            color = 0xFF2196F3.toInt(),
            courseGroup = "1º A",
            isActive = true,
            semester = 3
        )
        val slot1 = ClassSlot(1L, 1L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 30), entryType = EntryType.THEORY)
        val slot3 = ClassSlot(2L, 2L, DayOfWeek.FRIDAY, LocalTime.of(11, 0), LocalTime.of(12, 30), entryType = EntryType.THEORY)

        val exported = parser.exportToJson(
            subjectsWithSlots = listOf(SubjectWithSlots(s1, listOf(slot1)), SubjectWithSlots(s3, listOf(slot3))),
            periodType = PeriodType.TRIMESTERS
        )
        assertTrue(exported.contains("\"cuatrimestre\": \"1T\""))
        assertTrue(exported.contains("\"cuatrimestre\": \"3T\""))

        val reimported = parser.parseJson(exported).getOrThrow()
        assertEquals(null, reimported.semester)
        val subjects = reimported.years.single().groups.single().subjects
        assertEquals(1, subjects.first { it.code == "T1" }.semester)
        assertEquals(3, subjects.first { it.code == "T3" }.semester)
    }

    @Test
    fun exportToJson_customPeriodType_formatsCustomTermCodes() {
        val s5 = Subject(
            id = 5L,
            code = "C5",
            name = "Custom Period 5",
            color = 0xFF4CAF50.toInt(),
            courseGroup = "1º A",
            isActive = true,
            semester = 5
        )
        val slot5 = ClassSlot(5L, 5L, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30), entryType = EntryType.THEORY)

        val exported = parser.exportToJson(
            subjectsWithSlots = listOf(SubjectWithSlots(s5, listOf(slot5))),
            periodType = PeriodType.CUSTOM
        )
        assertTrue(exported.contains("\"cuatrimestre\": \"5P\""))
    }
}
