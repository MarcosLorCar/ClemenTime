package com.github.marcoslorcar.clementime.utils

import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import java.time.Duration
import java.time.LocalTime

data class ScheduleSolution(
    val labSelections: Map<Long, List<String>>, // subjectId -> List of equivalent lab group names
    val overlapsCount: Int,
    val theoryOverlapsCount: Int,
    val overlappingSlotIds: Set<Long>,
    val freeDaysCount: Int,
    val compactnessScore: Double, // Higher is better (lower gaps)
    val totalSlots: List<Pair<Subject, ClassSlot>>,
    val isCurrent: Boolean = false
)

object ConflictSolver {

    /**
     * Generates and ranks possible schedule solutions by selecting one lab variant per subject.
     */
    fun findSolutions(subjects: List<SubjectWithSlots>): List<ScheduleSolution> {
        // 1. Separate subjects into those with choice (multiple lab variants) and fixed ones.
        // A choice now represents a unique *schedule* of labs for that subject.
        val subjectsWithChoices = subjects.filter { s ->
            !s.subject.isDummy && run {
                val uniqueLabSchedules = s.slots
                    .filter { it.entryType == EntryType.LAB }
                    .groupBy { it.labGroupName }
                    .map { (_, slots) -> slots.map { it.dayOfWeek to (it.startTime to it.endTime) }.sortedBy { it.first } }
                    .distinct()
                uniqueLabSchedules.size > 1
            }
        }
        
        val fixedSlots = subjects.flatMap { s ->
            val uniqueLabSchedules = s.slots
                .filter { it.entryType == EntryType.LAB }
                .groupBy { it.labGroupName }
                .map { (_, slots) -> slots.map { it.dayOfWeek to (it.startTime to it.endTime) }.sortedBy { it.first } }
                .distinct()

            if (uniqueLabSchedules.size <= 1) {
                // All slots for this subject are "fixed" (Theory or the only unique Lab schedule)
                s.slots.map { s.subject to it }
            } else {
                // Only Theory slots are fixed
                s.slots.filter { it.entryType == EntryType.THEORY }.map { s.subject to it }
            }
        }

        // 2. Generate Cartesian product of UNIQUE lab schedules
        val choices = subjectsWithChoices.map { s ->
            val labGroups = s.slots.filter { it.entryType == EntryType.LAB }.groupBy { it.labGroupName }
            
            // Group lab group names by their schedule signature
            val scheduleToGroupNames = labGroups.entries.groupBy(
                keySelector = { entry -> 
                    entry.value.map { it.dayOfWeek to (it.startTime to it.endTime) }.sortedBy { it.first } 
                },
                valueTransform = { it.key!! }
            )

            scheduleToGroupNames.map { (_, groupNames) ->
                // representative slots for this schedule
                val repGroupName = groupNames.first()
                val slots = labGroups[repGroupName]!!
                s.subject.id to (groupNames to slots)
            }
        }

        val allCombinations = cartesianProduct(choices)

        // 3. Evaluate each combination
        val solutions = allCombinations.map { combination ->
            val labSelections = combination.associate { it.first to it.second.first }
            val chosenLabSlots = combination.flatMap { comb ->
                val subject = subjects.find { it.subject.id == comb.first }!!.subject
                comb.second.second.map { slot -> subject to slot }
            }
            
            val totalSlots = fixedSlots + chosenLabSlots
            evaluateSolution(labSelections, totalSlots)
        }

        // 4. Rank solutions
        return solutions.sortedWith(
            compareBy<ScheduleSolution> { it.overlapsCount }
                .thenByDescending { it.freeDaysCount }
                .thenByDescending { it.compactnessScore }
        )
    }

    private fun evaluateSolution(
        labSelections: Map<Long, List<String>>,
        slots: List<Pair<Subject, ClassSlot>>
    ): ScheduleSolution {
        var overlapsCount = 0
        var theoryOverlapsCount = 0
        val overlappingSlotIds = mutableSetOf<Long>()
        val evaluationSlots = slots.filter { !it.first.isDummy }
        val slotsByDay = evaluationSlots.groupBy { it.second.dayOfWeek }
        
        var totalGapMinutes = 0
        var activeDays = 0

        slotsByDay.forEach { (_, daySlots) ->
            activeDays++
            val sortedSlots = daySlots.sortedBy { it.second.startTime }
            
            // Check overlaps
            for (i in sortedSlots.indices) {
                for (j in i + 1 until sortedSlots.size) {
                    val pair1 = sortedSlots[i]
                    val pair2 = sortedSlots[j]
                    val s1 = pair1.second
                    val s2 = pair2.second
                    
                    if (!s1.isIgnored && !s2.isIgnored && s1.startTime < s2.endTime && s2.startTime < s1.endTime) {
                        val isTheoryTheory = s1.entryType == EntryType.THEORY && s2.entryType == EntryType.THEORY
                        if (isTheoryTheory) {
                            theoryOverlapsCount++
                        } else {
                            overlapsCount++
                        }
                        overlappingSlotIds.add(s1.id)
                        overlappingSlotIds.add(s2.id)
                    }
                }
            }

            // Calculate gaps (compactness)
            // We only care about gaps between non-overlapping classes for "good" compactness
            // A gap is space between end of one class and start of next.
            var lastEnd: LocalTime? = null
            sortedSlots.forEach { (_, slot) ->
                if (lastEnd != null && slot.startTime > lastEnd) {
                    totalGapMinutes += Duration.between(lastEnd, slot.startTime).toMinutes().toInt()
                }
                if (lastEnd == null || slot.endTime > lastEnd) {
                    lastEnd = slot.endTime
                }
            }
        }

        // Score compactness: 1 / (1 + totalGapMinutes) or similar.
        // Or just use negative gap minutes for sorting.
        val compactnessScore = if (totalGapMinutes == 0) 100.0 else 100.0 / (1.0 + totalGapMinutes / 60.0)

        return ScheduleSolution(
            labSelections = labSelections,
            overlapsCount = overlapsCount,
            theoryOverlapsCount = theoryOverlapsCount,
            overlappingSlotIds = overlappingSlotIds,
            freeDaysCount = 5 - activeDays, // Assuming 5-day week
            compactnessScore = compactnessScore,
            totalSlots = slots
        )
    }

    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        var result = lists[0].map { listOf(it) }
        for (i in 1 until lists.size) {
            val nextList = lists[i]
            val newResult = mutableListOf<List<T>>()
            for (acc in result) {
                for (item in nextList) {
                    newResult.add(acc + item)
                    // Cap at 1000 combinations to prevent memory/performance issues
                    if (newResult.size >= 1000) break
                }
                if (newResult.size >= 1000) break
            }
            result = newResult
            if (result.size >= 1000) break
        }
        return result
    }
}
