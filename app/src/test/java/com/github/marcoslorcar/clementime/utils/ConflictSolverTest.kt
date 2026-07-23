package com.github.marcoslorcar.clementime.utils

import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.EntryType
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.SubjectWithSlots
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ConflictSolverTest {

    @Test
    fun `test findSolutions finds zero overlap solution`() {
        val subject1 = Subject(id = 1, code = "S1", name = "Subject 1", color = 0, isActive = true)
        val subject2 = Subject(id = 2, code = "S2", name = "Subject 2", color = 0, isActive = true)

        val s1Theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        
        // Lab variants for S2: L1 overlaps with S1 Theory, L2 does not.
        val s2Lab1 = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 30), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "L1")
        val s2Lab2 = ClassSlot(id = 3, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        val data = listOf(
            SubjectWithSlots(subject1, listOf(s1Theory)),
            SubjectWithSlots(subject2, listOf(s2Lab1, s2Lab2))
        )

        val solutions = ConflictSolver.findSolutions(data)

        assertEquals(2, solutions.size)
        // Best solution should be L2 (0 overlaps)
        assertEquals(listOf("L2"), solutions[0].labSelections[2L])
        assertEquals(0, solutions[0].overlapsCount)
        // Worst solution should be L1 (1 overlap)
        assertEquals(listOf("L1"), solutions[1].labSelections[2L])
        assertEquals(1, solutions[1].overlapsCount)
    }

    @Test
    fun `test deduplicates idempotent lab options`() {
        val subject1 = Subject(id = 1, code = "S1", name = "Subject 1", color = 0, isActive = true)
        val subject2 = Subject(id = 2, code = "S2", name = "Subject 2", color = 0, isActive = true)

        val s1Theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)

        // Lab variants for S2: L1 and L2 have the same schedule. L3 is different.
        val s2Lab1 = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 30), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "L1")
        val s2Lab2 = ClassSlot(id = 3, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 30), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "L2")
        val s2Lab3 = ClassSlot(id = 4, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L3")

        val data = listOf(
            SubjectWithSlots(subject1, listOf(s1Theory)),
            SubjectWithSlots(subject2, listOf(s2Lab1, s2Lab2, s2Lab3))
        )

        val solutions = ConflictSolver.findSolutions(data)

        // Only 2 solutions: {L1, L2} and {L3}, instead of 3.
        assertEquals(2, solutions.size)
        
        // Solution 1 should contain both L1 and L2
        val combinedNames = solutions.find { it.labSelections[2L]?.contains("L1") == true }?.labSelections?.get(2L)
        assertEquals(listOf("L1", "L2"), combinedNames)
    }

    @Test
    fun `test ignored slots are not counted as overlaps`() {
        val subject1 = Subject(id = 1, code = "S1", name = "Subject 1", color = 0, isActive = true)
        val subject2 = Subject(id = 2, code = "S2", name = "Subject 2", color = 0, isActive = true)

        // S1 Theory overlaps with S2 Theory, but S2 Theory is ignored.
        val s1Theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val s2Theory = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY, isIgnored = true)

        val data = listOf(
            SubjectWithSlots(subject1, listOf(s1Theory)),
            SubjectWithSlots(subject2, listOf(s2Theory))
        )

        val solutions = ConflictSolver.findSolutions(data)
        assertEquals(0, solutions[0].overlapsCount)
    }

    @Test
    fun `test dummy subjects do not count as overlaps and are not selected`() {
        val subject1 = Subject(id = 1, code = "S1", name = "Subject 1", color = 0, isActive = true)
        val subject2 = Subject(id = 2, code = "S2", name = "Subject 2", color = 0, isActive = true, isDummy = true)

        val s1Theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        
        // Lab variants for S2 (which is dummy): L1 overlaps with S1 Theory, L2 does not.
        val s2Lab1 = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 30), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "L1")
        val s2Lab2 = ClassSlot(id = 3, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "L2")

        val data = listOf(
            SubjectWithSlots(subject1, listOf(s1Theory)),
            SubjectWithSlots(subject2, listOf(s2Lab1, s2Lab2))
        )

        val solutions = ConflictSolver.findSolutions(data)

        // Since S2 is dummy, we shouldn't make lab selections/choices for it.
        // There should be only 1 solution (no options generated for S2).
        assertEquals(1, solutions.size)
        // S2 should not be in labSelections
        assertEquals(false, solutions[0].labSelections.containsKey(2L))
        // Overlaps should be 0 because S2 is dummy and shouldn't count towards overlaps
        assertEquals(0, solutions[0].overlapsCount)
    }
}
