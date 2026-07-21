package com.example.clementime.data.importing.repository

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.importing.model.JsonMatter
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.parser.JsonScheduleParser
import javax.inject.Inject

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser()
) {

    fun parseJsonString(jsonContent: String): Result<ScheduleJsonSchema> {
        return parser.parseJson(jsonContent)
    }

    suspend fun importMatters(selectedJsonMatters: List<JsonMatter>) {
        selectedJsonMatters.forEach { jsonMatter ->
            val matter = Matter(
                code = jsonMatter.code,
                name = jsonMatter.name,
                color = jsonMatter.color ?: 0xFF2196F3.toInt(),
                courseGroup = jsonMatter.courseGroup
            )

            val slots = mutableListOf<ClassSlot>()

            // Theory slots (Always selected)
            jsonMatter.theorySlots.forEach {
                slots.add(with(parser) { it.toClassSlot().copy(isSelected = true) })
            }

            // All Lab Variants (Imported, but isSelected = false)
            jsonMatter.labVariants.forEach { (groupName, variantSlots) ->
                variantSlots.forEach { slot ->
                    slots.add(
                        with(parser) {
                            slot.toClassSlot().copy(
                                labGroupName = groupName,
                                entryType = EntryType.LAB,
                                isSelected = false // Unselected by default!
                            )
                        }
                    )
                }
            }

            dao.insertMatterWithSlots(matter, slots)
        }
    }
}