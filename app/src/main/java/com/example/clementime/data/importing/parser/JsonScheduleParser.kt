package com.example.clementime.data.importing.parser

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.JsonTimeSlot
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import kotlinx.serialization.encodeToString
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
            json.decodeFromString<ScheduleJsonSchema>(jsonString)
        }
    }

    fun exportToJson(title: String, mattersWithSlots: List<MatterWithSlots>): String {
        val jsonMatters = mattersWithSlots.map { matterWithSlots ->
            val (matter, slots) = matterWithSlots

            val theorySlots = slots.filter { it.entryType != EntryType.LAB }.map { it.toJsonTimeSlot() }
            val labSlots = slots.filter { it.entryType == EntryType.LAB }

            val labVariants = if (labSlots.isNotEmpty()) {
                labSlots.groupBy { it.labGroupName ?: "Lab" }
                    .mapValues { entry -> entry.value.map { it.toJsonTimeSlot() } }
            } else {
                emptyMap()
            }

            JsonMatter(
                code = matter.code,
                name = matter.name,
                color = matter.color,
                courseGroup = matter.courseGroup,
                theorySlots = theorySlots,
                labVariants = labVariants
            )
        }

        val schema = ScheduleJsonSchema(
            title = title,
            matters = jsonMatters
        )

        return json.encodeToString(schema)
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