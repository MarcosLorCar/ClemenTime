package com.marcoslorcar.clementime.data.importing.parser

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
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

    private data class YearBuckets(
        val yearSubjects: MutableList<JsonSubject> = mutableListOf(),
        val groups: MutableMap<String, MutableList<JsonSubject>> = mutableMapOf()
    )

    fun parseJson(jsonString: String): Result<ScheduleJsonSchema> {
        return runCatching {
            json.decodeFromString<ScheduleJsonSchema>(jsonString)
        }
    }

    fun exportToJson(title: String, subjectsWithSlots: List<SubjectWithSlots>): String {
        val rootSubjects = mutableListOf<JsonSubject>()
        val yearsMap = mutableMapOf<String, YearBuckets>()

        subjectsWithSlots.forEach { subjectWithSlots ->
            val (subject, slots) = subjectWithSlots
            val jsonSubject = subjectToPayload(subject, slots)
            val fullGroup = subject.courseGroup?.trim() ?: ""

            if (fullGroup.isEmpty() || fullGroup.equals("General", ignoreCase = true)) {
                rootSubjects.add(jsonSubject)
            } else {
                val parts = fullGroup.split("\\s+".toRegex())
                val yearName = parts[0]
                val buckets = yearsMap.getOrPut(yearName) { YearBuckets() }

                if (parts.size == 1) {
                    buckets.yearSubjects.add(jsonSubject)
                } else {
                    val groupName = parts.subList(1, parts.size).joinToString(" ")
                    buckets.groups.getOrPut(groupName) { mutableListOf() }.add(jsonSubject)
                }
            }
        }

        val jsonYears = yearsMap.map { (yearName, buckets) ->
            JsonYear(
                name = yearName,
                subjects = buckets.yearSubjects,
                groups = buckets.groups.map { (groupName, subjects) ->
                    JsonGroup(name = groupName, subjects = subjects)
                }
            )
        }.sortedBy { it.name }

        val schema = ScheduleJsonSchema(
            title = title,
            subjects = rootSubjects,
            years = jsonYears
        )

        return json.encodeToString(schema)
    }

    private fun subjectToPayload(subject: Subject, slots: List<ClassSlot>): JsonSubject {
        val theorySlots = slots.filter { it.entryType != EntryType.LAB }.map { it.toJsonTimeSlot() }
        val labSlots = slots.filter { it.entryType == EntryType.LAB }

        val labVariants = if (labSlots.isNotEmpty()) {
            labSlots.groupBy { it.labGroupName ?: "Lab" }
                .mapValues { entry -> entry.value.map { it.toJsonTimeSlot() } }
        } else {
            emptyMap()
        }

        return JsonSubject(
            code = subject.code,
            name = subject.name,
            color = subject.color,
            theorySlots = theorySlots,
            labVariants = labVariants,
            isDummy = subject.isDummy
        )
    }

    // Mapping Helpers
    fun JsonTimeSlot.toClassSlot(subjectId: Long = 0): ClassSlot {
        return ClassSlot(
            subjectId = subjectId,
            dayOfWeek = DayOfWeek.valueOf(this.dayOfWeek.uppercase()),
            startTime = LocalTime.parse(this.startTime),
            endTime = LocalTime.parse(this.endTime),
            classroom = this.classroom,
            labGroupName = this.groupName,
            entryType = runCatching { EntryType.valueOf(this.entryType.uppercase()) }.getOrDefault(EntryType.THEORY),
            professor = this.professor
        )
    }

    private fun ClassSlot.toJsonTimeSlot(): JsonTimeSlot {
        return JsonTimeSlot(
            dayOfWeek = this.dayOfWeek.name,
            startTime = this.startTime.toString(),
            endTime = this.endTime.toString(),
            classroom = this.classroom,
            groupName = this.labGroupName,
            entryType = this.entryType.name,
            professor = this.professor
        )
    }
}