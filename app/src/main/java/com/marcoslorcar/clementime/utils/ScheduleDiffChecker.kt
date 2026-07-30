package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import java.time.DayOfWeek
import java.time.LocalTime

enum class DiffType { MODIFIED, ADDED, REMOVED }

data class SlotDiff(
    val subjectCode: String,
    val subjectName: String,
    val changeType: DiffType,
    val oldDetail: String? = null,
    val newDetail: String? = null
)

object ScheduleDiffChecker {

    private data class ParsedRemoteSlot(
        val dayOfWeek: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val classroom: String,
        val professor: String,
        val isLab: Boolean,
        val labGroupName: String
    )

    fun findDiffs(
        existingSubjects: List<SubjectWithSlots>,
        remoteSlots: List<JsonFlatSlot>,
        currentSemester: Int
    ): List<SlotDiff> {
        val diffs = mutableListOf<SlotDiff>()
        val targetSemesterCode = "${currentSemester}C"

        val semesterRemoteSlots = remoteSlots.filter { slot ->
            val slotCuat = slot.cuatrimestre.trim()
            slotCuat.equals(targetSemesterCode, ignoreCase = true) || slotCuat == "$currentSemester"
        }

        val activeSubjects = existingSubjects.filter { it.subject.isActive }

        for (subjectWithSlots in activeSubjects) {
            val subject = subjectWithSlots.subject
            val existingSlots = subjectWithSlots.slots

            val matchedJsonSlots = semesterRemoteSlots.filter { rSlot ->
                val asigNorm = normalizeText(rSlot.asignatura)
                val codeNorm = normalizeText(subject.code)
                val nameNorm = normalizeText(subject.name)

                val codeMatch = codeNorm.isNotBlank() && asigNorm == codeNorm
                val nameMatch = nameNorm.isNotBlank() && asigNorm == nameNorm

                val subjectGroupNorm = normalizeGroup(subject.courseGroup)
                val rSlotGroupNorm = normalizeGroup(rSlot.grupo)
                val groupMatch = subject.courseGroup.isNullOrBlank() ||
                        rSlot.grupo.isBlank() ||
                        subjectGroupNorm == rSlotGroupNorm ||
                        (subjectGroupNorm.isNotBlank() && rSlotGroupNorm.contains(subjectGroupNorm)) ||
                        (rSlotGroupNorm.isNotBlank() && subjectGroupNorm.contains(rSlotGroupNorm))

                (codeMatch || nameMatch) && groupMatch
            }

            val parsedRemoteSlots = matchedJsonSlots.map { rSlot ->
                val isLab = rSlot.esLaboratorio ||
                        rSlot.tipo.contains("laboratorio", ignoreCase = true) ||
                        rSlot.tipo.contains("lab", ignoreCase = true)
                ParsedRemoteSlot(
                    dayOfWeek = parseDayOfWeek(rSlot.dia),
                    startTime = parseLocalTime(rSlot.horaInicio),
                    endTime = parseLocalTime(rSlot.horaFin),
                    classroom = rSlot.aula.trim(),
                    professor = rSlot.profesor.trim(),
                    isLab = isLab,
                    labGroupName = rSlot.grupoPracticas.trim()
                )
            }.filter { rSlot ->
                if (rSlot.isLab && !subject.selectedLabGroup.isNullOrBlank() && rSlot.labGroupName.isNotBlank()) {
                    rSlot.labGroupName.equals(subject.selectedLabGroup, ignoreCase = true)
                } else {
                    true
                }
            }

            val remainingLocal = existingSlots.toMutableList()
            val remainingRemote = parsedRemoteSlots.toMutableList()

            // 1. Exact matches (no diff)
            val exactLocalMatches = mutableListOf<ClassSlot>()
            val exactRemoteMatches = mutableListOf<ParsedRemoteSlot>()

            for (local in remainingLocal) {
                val remoteMatch = remainingRemote.find { remote ->
                    remote !in exactRemoteMatches &&
                            local.dayOfWeek == remote.dayOfWeek &&
                            local.startTime == remote.startTime &&
                            local.endTime == remote.endTime &&
                            local.classroom.orEmpty().trim() == remote.classroom &&
                            local.professor.orEmpty().trim() == remote.professor &&
                            (local.entryType == EntryType.LAB) == remote.isLab
                }
                if (remoteMatch != null) {
                    exactLocalMatches.add(local)
                    exactRemoteMatches.add(remoteMatch)
                }
            }

            remainingLocal.removeAll(exactLocalMatches)
            remainingRemote.removeAll(exactRemoteMatches)

            // 2. Modified matches (same day & lab type, or same time & lab type)
            val modifiedLocalMatches = mutableListOf<ClassSlot>()
            val modifiedRemoteMatches = mutableListOf<ParsedRemoteSlot>()

            for (local in remainingLocal) {
                // Priority A: Same isLab, same dayOfWeek
                var remoteMatch = remainingRemote.find { remote ->
                    remote !in modifiedRemoteMatches &&
                            (local.entryType == EntryType.LAB) == remote.isLab &&
                            local.dayOfWeek == remote.dayOfWeek
                }
                // Priority B: Same isLab, same time
                if (remoteMatch == null) {
                    remoteMatch = remainingRemote.find { remote ->
                        remote !in modifiedRemoteMatches &&
                                (local.entryType == EntryType.LAB) == remote.isLab &&
                                local.startTime == remote.startTime &&
                                local.endTime == remote.endTime
                    }
                }
                // Priority C: Same isLab
                if (remoteMatch == null) {
                    remoteMatch = remainingRemote.find { remote ->
                        remote !in modifiedRemoteMatches &&
                                (local.entryType == EntryType.LAB) == remote.isLab
                    }
                }

                if (remoteMatch != null) {
                    modifiedLocalMatches.add(local)
                    modifiedRemoteMatches.add(remoteMatch)

                    val oldDetail = formatSlotDetail(
                        local.dayOfWeek, local.startTime, local.endTime,
                        local.classroom, local.professor
                    )
                    val newDetail = formatSlotDetail(
                        remoteMatch.dayOfWeek, remoteMatch.startTime, remoteMatch.endTime,
                        remoteMatch.classroom, remoteMatch.professor
                    )

                    diffs.add(
                        SlotDiff(
                            subjectCode = subject.code.ifBlank { subject.name },
                            subjectName = subject.name,
                            changeType = DiffType.MODIFIED,
                            oldDetail = oldDetail,
                            newDetail = newDetail
                        )
                    )
                }
            }

            remainingLocal.removeAll(modifiedLocalMatches)
            remainingRemote.removeAll(modifiedRemoteMatches)

            // 3. Removed slots
            for (local in remainingLocal) {
                val oldDetail = formatSlotDetail(
                    local.dayOfWeek, local.startTime, local.endTime,
                    local.classroom, local.professor
                )
                diffs.add(
                    SlotDiff(
                        subjectCode = subject.code.ifBlank { subject.name },
                        subjectName = subject.name,
                        changeType = DiffType.REMOVED,
                        oldDetail = oldDetail,
                        newDetail = null
                    )
                )
            }

            // 4. Added slots
            for (remote in remainingRemote) {
                val newDetail = formatSlotDetail(
                    remote.dayOfWeek, remote.startTime, remote.endTime,
                    remote.classroom, remote.professor
                )
                diffs.add(
                    SlotDiff(
                        subjectCode = subject.code.ifBlank { subject.name },
                        subjectName = subject.name,
                        changeType = DiffType.ADDED,
                        oldDetail = null,
                        newDetail = newDetail
                    )
                )
            }
        }

        return diffs
    }

