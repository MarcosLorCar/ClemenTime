package com.example.clementime.data.importing.repository

import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.model.SelectedMatter
import com.example.clementime.data.importing.parser.JsonScheduleParser
import javax.inject.Inject

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser()
) {

    fun parseJsonString(jsonContent: String): Result<ScheduleJsonSchema> {
        return parser.parseJson(jsonContent)
    }

    suspend fun importMatters(selectedMatters: List<SelectedMatter>) {
        selectedMatters.forEach { selected ->
            val jsonMatter = selected.matter
            val matter = Matter(
                code = jsonMatter.code,
                name = jsonMatter.name,
                color = jsonMatter.color ?: Matter.PRESET_COLORS.random(),
                courseGroup = selected.courseGroup,
                isActive = true
            )

            val theorySlots = jsonMatter.theorySlots.map {
                with(parser) { it.toClassSlot() }
            }

            val labSlots = jsonMatter.labVariants.flatMap { (groupName, variantSlots) ->
                variantSlots.map { slot ->
                    with(parser) {
                        slot.toClassSlot().copy(
                            labGroupName = groupName,
                            entryType = EntryType.LAB
                        )
                    }
                }
            }

            dao.upsertMatterWithSlots(matter, theorySlots + labSlots)
        }
    }
}