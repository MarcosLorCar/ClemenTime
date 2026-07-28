package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ConflictSolverTest {

    @Test
    fun `findSolutions handles null lab group names`() {
        val subject = Subject(id = 1, code = "S1", name = "Sub", color = 0, isActive = true)
        val labSlot = ClassSlot(
            id = 1,
            subjectId = 1,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            entryType = EntryType.LAB,
            labGroupName = null // This caused NPE before
        )
        val labSlot2 = ClassSlot(
            id = 2,
            subjectId = 1,
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            entryType = EntryType.LAB,
            labGroupName = "Group A"
        )
        
        val subjects = listOf(SubjectWithSlots(subject, listOf(labSlot, labSlot2)))
        
        val solutions = ConflictSolver.findSolutions(subjects)
        assertNotNull(solutions)
    }
}
