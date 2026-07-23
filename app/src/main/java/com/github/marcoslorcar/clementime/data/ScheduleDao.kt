@file:Suppress("EmptyMethod")

package com.github.marcoslorcar.clementime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    // Main schedule view: Only loads active slots (Theory + Selected Lab)
    @Transaction
    @Query("SELECT * FROM subjects WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveSubjectsWithSlots(): Flow<List<SubjectWithSlots>>

    @Query("UPDATE subjects SET isActive = :isActive WHERE id = :subjectId")
    suspend fun updateSubjectActiveStatus(subjectId: Long, isActive: Boolean)

    @Query("UPDATE subjects SET selectedLabGroup = :labGroup WHERE id = :subjectId")
    suspend fun updateSelectedLabGroup(subjectId: Long, labGroup: String?)

    @Transaction
    suspend fun updateSelectedLabGroups(selections: Map<Long, String?>) {
        selections.forEach { (id, group) ->
            updateSelectedLabGroup(id, group)
        }
    }

    @Transaction
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjectsWithSlots(): Flow<List<SubjectWithSlots>>

    @Transaction
    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    fun getSubjectWithSlotsById(subjectId: Long): Flow<SubjectWithSlots?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Update
    suspend fun updateSubject(subject: Subject)

    @Query("DELETE FROM subjects WHERE id = :subjectId")
    suspend fun deleteSubjectById(subjectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ClassSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ClassSlot>)

    @Update
    suspend fun updateSlot(slot: ClassSlot)

    @Query("UPDATE class_slots SET isIgnored = :isIgnored WHERE id = :slotId")
    suspend fun updateSlotIgnoredStatus(slotId: Long, isIgnored: Boolean)

    @Delete
    suspend fun deleteSlot(slot: ClassSlot)

    @Query("DELETE FROM class_slots WHERE id = :slotId")
    suspend fun deleteSlotById(slotId: Long)

    @Transaction
    suspend fun upsertSubjectWithSlots(subject: Subject, slots: List<ClassSlot>) {
        val actualSubjectId = if (subject.id == 0L) {
            insertSubject(subject)
        } else {
            updateSubject(subject)
            subject.id
        }

        deleteSlotsForSubject(actualSubjectId)

        val updatedSlots = slots.map { slot ->
            slot.copy(
                id = if (subject.id == 0L) 0L else slot.id,
                subjectId = actualSubjectId
            )
        }
        insertSlots(updatedSlots)
    }

    @Query("DELETE FROM class_slots WHERE subjectId = :subjectId")
    suspend fun deleteSlotsForSubject(subjectId: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query("DELETE FROM subjects WHERE id IN (:subjectIds)")
    suspend fun deleteSubjectsByIds(subjectIds: List<Long>)

    @Query("UPDATE subjects SET isActive = :isActive WHERE id IN (:subjectIds)")
    suspend fun updateSubjectsActiveStatus(subjectIds: List<Long>, isActive: Boolean)
}