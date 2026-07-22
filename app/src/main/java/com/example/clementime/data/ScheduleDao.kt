package com.example.clementime.data

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
    @Query("SELECT * FROM matters WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveMattersWithSlots(): Flow<List<MatterWithSlots>>

    @Query("UPDATE matters SET isActive = :isActive WHERE id = :matterId")
    suspend fun updateMatterActiveStatus(matterId: Long, isActive: Boolean)

    @Transaction
    @Query("SELECT * FROM matters ORDER BY name ASC")
    fun getAllMattersWithSlots(): Flow<List<MatterWithSlots>>

    @Transaction
    @Query("SELECT * FROM matters WHERE id = :matterId")
    fun getMatterWithSlotsById(matterId: Long): Flow<MatterWithSlots?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatter(matter: Matter): Long

    @Update
    suspend fun updateMatter(matter: Matter)

    @Query("DELETE FROM matters WHERE id = :matterId")
    suspend fun deleteMatterById(matterId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ClassSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ClassSlot>)

    @Update
    suspend fun updateSlot(slot: ClassSlot)

    @Delete
    suspend fun deleteSlot(slot: ClassSlot)

    @Query("DELETE FROM class_slots WHERE id = :slotId")
    suspend fun deleteSlotById(slotId: Long)

    @Transaction
    suspend fun upsertMatterWithSlots(matter: Matter, slots: List<ClassSlot>) {
        val actualMatterId = if (matter.id == 0L) {
            insertMatter(matter)
        } else {
            updateMatter(matter)
            matter.id
        }

        deleteSlotsForMatter(actualMatterId)

        val updatedSlots = slots.map { slot ->
            slot.copy(
                id = if (matter.id == 0L) 0L else slot.id,
                matterId = actualMatterId
            )
        }
        insertSlots(updatedSlots)
    }

    @Query("DELETE FROM class_slots WHERE matterId = :matterId")
    suspend fun deleteSlotsForMatter(matterId: Long)

    @Query("DELETE FROM matters")
    suspend fun deleteAllMatters()

    @Query("DELETE FROM matters WHERE id IN (:matterIds)")
    suspend fun deleteMattersByIds(matterIds: List<Long>)

    @Query("UPDATE matters SET isActive = :isActive WHERE id IN (:matterIds)")
    suspend fun updateMattersActiveStatus(matterIds: List<Long>, isActive: Boolean)
}