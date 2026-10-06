package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ConflictSolverLabFreeTest {

    @Test
    fun `test empty subject list returns empty solutions`() {
        val solutions = ConflictSolver.findSolutions(emptyList())
        assertTrue("Empty subject list should yield empty solutions", solutions.isEmpty())
    }

    @Test
    fun `test pure lecture faculty with zero labs returns exactly one optimal solution`() {
        // Faculty of Law / Humanities scenario: all subjects are theory lectures, zero labs
        val subjects = (1..4).map { id ->
            Subject(
                id = id.toLong(),
                code = "LAW$id",
                name = "Law Course $id",
                color = id * 100,
                isActive = true
            )
        }

        // Each subject has 2 distinct non-overlapping lectures
        val slots = listOf(
            ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.THEORY),
            ClassSlot(id = 2, subjectId = 1, dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.THEORY),

            ClassSlot(id = 3, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 30), entryType = EntryType.THEORY),
            ClassSlot(id = 4, subjectId = 2, dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 30), entryType = EntryType.THEORY),

            ClassSlot(id = 5, subjectId = 3, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.THEORY),
            ClassSlot(id = 6, subjectId = 3, dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.THEORY),

            ClassSlot(id = 7, subjectId = 4, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 30), entryType = EntryType.THEORY),
            ClassSlot(id = 8, subjectId = 4, dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 30), entryType = EntryType.THEORY)
        )

        val data = subjects.map { sub ->
            SubjectWithSlots(sub, slots.filter { it.subjectId == sub.id })
        }

        val solutions = ConflictSolver.findSolutions(data)

        assertEquals("Should produce exactly 1 solution for fixed lecture schedule", 1, solutions.size)
        val solution = solutions.first()
        assertEquals("Total slots should match all 8 lectures", 8, solution.totalSlots.size)
        assertEquals("Should have 0 overlaps", 0, solution.overlapsCount)
        assertTrue("Overlapping slot IDs must be empty", solution.overlappingSlotIds.isEmpty())
        assertTrue("Lab selections should be empty since no labs exist", solution.labSelections.isEmpty())
        assertEquals("Friday is completely free -> 1 free day out of 5", 1, solution.freeDaysCount)
    }

    @Test
    fun `test pure lecture faculty with overlapping lectures detects conflicts`() {
        val subject1 = Subject(id = 1, code = "HIST1", name = "History 1", color = 1, isActive = true)
        val subject2 = Subject(id = 2, code = "PHIL1", name = "Philosophy 1", color = 2, isActive = true)

        val slot1 = ClassSlot(id = 101, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.THEORY)
        // Overlaps with slot1 (11:00-13:00 vs 10:00-12:00)
        val slot2 = ClassSlot(id = 102, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(13, 0), entryType = EntryType.THEORY)

        val data = listOf(
            SubjectWithSlots(subject1, listOf(slot1)),
            SubjectWithSlots(subject2, listOf(slot2))
        )

        val solutions = ConflictSolver.findSolutions(data)

        assertEquals(1, solutions.size)
        val solution = solutions.first()
        assertEquals(0, solution.overlapsCount)
        assertEquals(1, solution.theoryOverlapsCount)
        assertEquals(setOf(101L, 102L), solution.overlappingSlotIds)
    }

    @Test
    fun `test single lab variant per subject is treated as fixed and requires no choices`() {
        val subject = Subject(id = 1, code = "CHEM1", name = "Chemistry 1", color = 1, isActive = true)
        val theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.THEORY)
        val singleLab = ClassSlot(id = 2, subjectId = 1, dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), entryType = EntryType.LAB, labGroupName = "Lab-Group-A")

        val data = listOf(SubjectWithSlots(subject, listOf(theory, singleLab)))
        val solutions = ConflictSolver.findSolutions(data)

        assertEquals("Single lab option should be fixed without choices", 1, solutions.size)
        assertEquals(2, solutions[0].totalSlots.size)
        assertEquals(0, solutions[0].overlapsCount)
        // No choice permutations need to be made
        assertTrue(solutions[0].labSelections.isEmpty())
    }

    @Test
    fun `test mixed curriculum with lecture-only and multiple-subgroup subjects`() {
        // Subject 1: Pure lecture (e.g. Mathematics)
        val subMath = Subject(id = 1, code = "MATH", name = "Mathematics", color = 1, isActive = true)
        val mathLecture = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(8, 30), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)

        // Subject 2: Lecture + 2 Seminar/Subgroup choices
        val subEcon = Subject(id = 2, code = "ECON", name = "Economics", color = 2, isActive = true)
        val econLecture = ClassSlot(id = 2, subjectId = 2, dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(8, 30), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val econSem1 = ClassSlot(id = 3, subjectId = 2, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "Sem-1") // Overlaps with mathLecture
        val econSem2 = ClassSlot(id = 4, subjectId = 2, dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), entryType = EntryType.LAB, labGroupName = "Sem-2") // Free

        val data = listOf(
            SubjectWithSlots(subMath, listOf(mathLecture)),
            SubjectWithSlots(subEcon, listOf(econLecture, econSem1, econSem2))
        )

        val solutions = ConflictSolver.findSolutions(data)

        assertEquals("Should produce 2 solutions corresponding to Sem-1 and Sem-2", 2, solutions.size)

        // First solution should be Sem-2 with 0 overlaps
        val bestSolution = solutions[0]
        assertEquals(listOf("Sem-2"), bestSolution.labSelections[2L])
        assertEquals(0, bestSolution.overlapsCount)
        assertTrue(bestSolution.totalSlots.any { it.second.id == 1L }) // Math lecture is present
        assertTrue(bestSolution.totalSlots.any { it.second.id == 2L }) // Econ lecture is present
        assertTrue(bestSolution.totalSlots.any { it.second.id == 4L }) // Sem-2 is present

        // Second solution should be Sem-1 with 1 overlap (Math Lecture vs Sem-1)
        val conflictingSolution = solutions[1]
        assertEquals(listOf("Sem-1"), conflictingSolution.labSelections[2L])
        assertEquals(1, conflictingSolution.overlapsCount)
        assertEquals(setOf(1L, 3L), conflictingSolution.overlappingSlotIds)
    }

    @Test
    fun `test labVariantCount returns zero for pure lectures and handles null or blank subgroup names`() {
        val subject = Subject(id = 1, code = "ENG", name = "English", color = 1, isActive = true)

        val theory1 = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val theory2 = ClassSlot(id = 2, subjectId = 1, dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)

        // Pure theory -> 0
        assertEquals(0, ConflictSolver.labVariantCount(SubjectWithSlots(subject, listOf(theory1, theory2))))

        // Theory + lab with null labGroupName -> 0 (defensive check)
        val nullLab = ClassSlot(id = 3, subjectId = 1, dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.LAB, labGroupName = null)
        assertEquals(0, ConflictSolver.labVariantCount(SubjectWithSlots(subject, listOf(theory1, nullLab))))

        // Theory + lab with blank labGroupName -> 0
        val blankLab = ClassSlot(id = 4, subjectId = 1, dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.LAB, labGroupName = "   ")
        assertEquals(0, ConflictSolver.labVariantCount(SubjectWithSlots(subject, listOf(theory1, blankLab))))

        // Valid named subgroup -> 1
        val validLab = ClassSlot(id = 5, subjectId = 1, dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.LAB, labGroupName = "Seminar-A")
        assertEquals(1, ConflictSolver.labVariantCount(SubjectWithSlots(subject, listOf(theory1, validLab))))
    }

    @Test
    fun `test ClassSlot isSubgroup property`() {
        val theory = ClassSlot(id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), entryType = EntryType.THEORY)
        val lab = ClassSlot(id = 2, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0), entryType = EntryType.LAB, labGroupName = "L1")

        assertFalse(theory.isSubgroup)
        assertTrue(lab.isSubgroup)
    }
}
