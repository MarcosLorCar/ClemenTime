package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.JsonGroup
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.JsonTimeSlot
import com.marcoslorcar.clementime.data.importing.model.JsonYear
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

class JsonScheduleParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun parseJson(jsonString: String): Result<ScheduleJsonSchema> {
        return runCatching {
            val cleanJson = jsonString.trimStart('\uFEFF').trim()
            if (cleanJson.startsWith("[")) {
                val flatSlots = json.decodeFromString<List<JsonFlatSlot>>(cleanJson)
                flatSlotsToSchema(flatSlots)
            } else {
                json.decodeFromString<ScheduleJsonSchema>(cleanJson)
            }
        }
    }

    fun exportToJson(@Suppress("UNUSED_PARAMETER") title: String, subjectsWithSlots: List<SubjectWithSlots>): String {
        val flatSlots = mutableListOf<JsonFlatSlot>()

        subjectsWithSlots.forEach { subjectWithSlots ->
            val (subject, slots) = subjectWithSlots
            val fullGroup = subject.courseGroup?.trim() ?: ""
            val cuatrimestreStr = if (subject.semester == 2) "2C" else "1C"
            val asignaturaStr = subject.code.ifBlank { subject.name }

            slots.forEach { slot ->
                val isLab = slot.entryType == EntryType.LAB
                val flatSlot = JsonFlatSlot(
                    grupo = fullGroup,
                    cuatrimestre = cuatrimestreStr,
                    dia = dayOfWeekToSpanish(slot.dayOfWeek),
                    horaInicio = slot.startTime.toString(),
                    horaFin = slot.endTime.toString(),
                    asignatura = asignaturaStr,
                    tipo = if (isLab) "laboratorio" else "teoría",
                    aula = slot.classroom ?: "",
                    profesor = slot.professor ?: "",
                    esLaboratorio = isLab,
                    grupoPracticas = slot.labGroupName ?: ""
                )
                flatSlots.add(flatSlot)
            }
        }

        return json.encodeToString(flatSlots)
    }

    private fun flatSlotsToSchema(flatSlots: List<JsonFlatSlot>): ScheduleJsonSchema {
        if (flatSlots.isEmpty()) {
            return ScheduleJsonSchema(title = "Primer Cuatrimestre", semester = 1)
        }

        val semesters = flatSlots.map { it.cuatrimestre.trim().uppercase() }.filter { it.isNotBlank() }
        val isSecondSemester = semesters.isNotEmpty() && semesters.all { it == "2C" || it == "2" }
        val semesterInt = if (isSecondSemester) 2 else 1
        val title = if (isSecondSemester) "Segundo Cuatrimestre" else "Primer Cuatrimestre"

        class SubjectAccumulator(
            val code: String,
            val name: String,
            val semester: Int
        ) {
            var isDummy: Boolean = false
            val theorySlots = mutableListOf<JsonTimeSlot>()
            val labVariants = mutableMapOf<String, MutableList<JsonTimeSlot>>()
        }

        class GroupBucket(val name: String) {
            val subjects = mutableMapOf<String, SubjectAccumulator>()
        }

        class YearBucket(val name: String) {
            val commonSubjects = mutableMapOf<String, SubjectAccumulator>()
            val groups = mutableMapOf<String, GroupBucket>()
        }

        val rootSubjects = mutableMapOf<String, SubjectAccumulator>()
        val years = mutableMapOf<String, YearBucket>()

        val GLOBAL_EVENT_NAMES = setOf("Pruebas de Progreso", "Conferencias", "PruebasProgreso")

        for (slot in flatSlots) {
            val asig = slot.asignatura.trim()
            if (asig.isEmpty()) continue

            if (asig.contains("universidad de mayores", ignoreCase = true) || asig.contains("univmayores", ignoreCase = true)) {
                continue
            }

            val (yearName, groupName) = parseYearAndGroup(slot.grupo)
            val dayOfWeek = parseDayOfWeek(slot.dia).name
            val startTime = formatTime(slot.horaInicio, isEndTime = false)
            val endTime = formatTime(slot.horaFin, isEndTime = true)
            val isLab = slot.esLaboratorio || slot.tipo.equals("laboratorio", ignoreCase = true) || slot.tipo.equals("LAB", ignoreCase = true)
            val isEvento = slot.tipo.equals("evento", ignoreCase = true) || asig in GLOBAL_EVENT_NAMES
            val entryType = if (isLab) "LAB" else "THEORY"
            val classroom = slot.aula.trim().takeIf { it.isNotEmpty() }
            val professor = slot.profesor.trim().takeIf { it.isNotEmpty() }
            val labGroup = slot.grupoPracticas.trim().takeIf { it.isNotEmpty() }

            val timeSlot = JsonTimeSlot(
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                classroom = classroom,
                groupName = labGroup,
                entryType = entryType,
                professor = professor
            )

            val targetSubjectMap: MutableMap<String, SubjectAccumulator> = when {
                yearName == null || isEvento -> rootSubjects
                groupName == null -> years.getOrPut(yearName) { YearBucket(yearName) }.commonSubjects
                else -> {
                    val yearBucket = years.getOrPut(yearName) { YearBucket(yearName) }
                    val groupBucket = yearBucket.groups.getOrPut(groupName) { GroupBucket(groupName) }
                    groupBucket.subjects
                }
            }

            val subjectAcc = targetSubjectMap.getOrPut(asig) {
                SubjectAccumulator(code = asig, name = asig, semester = semesterInt)
            }
            if (isEvento) {
                subjectAcc.isDummy = true
            }

            if (isLab) {
                val variantName = labGroup ?: "Lab"
                val slotsList = subjectAcc.labVariants.getOrPut(variantName) { mutableListOf() }
                if (timeSlot !in slotsList) {
                    slotsList.add(timeSlot)
                }
            } else {
                if (timeSlot !in subjectAcc.theorySlots) {
                    subjectAcc.theorySlots.add(timeSlot)
                }
            }
        }

        fun createJsonSubject(acc: SubjectAccumulator): JsonSubject {
            return JsonSubject(
                code = acc.code,
                name = acc.name,
                semester = acc.semester,
                theorySlots = acc.theorySlots,
                labVariants = acc.labVariants,
                isDummy = acc.isDummy || acc.name in GLOBAL_EVENT_NAMES || acc.code in GLOBAL_EVENT_NAMES
            )
        }

        val jsonRootSubjects = rootSubjects.values.map { createJsonSubject(it) }

        val jsonYears = years.values.map { yAcc ->
            JsonYear(
                name = yAcc.name,
                subjects = yAcc.commonSubjects.values.map { createJsonSubject(it) },
                groups = yAcc.groups.values.map { gAcc ->
                    JsonGroup(
                        name = gAcc.name,
                        subjects = gAcc.subjects.values.map { createJsonSubject(it) }
                    )
                }.sortedBy { it.name }
            )
        }.sortedBy { it.name }

        return ScheduleJsonSchema(
            version = 1,
            title = title,
            semester = semesterInt,
            subjects = jsonRootSubjects,
            years = jsonYears
        )
    }

    private fun parseYearAndGroup(grupoStr: String): Pair<String?, String?> {
        val trimmed = grupoStr.trim()
        if (trimmed.isEmpty() || trimmed.equals("General", ignoreCase = true)) {
            return Pair(null, null)
        }

        val regex1 = Regex("""^(\d+)([A-Z])$""", RegexOption.IGNORE_CASE)
        val match1 = regex1.matchEntire(trimmed)
        if (match1 != null) {
            val (yearNum, groupChar) = match1.destructured
            return Pair("${yearNum}º", groupChar.uppercase())
        }

        val parts = trimmed.split(Regex("""\s+"""))
        if (parts.size > 1) {
            val rawYear = parts[0]
            val yearName = if (rawYear.all { it.isDigit() }) "${rawYear}º" else rawYear
            val groupName = parts.subList(1, parts.size).joinToString(" ")
            return Pair(yearName, groupName)
        }

        val yearName = if (trimmed.all { it.isDigit() }) "${trimmed}º" else trimmed
        return Pair(yearName, null)
    }

    private fun formatTime(timeStr: String, isEndTime: Boolean = false): String {
        if (timeStr.isBlank()) return timeStr
        val parts = timeStr.trim().split(":")
        if (parts.size == 2) {
            var hours = parts[0].toIntOrNull() ?: return timeStr
            var minutes = parts[1].toIntOrNull() ?: return timeStr

            if (isEndTime && minutes == 50) {
                hours = (hours + 1) % 24
                minutes = 0
            }
            return "%02d:%02d".format(hours, minutes)
        }
        return timeStr
    }

    fun parseDayOfWeek(dayStr: String): DayOfWeek {
        val clean = dayStr.trim().uppercase()
        return when (clean) {
            "LUNES", "MONDAY" -> DayOfWeek.MONDAY
            "MARTES", "TUESDAY" -> DayOfWeek.TUESDAY
            "MIÉRCOLES", "MIERCOLES", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
            "JUEVES", "THURSDAY" -> DayOfWeek.THURSDAY
            "VIERNES", "FRIDAY" -> DayOfWeek.FRIDAY
            "SÁBADO", "SABADO", "SATURDAY" -> DayOfWeek.SATURDAY
            "DOMINGO", "SUNDAY" -> DayOfWeek.SUNDAY
            else -> runCatching { DayOfWeek.valueOf(clean) }.getOrDefault(DayOfWeek.MONDAY)
        }
    }

    private fun dayOfWeekToSpanish(day: DayOfWeek): String {
        return when (day) {
            DayOfWeek.MONDAY -> "Lunes"
            DayOfWeek.TUESDAY -> "Martes"
            DayOfWeek.WEDNESDAY -> "Miércoles"
            DayOfWeek.THURSDAY -> "Jueves"
            DayOfWeek.FRIDAY -> "Viernes"
            DayOfWeek.SATURDAY -> "Sábado"
            DayOfWeek.SUNDAY -> "Domingo"
        }
    }

    // Mapping Helpers
    fun JsonTimeSlot.toClassSlot(subjectId: Long = 0): ClassSlot {
        return ClassSlot(
            subjectId = subjectId,
            dayOfWeek = parseDayOfWeek(this.dayOfWeek),
            startTime = LocalTime.parse(this.startTime),
            endTime = LocalTime.parse(this.endTime),
            classroom = this.classroom,
            labGroupName = this.groupName,
            entryType = runCatching { EntryType.valueOf(this.entryType.uppercase()) }.getOrDefault(EntryType.THEORY),
            professor = this.professor
        )
    }
}