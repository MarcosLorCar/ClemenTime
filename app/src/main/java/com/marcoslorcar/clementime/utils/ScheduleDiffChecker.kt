package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.TermCodeParser
import com.marcoslorcar.clementime.data.importing.model.JsonFlatSlot
import com.marcoslorcar.clementime.data.importing.parser.isLab
import com.marcoslorcar.clementime.data.importing.parser.normalizeGroup
import com.marcoslorcar.clementime.data.importing.parser.parseDayOfWeek
import com.marcoslorcar.clementime.data.importing.parser.parseRemoteTime
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalTime

enum class DiffType { MODIFIED, ADDED, REMOVED }

data class DiffSlotSnapshot(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val entryType: EntryType = EntryType.THEORY,
    val classroom: String? = null,
    val professor: String? = null,
    val labGroupName: String? = null
)

data class SlotDiff(
    val subjectCode: String,
    val subjectName: String,
    val changeType: DiffType,
    val subjectKey: String = "",
    val oldSlot: DiffSlotSnapshot? = null,
    val newSlot: DiffSlotSnapshot? = null,
    val subjectColor: Int? = null
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

    fun subjectKey(subjectCode: String, subjectName: String, courseGroup: String?): String {
        val idPart = normalizeText(subjectCode.ifBlank { subjectName })
        val groupPart = normalizeGroup(courseGroup)
        return "${idPart}__${groupPart}"
    }

    fun computeRemoteSubjectHashes(
        existingSubjects: List<SubjectWithSlots>,
        remoteSlots: List<JsonFlatSlot>,
        currentSemester: Int
    ): Map<String, String> {
        val semesterRemoteSlots = filterSemesterSlots(remoteSlots, currentSemester)
        val activeSubjects = existingSubjects.filter { it.subject.isActive }
        val hashes = mutableMapOf<String, String>()

        for (subjectWithSlots in activeSubjects) {
            val subject = subjectWithSlots.subject
            val key = subjectKey(subject.code, subject.name, subject.courseGroup)
            val parsedRemoteSlots = matchAndParseRemoteSlots(subject, semesterRemoteSlots)
            hashes[key] = hashParsedSlots(parsedRemoteSlots)
        }
        return hashes
    }

    fun findDiffs(
        existingSubjects: List<SubjectWithSlots>,
        remoteSlots: List<JsonFlatSlot>,
        currentSemester: Int,
        previousRemoteSubjectHashes: Map<String, String> = emptyMap()
    ): List<SlotDiff> {
        val diffs = mutableListOf<SlotDiff>()
        val semesterRemoteSlots = filterSemesterSlots(remoteSlots, currentSemester)
        val activeSubjects = existingSubjects.filter { it.subject.isActive }
        val currentRemoteHashes = computeRemoteSubjectHashes(activeSubjects, remoteSlots, currentSemester)

        for (subjectWithSlots in activeSubjects) {
            val subject = subjectWithSlots.subject
            val existingSlots = subjectWithSlots.slots
            val key = subjectKey(subject.code, subject.name, subject.courseGroup)

            // If this subject's upstream remote slots have not changed since the last known
            // remote snapshot, any discrepancy in Room is a deliberate manual edit by the user.
            val previousRemoteHash = previousRemoteSubjectHashes[key]
            val currentRemoteHash = currentRemoteHashes[key]
            if (!previousRemoteHash.isNullOrBlank() && previousRemoteHash == currentRemoteHash) {
                continue
            }

            val parsedRemoteSlots = matchAndParseRemoteSlots(subject, semesterRemoteSlots)

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

                    diffs.add(
                        SlotDiff(
                            subjectCode = subject.code.ifBlank { subject.name },
                            subjectName = subject.name,
                            changeType = DiffType.MODIFIED,
                            subjectKey = key,
                            oldSlot = local.toDiffSnapshot(),
                            newSlot = remoteMatch.toDiffSnapshot(),
                            subjectColor = subject.color
                        )
                    )
                }
            }

            remainingLocal.removeAll(modifiedLocalMatches)
            remainingRemote.removeAll(modifiedRemoteMatches)

            // 3. Removed slots
            for (local in remainingLocal) {
                diffs.add(
                    SlotDiff(
                        subjectCode = subject.code.ifBlank { subject.name },
                        subjectName = subject.name,
                        changeType = DiffType.REMOVED,
                        subjectKey = key,
                        oldSlot = local.toDiffSnapshot(),
                        newSlot = null,
                        subjectColor = subject.color
                    )
                )
            }

            // 4. Added slots
            for (remote in remainingRemote) {
                diffs.add(
                    SlotDiff(
                        subjectCode = subject.code.ifBlank { subject.name },
                        subjectName = subject.name,
                        changeType = DiffType.ADDED,
                        subjectKey = key,
                        oldSlot = null,
                        newSlot = remote.toDiffSnapshot(),
                        subjectColor = subject.color
                    )
                )
            }
        }

        return diffs
    }

    private fun ClassSlot.toDiffSnapshot(): DiffSlotSnapshot = DiffSlotSnapshot(
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        entryType = entryType,
        classroom = classroom?.trim()?.ifEmpty { null },
        professor = professor?.trim()?.ifEmpty { null },
        labGroupName = labGroupName?.trim()?.ifEmpty { null }
    )

    private fun ParsedRemoteSlot.toDiffSnapshot(): DiffSlotSnapshot = DiffSlotSnapshot(
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        entryType = if (isLab) EntryType.LAB else EntryType.THEORY,
        classroom = classroom.ifBlank { null },
        professor = professor.ifBlank { null },
        labGroupName = labGroupName.ifBlank { null }
    )

    private fun filterSemesterSlots(
        remoteSlots: List<JsonFlatSlot>,
        currentSemester: Int
    ): List<JsonFlatSlot> {
        return remoteSlots.filter { slot ->
            TermCodeParser.matchesSemester(slot.cuatrimestre, currentSemester)
        }
    }

    private fun matchAndParseRemoteSlots(
        subject: Subject,
        semesterRemoteSlots: List<JsonFlatSlot>
    ): List<ParsedRemoteSlot> {
        val codeNorm = normalizeText(subject.code)
        val nameNorm = normalizeText(subject.name)
        val subjectGroupNorm = normalizeGroup(subject.courseGroup)

        val matchedJsonSlots = semesterRemoteSlots.filter { rSlot ->
            val asigNorm = normalizeText(rSlot.asignatura)
            val rCodeNorm = normalizeText(rSlot.codigo)

            val codeMatch = codeNorm.isNotBlank() && (rCodeNorm == codeNorm || asigNorm == codeNorm)
            val nameMatch = nameNorm.isNotBlank() && asigNorm == nameNorm

            val rSlotGroupNorm = normalizeGroup(rSlot.grupo)
            val groupMatch = subject.courseGroup.isNullOrBlank() ||
                    rSlot.grupo.isBlank() ||
                    subjectGroupNorm == rSlotGroupNorm ||
                    (subjectGroupNorm.isNotBlank() && rSlotGroupNorm.contains(subjectGroupNorm)) ||
                    (rSlotGroupNorm.isNotBlank() && subjectGroupNorm.contains(rSlotGroupNorm))

            (codeMatch || nameMatch) && groupMatch
        }

        return matchedJsonSlots.map { rSlot ->
            ParsedRemoteSlot(
                dayOfWeek = parseDayOfWeek(rSlot.dia),
                startTime = parseRemoteTime(rSlot.horaInicio),
                endTime = parseRemoteTime(rSlot.horaFin),
                classroom = rSlot.aula.trim(),
                professor = rSlot.profesor.trim(),
                isLab = rSlot.isLab(),
                labGroupName = rSlot.grupoPracticas.trim()
            )
        }.filter { rSlot ->
            if (rSlot.isLab && !subject.selectedLabGroup.isNullOrBlank() && rSlot.labGroupName.isNotBlank()) {
                rSlot.labGroupName.equals(subject.selectedLabGroup, ignoreCase = true)
            } else {
                true
            }
        }
    }

    private fun hashParsedSlots(slots: List<ParsedRemoteSlot>): String {
        val canonical = slots
            .sortedWith(
                compareBy(
                    ParsedRemoteSlot::dayOfWeek,
                    ParsedRemoteSlot::startTime,
                    ParsedRemoteSlot::endTime,
                    ParsedRemoteSlot::isLab,
                    ParsedRemoteSlot::labGroupName,
                    ParsedRemoteSlot::classroom,
                    ParsedRemoteSlot::professor
                )
            )
            .joinToString("\n") { slot ->
                "${slot.dayOfWeek.value}|${slot.startTime}|${slot.endTime}|${slot.classroom}|${slot.professor}|${slot.isLab}|${slot.labGroupName}"
            }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfkd = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD)
        return nfkd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").uppercase()
    }
}
