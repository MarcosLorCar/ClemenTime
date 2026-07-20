package com.example.clementime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_items ORDER BY startTime ASC")
    fun getAllItems(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE id = :id")
    suspend fun getItemById(id: Long): ScheduleItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ScheduleItem)

    @Update
    suspend fun updateItem(item: ScheduleItem)

    @Delete
    suspend fun deleteItem(item: ScheduleItem)
}
