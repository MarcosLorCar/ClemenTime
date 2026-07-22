package com.example.clementime.data.importing.parser

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.importing.model.JsonGroup
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.JsonTimeSlot
import com.example.clementime.data.importing.model.JsonYear
import com.example.clementime.data.importing.model.ScheduleJsonSchema
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
        val yearMatters: MutableList<JsonMatter> = mutableListOf(),
        val groups: MutableMap<String, MutableList<JsonMatter>> = mutableMapOf()
    )

    fun parseJson(jsonString: String): Result<ScheduleJsonSchema> {
        return runCatching {
            json.decodeFromString<ScheduleJsonSchema>(jsonString)
        }
    }

    fun exportToJson(title: String, mattersWithSlots: List<MatterWithSlots>): String {
        val rootMatters = mutableListOf<JsonMatter>()
        val yearsMap = mutableMapOf<String, YearBuckets>()

        mattersWithSlots.forEach { matterWithSlots ->
            val (matter, slots) = matterWithSlots
            val jsonMatter = matterToPayload(matter, slots)
            val fullGroup = matter.courseGroup?.trim() ?: ""

            if (fullGroup.isEmpty() || fullGroup.equals("General", ignoreCase = true)) {
                rootMatters.add(jsonMatter)
            } else {
                val parts = fullGroup.split("\\s+".toRegex())
                val yearName = parts[0]
                val buckets = yearsMap.getOrPut(yearName) { YearBuckets() }

                if (parts.size == 1) {
                    buckets.yearMatters.add(jsonMatter)
                } else {
                    val groupName = parts.subList(1, parts.size).joinToString(" ")
                    buckets.groups.getOrPut(groupName) { mutableListOf() }.add(jsonMatter)
                }
            }
        }

        val jsonYears = yearsMap.map { (yearName, buckets) ->
            JsonYear(
                name = yearName,
                matters = buckets.yearMatters,
                groups = buckets.groups.map { (groupName, matters) ->
                    JsonGroup(name = groupName, matters = matters)
                }
            )
        }.sortedBy { it.name }

        val schema = ScheduleJsonSchema(
            title = title,
            matters = rootMatters,
            years = jsonYears
        )

        return json.encodeToString(schema)
    }

    private fun matterToPayload(matter: Matter, slots: List<ClassSlot>): JsonMatter {
        val theorySlots = slots.filter { it.entryType != EntryType.LAB }.map { it.toJsonTimeSlot() }
        val labSlots = slots.filter { it.entryType == EntryType.LAB }

        val labVariants = if (labSlots.isNotEmpty()) {
            labSlots.groupBy { it.labGroupName ?: "Lab" }
                .mapValues { entry -> entry.value.map { it.toJsonTimeSlot() } }
        } else {
            emptyMap()
        }

        return JsonMatter(
            code = matter.code,
            name = matter.name,
            color = matter.color,
            theorySlots = theorySlots,
            labVariants = labVariants
        )
    }

    // Mapping Helpers
    fun JsonTimeSlot.toClassSlot(matterId: Long = 0): ClassSlot {
        return ClassSlot(
            matterId = matterId,
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