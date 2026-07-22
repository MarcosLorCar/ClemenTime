package com.example.clementime.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Subject::class, ClassSlot::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename table 'matters' to 'subjects'
                db.execSQL("ALTER TABLE matters RENAME TO subjects")
                
                // Rename column 'matterId' to 'subjectId' in 'class_slots'
                db.execSQL("ALTER TABLE class_slots RENAME COLUMN matterId TO subjectId")
                
                // Recreate the index for the new column name
                db.execSQL("DROP INDEX IF EXISTS index_class_slots_matterId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_class_slots_subjectId ON class_slots(subjectId)")
            }
        }
    }
}
