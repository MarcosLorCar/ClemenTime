package com.marcoslorcar.clementime.ui.model

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import java.time.DayOfWeek
import java.time.LocalTime

data class ClassSlotUiModel(
    val id: Long = 0L,
    val subjectId: Long = 0L,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val classroom: String? = null,
    val labGroupName: String? = null,
    val entryType: EntryType = EntryType.THEORY,
    val professor: String? = null,
    val isIgnored: Boolean = false
)

fun ClassSlot.toUiModel(): ClassSlotUiModel = ClassSlotUiModel(
    id = id,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    classroom = classroom,
    labGroupName = labGroupName,
    entryType = entryType,
    professor = professor,
    isIgnored = isIgnored
)

fun ClassSlotUiModel.toEntity(subjectId: Long): ClassSlot? {
    val start = startTime ?: return null
    val end = endTime ?: return null
    return ClassSlot(
        id = id,
        subjectId = subjectId,
        dayOfWeek = dayOfWeek,
        startTime = start,
        endTime = end,
        classroom = classroom,
        labGroupName = labGroupName,
        entryType = entryType,
        professor = professor,
        isIgnored = isIgnored
    )
}
