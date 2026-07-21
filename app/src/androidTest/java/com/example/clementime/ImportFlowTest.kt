package com.example.clementime

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.clementime.data.AppDatabase
import com.example.clementime.data.EntryType
import com.example.clementime.data.importing.repository.ImportRepository
import com.example.clementime.data.importing.parser.UntisPdfParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ImportFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ImportRepository
    private lateinit var context: Context
    private lateinit var tempPdfFile: File
    private lateinit var pdfUri: Uri

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ImportRepository(db.scheduleDao())

        // Copy asset PDF to a temp file in the cache directory to get a Uri
        tempPdfFile = File(context.cacheDir, "test_schedule.pdf")
        context.assets.open("ESI2026-27-GRADO_1C_Grupos.pdf").use { input ->
            tempPdfFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        pdfUri = Uri.fromFile(tempPdfFile)
    }

    @After
    fun tearDown() {
        db.close()
        if (tempPdfFile.exists()) {
            tempPdfFile.delete()
        }
    }

    @Test
    fun testPdfParserSuccess() {
        // Log the first page's text using LayoutPreservingTextStripper
        val stripper = com.example.clementime.data.importing.parser.LayoutPreservingTextStripper()
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(context.assets.open("ESI2026-27-GRADO_1C_Grupos.pdf"))
        stripper.startPage = 1
        stripper.endPage = 1
        stripper.getText(doc)
        doc.close()
        val customText = stripper.getLayoutText()
        android.util.Log.d("PDF_DEBUG", "Custom page text length: ${customText.length}\n$customText")

        // Act: Parse PDF
        val groups = UntisPdfParser.parse(context, pdfUri)

        // Assert: Groups parsed
        assertNotNull(groups)
        assertTrue("Parsed groups should not be empty", groups.isNotEmpty())

        // Verify we found common group names
        val groupNames = groups.map { it.groupName }
        android.util.Log.d("PDF_DEBUG", "Parsed group names: $groupNames")
        
        assertTrue("Expected 1º A Bilingüe in $groupNames", groupNames.contains("1º A Bilingüe"))
        assertTrue("Expected 1º B in $groupNames", groupNames.contains("1º B"))
        assertTrue("Expected 2º A Bilingüe in $groupNames", groupNames.contains("2º A Bilingüe"))

        // Find "1º A Bilingüe" and check subjects
        val group1A = groups.first { it.groupName == "1º A Bilingüe" }
        assertNotNull(group1A)
        assertEquals("1º Cuatrimestre", group1A.term)

        val subjectCodes = group1A.subjects.map { it.code }
        android.util.Log.d("PDF_DEBUG", "Parsed subject codes in 1A: $subjectCodes")

        // Verify "FunProg1" exists in 1º A Bilingüe
        val funProg = group1A.subjects.find { it.code == "FunProg1" }
        assertNotNull("FunProg1 subject should be found in $subjectCodes", funProg)
        assertEquals("Fundamentos de Programación I", funProg!!.fullName)

        // Verify theory slots (e.g., Lunes 10:00 - 11:30)
        assertTrue(funProg.theorySlots.isNotEmpty())
        val theorySlot = funProg.theorySlots.first()
        assertEquals(DayOfWeek.MONDAY, theorySlot.dayOfWeek)
        assertEquals(LocalTime.of(10, 0), theorySlot.startTime)
        assertEquals(LocalTime.of(11, 30), theorySlot.endTime)

        // Verify lab variants exist
        assertTrue(funProg.labVariants.isNotEmpty())
        assertTrue(funProg.labVariants.containsKey("Lab-A1"))
        assertTrue(funProg.labVariants.containsKey("Lab-A2"))
    }

    @Test
    fun testImportRepositoryInsertsIntoDb() = runBlocking {
        // Act: Parse PDF first
        val groups = UntisPdfParser.parse(context, pdfUri)
        val group1A = groups.first { it.groupName == "1º A Bilingüe" }
        val funProg = group1A.subjects.first { it.code == "FunProg1" }

        // Setup subject import selection: FunProg1 with "Lab-A1"
        val selection = mapOf(funProg to "Lab-A1")

        // Act: Import selected subjects
        repository.importSelectedSubjects(selection)

        // Assert: Verify database contents
        val matters = db.scheduleDao().getAllMatters().first()
        assertEquals(1, matters.size)
        val importedMatter = matters[0]
        assertEquals("Fundamentos de Programación I", importedMatter.name)
        assertEquals("FunProg1", importedMatter.code)
        assertEquals("1º A Bilingüe", importedMatter.courseGroup)

        val entries = db.scheduleDao().getAllEntriesWithMatter().first()
        // There should be theory entries and Lab-A1 entries
        assertTrue(entries.isNotEmpty())

        // Verify all entries link to our matter
        entries.forEach { entryWithMatter ->
            assertEquals(importedMatter.id, entryWithMatter.matter.id)
            if (entryWithMatter.entry.entryType == EntryType.LAB) {
                assertEquals("Lab-A1", entryWithMatter.entry.groupName)
            } else {
                assertEquals("Teoría", entryWithMatter.entry.groupName)
            }
        }
    }
}
