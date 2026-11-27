package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.TermCodeParser
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.model.JsonGroup
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.JsonTimeSlot
import com.marcoslorcar.clementime.data.importing.model.JsonYear
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import kotlinx.serialization.json.Json
import java.time.DateTimeException
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

    fun exportToJson(
        subjectsWithSlots: List<SubjectWithSlots>,
        periodType: PeriodType = PeriodType.SEMESTERS
    ): String {
        val flatSlots = mutableListOf<JsonFlatSlot>()

        subjectsWithSlots.forEach { subjectWithSlots ->
            val (subject, slots) = subjectWithSlots
            val fullGroup = subject.courseGroup?.trim() ?: ""
            val cuatrimestreStr = TermCodeParser.formatTermCode(subject.semester, periodType)
            val asignaturaStr = subject.name
            val codigoStr = subject.code

            slots.forEach { slot ->
                val isLab = slot.entryType == EntryType.LAB
                val flatSlot = JsonFlatSlot(
                    grupo = fullGroup,
                    cuatrimestre = cuatrimestreStr,
                    dia = dayOfWeekToSpanish(slot.dayOfWeek),
                    horaInicio = slot.startTime.toString(),
                    horaFin = slot.endTime.toString(),
                    codigo = codigoStr,
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

        val rawTerms = flatSlots.map { it.cuatrimestre.trim().uppercase() }.filter { it.isNotBlank() }
        val distinctSemesters = flatSlots.map { TermCodeParser.parseTermIndex(it.cuatrimestre) ?: 1 }.distinct()
        val schemaSemester = distinctSemesters.singleOrNull()
        val title = when {
            distinctSemesters.size > 1 -> "Full Schedule"
            else -> {
                val sem = distinctSemesters.first()
                when {
                    rawTerms.any { it == "ANUAL" || it == "ANNUAL" || it == "YEAR" || it == "1A" || it == "A1" } -> "Anual"
                    rawTerms.any { it.endsWith("T") || it.startsWith("T") } -> "${sem}º Trimestre"
                    rawTerms.any { it.endsWith("Q") || it.startsWith("Q") } -> "${sem}º Cuarto"
                    sem == 1 -> "Primer Cuatrimestre"
                    sem == 2 -> "Segundo Cuatrimestre"
                    else -> "Periodo $sem"
                }
            }
        }

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

        for (slot in flatSlots) {
            val asig = slot.asignatura.trim()
            if (asig.isEmpty()) continue

            val slotSemester = TermCodeParser.parseTermIndex(slot.cuatrimestre) ?: 1
            val (yearName, groupName) = parseYearAndGroup(slot.grupo)
            val dayOfWeek = parseDayOfWeek(slot.dia).name
            val startTime = formatTime(slot.horaInicio)
            val endTime = formatTime(slot.horaFin)
            val isLab = slot.isLab()
            val isEvento = slot.tipo.equals("evento", ignoreCase = true)
            val entryType = if (isLab) "LAB" else "THEORY"
            val classroom = slot.aula.trim().takeIf { it.isNotEmpty() }
            var professor = slot.profesor.trim().takeIf { it.isNotEmpty() }
            val labGroup = slot.grupoPracticas.trim().takeIf { it.isNotEmpty() }

            if (isEvento) {
                professor = null
            }

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

            val subjectKey = "$asig|$slotSemester"
            val subjectAcc = targetSubjectMap.getOrPut(subjectKey) {
                val resolvedCode = slot.codigo ?: generateCode(asig)
                SubjectAccumulator(code = resolvedCode, name = asig, semester = slotSemester)
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
                isDummy = acc.isDummy
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
            semester = schemaSemester,
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

    private fun formatTime(time: String): String = try {
        parseLocalTime(time).toString()
    } catch (_: DateTimeException) {
        time
    }

    private fun generateCode(name: String): String {
        return name.split(" ")
            .filter { it.length > 2 }
            .joinToString("") { it.take(3).uppercase() }
            .ifBlank { name.take(5).uppercase().trim() }
    }

    // Mapping Helpers
    fun JsonTimeSlot.toClassSlot(subjectId: Long = 0): ClassSlot {
        return ClassSlot(
            subjectId = subjectId,
            dayOfWeek = parseDayOfWeek(this.dayOfWeek),
            startTime = parseLocalTime(this.startTime),
            endTime = parseLocalTime(this.endTime),
            classroom = this.classroom,
            labGroupName = this.groupName,
            entryType = runCatching { EntryType.valueOf(this.entryType.uppercase()) }.getOrDefault(EntryType.THEORY),
            professor = this.professor
        )
    }
}
