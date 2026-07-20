package com.example.clementime.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScheduleItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
