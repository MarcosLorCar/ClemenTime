package com.example.clementime.utils

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Subject
import com.example.clementime.data.SubjectWithSlots
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
        assertEquals("L2", solutions[0].labSelections[2L])
        assertEquals(0, solutions[0].overlapsCount)
        // Worst solution should be L1 (1 overlap)
        assertEquals("L1", solutions[1].labSelections[2L])
        assertEquals(1, solutions[1].overlapsCount)
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

        // Only one "solution" since no choices, but let's check overlap count.
        assertEquals(0, solutions[0].overlapsCount)
    }
}