    private fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfkd = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD)
        return nfkd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").uppercase()
    }

    private fun normalizeGroup(group: String?): String {
        if (group.isNullOrBlank()) return ""
        return group.uppercase()
            .replace("GRUPO", "")
            .replace("GRADO", "")
            .replace("BILINGÜE", "")
            .replace("BILINGUE", "")
            .replace("º", "")
            .replace("ª", "")
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")
            .trim()
    }

    private fun parseDayOfWeek(dayStr: String): DayOfWeek {
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

    private fun parseLocalTime(timeStr: String): LocalTime {
        val trimmed = timeStr.trim()
        if (trimmed.isBlank()) return LocalTime.MIDNIGHT
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return LocalTime.of(h, m)
        }
        return runCatching { LocalTime.parse(trimmed) }.getOrDefault(LocalTime.MIDNIGHT)
    }

    private fun formatSlotDetail(
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
        classroom: String?,
        professor: String?
    ): String {
        val dayStr = dayOfWeekToSpanish(dayOfWeek)
        val timeStr = "%02d:%02d - %02d:%02d".format(
            startTime.hour, startTime.minute,
            endTime.hour, endTime.minute
        )
        val roomStr = if (!classroom.isNullOrBlank()) "Aula: ${classroom.trim()}" else null
        val profStr = if (!professor.isNullOrBlank()) "Prof: ${professor.trim()}" else null
        val extra = listOfNotNull(roomStr, profStr).joinToString(", ")
        return if (extra.isNotBlank()) "$dayStr $timeStr ($extra)" else "$dayStr $timeStr"
    }
}
