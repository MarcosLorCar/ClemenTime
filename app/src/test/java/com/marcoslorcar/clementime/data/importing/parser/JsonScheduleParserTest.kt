package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
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
                "tipo": "teoría",
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
                "tipo": "laboratorio",
                "aula": "John von Neumann - A1.1",
                "profesor": "Maria Luz Lopez",
                "es_laboratorio": true,
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
        assertEquals(DayOfWeek.MONDAY, parser.parseDayOfWeek("Lunes"))
        assertEquals(DayOfWeek.TUESDAY, parser.parseDayOfWeek("Martes"))
        assertEquals(DayOfWeek.WEDNESDAY, parser.parseDayOfWeek("Miércoles"))
        assertEquals(DayOfWeek.WEDNESDAY, parser.parseDayOfWeek("Miercoles"))
        assertEquals(DayOfWeek.THURSDAY, parser.parseDayOfWeek("Jueves"))
        assertEquals(DayOfWeek.FRIDAY, parser.parseDayOfWeek("Viernes"))
        assertEquals(DayOfWeek.SATURDAY, parser.parseDayOfWeek("Sábado"))
        assertEquals(DayOfWeek.SUNDAY, parser.parseDayOfWeek("Domingo"))
        assertEquals(DayOfWeek.MONDAY, parser.parseDayOfWeek("MONDAY"))
    }

    @Test
    fun exportToJson_exportsFlatArrayJsonFormat() {
        val subject = Subject(
            id = 1L,
            code = "FP1",
            name = "Fundamentos de Programación I",
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
            endTime = LocalTime.of(10, 0),
            classroom = "A1.1",
            professor = "Jesus Serrano",
            entryType = EntryType.THEORY
        )

        val exported = parser.exportToJson("Primer Cuatrimestre", listOf(SubjectWithSlots(subject, listOf(slot))))
        assertTrue(exported.trim().startsWith("["))
        assertTrue(exported.contains("\"asignatura\": \"FP1\""))
        assertTrue(exported.contains("\"dia\": \"Lunes\""))
    }
}
