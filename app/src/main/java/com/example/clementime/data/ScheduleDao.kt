package com.example.clementime.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    // Main schedule view: Only loads active slots (Theory + Selected Lab)
    @Transaction
    @Query("""
        SELECT * FROM matters 
        WHERE id IN (
            SELECT DISTINCT matterId FROM class_slots WHERE isSelected = 1
        ) 
        ORDER BY name ASC
    """)
    fun getActiveMattersWithSlots(): Flow<List<MatterWithSlots>>

    @Transaction
    suspend fun selectLabGroup(matterId: Long, labGroupName: String?) {
        deselectAllLabsForMatter(matterId)

        if (labGroupName != null) {
            activateLabGroupForMatter(matterId, labGroupName)
        }
    }

    @Query("UPDATE class_slots SET isSelected = 0 WHERE matterId = :matterId AND entryType = 'LAB'")
    suspend fun deselectAllLabsForMatter(matterId: Long)

    @Query("UPDATE class_slots SET isSelected = 1 WHERE matterId = :matterId AND labGroupName = :labGroupName")
    suspend fun activateLabGroupForMatter(matterId: Long, labGroupName: String)

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

    @Delete
    suspend fun deleteMatter(matter: Matter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ClassSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<ClassSlot>)

    @Update
    suspend fun updateSlot(slot: ClassSlot)

    @Delete
    suspend fun deleteSlot(slot: ClassSlot)

    @Query("DELETE FROM class_slots WHERE matterId = :matterId")
    suspend fun deleteSlotsForMatter(matterId: Long)

    @Transaction
    suspend fun insertMatterWithSlots(matter: Matter, slots: List<ClassSlot>) {
        val matterId = insertMatter(matter)
        val slotsWithParentId = slots.map { it.copy(matterId = matterId) }
        insertSlots(slotsWithParentId)
    }
}