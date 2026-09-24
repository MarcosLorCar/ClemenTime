package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.AttachedFileItem
import com.marcoslorcar.clementime.data.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileUtilsTest {

    @Test
    fun getFriendlyFileType_identifiesCommonExtensions() {
        assertEquals("PDF", getFriendlyFileType("syllabus.pdf"))
        assertEquals("PDF", getFriendlyFileType("SYLLABUS.PDF"))
        assertEquals("Word", getFriendlyFileType("essay.docx"))
        assertEquals("Word", getFriendlyFileType("doc.odt"))
        assertEquals("Excel", getFriendlyFileType("data.xlsx"))
        assertEquals("Excel", getFriendlyFileType("grades.csv"))
        assertEquals("PowerPoint", getFriendlyFileType("slides.pptx"))
        assertEquals("Image", getFriendlyFileType("diagram.png"))
        assertEquals("Image", getFriendlyFileType("photo.jpg"))
        assertEquals("Image", getFriendlyFileType("vector.svg"))
        assertEquals("Archive", getFriendlyFileType("source.zip"))
        assertEquals("Archive", getFriendlyFileType("backup.tar.gz"))
        assertEquals("Text", getFriendlyFileType("notes.txt"))
        assertEquals("Text", getFriendlyFileType("README.md"))
        assertEquals("Code", getFriendlyFileType("Solution.kt"))
        assertEquals("Code", getFriendlyFileType("Main.java"))
        assertEquals("Code", getFriendlyFileType("script.py"))
        assertEquals("Code", getFriendlyFileType("index.html"))
        assertEquals("File", getFriendlyFileType("unknown_extension_file.xyz"))
    }

    @Test
    fun formatFileSize_formatsCorrectly() {
        assertNull(formatFileSize(null))
        assertNull(formatFileSize(0L))
        assertNull(formatFileSize(-100L))

        assertEquals("500 B", formatFileSize(500L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("2.5 KB", formatFileSize(2560L))
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("15.5 MB", formatFileSize((15.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun attachedFileItem_serializationBackwardsCompatibility() {
        val converters = Converters()

        // Legacy JSON without fileSizeBytes
        val legacyJson = """[{"id":"test-1","name":"legacy.pdf","fileType":"PDF","uriString":"content://media/1"}]"""
        val deserialized = converters.toAttachedFiles(legacyJson)

        assertNotNull(deserialized)
        assertEquals(1, deserialized!!.size)
        assertEquals("test-1", deserialized[0].id)
        assertEquals("legacy.pdf", deserialized[0].name)
        assertEquals("PDF", deserialized[0].fileType)
        assertEquals("content://media/1", deserialized[0].uriString)
        assertNull(deserialized[0].fileSizeBytes)

        // New item with fileSizeBytes
        val newItem = AttachedFileItem(
            id = "test-2",
            name = "copied.pdf",
            fileType = "PDF",
            uriString = "/data/user/0/com.marcoslorcar.clementime/files/attachments/copied.pdf",
            fileSizeBytes = 2048L
        )
        val json = converters.fromAttachedFiles(listOf(newItem))
        val reDeserialized = converters.toAttachedFiles(json)

        assertNotNull(reDeserialized)
        assertEquals(1, reDeserialized!!.size)
        assertEquals(2048L, reDeserialized[0].fileSizeBytes)
        assertEquals("copied.pdf", reDeserialized[0].name)
    }
}
