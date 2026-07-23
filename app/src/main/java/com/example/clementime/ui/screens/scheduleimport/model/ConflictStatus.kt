package com.example.clementime.ui.screens.scheduleimport.model

import com.example.clementime.data.ClassSlot
import com.example.clementime.data.Subject
import com.example.clementime.data.importing.model.SelectedSubject

sealed interface ConflictStatus {
    object None : ConflictStatus
    
    /**
     * No theory-theory collisions, and at least one lab combination with 0 overlaps exists.
     */
    object Valid : ConflictStatus
    
    /**
     * Unavoidable overlaps exist.
     */
    data class Conflict(val detail: ConflictDetail) : ConflictStatus
}

data class ConflictDetail(
    val selectedSubjects: List<SelectedSubject>,
    val theoryOverlaps: List<TheoryOverlap>,
    val hasLabCombinationWithZeroOverlaps: Boolean,
    val theoryOverlappingSlots: List<Pair<Subject, ClassSlot>> = emptyList()
)

data class TheoryOverlap(
    val subject1: SelectedSubject,
    val subject2: SelectedSubject,
    val slots: List<Pair<ClassSlot, ClassSlot>>
)
