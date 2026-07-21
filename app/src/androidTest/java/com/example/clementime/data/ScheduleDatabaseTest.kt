package com.example.clementime.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.scheduleDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadMatterAndEntry() = runBlocking {
        val matter = Matter(name = "Math", color = 0xFF0000)
        val matterId = dao.insertMatter(matter)

        val entry = ScheduleEntry(
            matterId = matterId,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            groupName = "Group A"
        )
        dao.insertEntry(entry)

        val allEntries = dao.getAllEntriesWithMatter().first()
        assertEquals(1, allEntries.size)
        assertEquals("Math", allEntries[0].matter.name)
        assertEquals("Group A", allEntries[0].entry.groupName)
    }

    @Test
    fun deleteMatterCascadesToEntries() = runBlocking {
        val matter = Matter(name = "Physics", color = 0x00FF00)
        val matterId = dao.insertMatter(matter)

        val entry = ScheduleEntry(
            matterId = matterId,
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(16, 0)
        )
        dao.insertEntry(entry)

        dao.deleteMatter(dao.getAllMatters().first()[0])

        val allEntries = dao.getAllEntriesWithMatter().first()
        assertEquals(0, allEntries.size)
    }
}
