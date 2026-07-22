package com.example.clementime.utils

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Subject
import com.example.clementime.data.SubjectWithSlots
import java.time.Duration
import java.time.LocalTime

data class ScheduleSolution(
    val labSelections: Map<Long, String>, // subjectId -> labGroupName
    val overlapsCount: Int,
    val overlappingSlotIds: Set<Long>,
    val freeDaysCount: Int,
    val compactnessScore: Double, // Higher is better (lower gaps)
    val totalSlots: List<Pair<Subject, ClassSlot>>
)

object ConflictSolver {

    /**
     * Generates and ranks possible schedule solutions by selecting one lab variant per subject.
     */
    fun findSolutions(subjects: List<SubjectWithSlots>): List<ScheduleSolution> {
        // 1. Separate subjects into those with choice (multiple lab variants) and fixed ones.
        val subjectsWithChoices = subjects.filter { s ->
            s.slots.filter { it.entryType == EntryType.LAB }.mapNotNull { it.labGroupName }.distinct().size > 1
        }
        
        val fixedSlots = subjects.flatMap { s ->
            val labGroups = s.slots.filter { it.entryType == EntryType.LAB }.mapNotNull { it.labGroupName }.distinct()
            if (labGroups.size <= 1) {
                // All slots for this subject are "fixed" (Theory or the only Lab variant)
                s.slots.map { s.subject to it }
            } else {
                // Only Theory slots are fixed
                s.slots.filter { it.entryType == EntryType.THEORY }.map { s.subject to it }
            }
        }

        // 2. Generate Cartesian product of lab choices
        val choices = subjectsWithChoices.map { s ->
            val groups = s.slots.filter { it.entryType == EntryType.LAB }.mapNotNull { it.labGroupName }.distinct()
            groups.map { groupName ->
                s.subject.id to (groupName to s.slots.filter { it.labGroupName == groupName })
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
        labSelections: Map<Long, String>,
        slots: List<Pair<Subject, ClassSlot>>
    ): ScheduleSolution {
        var overlapsCount = 0
        val overlappingSlotIds = mutableSetOf<Long>()
        val slotsByDay = slots.groupBy { it.second.dayOfWeek }
        
        var totalGapMinutes = 0
        var activeDays = 0

        slotsByDay.forEach { (_, daySlots) ->
            activeDays++
            val sortedSlots = daySlots.sortedBy { it.second.startTime }
            
            // Check overlaps
            for (i in 0 until sortedSlots.size) {
                for (j in i + 1 until sortedSlots.size) {
                    val s1 = sortedSlots[i].second
                    val s2 = sortedSlots[j].second
                    
                    if (!s1.isIgnored && !s2.isIgnored && s1.startTime < s2.endTime && s2.startTime < s1.endTime) {
                        overlapsCount++
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
                if (lastEnd == null || slot.endTime > lastEnd!!) {
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
