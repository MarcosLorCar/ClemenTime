package com.github.marcoslorcar.clementime.ui.screens.scheduleimport.model

import com.github.marcoslorcar.clementime.data.ClassSlot
import com.github.marcoslorcar.clementime.data.Subject
import com.github.marcoslorcar.clementime.data.importing.model.SelectedSubject

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
    val subject1Code: String,
    val subject1Name: String,
    val subject2Code: String,
    val subject2Name: String,
    val slots: List<Pair<ClassSlot, ClassSlot>>
)
