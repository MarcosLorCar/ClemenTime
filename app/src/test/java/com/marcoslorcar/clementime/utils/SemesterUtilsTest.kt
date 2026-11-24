package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.TermCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterUtilsTest {

    @Test
    fun parseTermSemester_handlesSemestersTrimestersQuartersAndScopedIds() {
        assertEquals(1, SemesterUtils.parseTermSemester("1C"))
        assertEquals(2, SemesterUtils.parseTermSemester("2c"))
        assertEquals(3, SemesterUtils.parseTermSemester("3C"))
        assertEquals(3, SemesterUtils.parseTermSemester("3T"))
        assertEquals(3, SemesterUtils.parseTermSemester("T3"))
        assertEquals(4, SemesterUtils.parseTermSemester("Q4"))
        assertEquals(4, SemesterUtils.parseTermSemester("4Q"))
        assertEquals(2, SemesterUtils.parseTermSemester("S2"))
        assertEquals(5, SemesterUtils.parseTermSemester("5"))
        assertEquals(3, SemesterUtils.parseTermSemester("Term 3"))
        assertEquals(2, SemesterUtils.parseTermSemester("Sem 2"))
        assertEquals(3, SemesterUtils.parseTermSemester("uclm_esi_3T"))
        assertEquals(4, SemesterUtils.parseTermSemester("https://example.com/schedules/4C.json?v=123"))
        assertEquals(1, SemesterUtils.parseTermSemester("ANUAL"))
        assertEquals(1, SemesterUtils.parseTermSemester("annual"))
    }

    @Test
    fun parseTermSemester_rejectsCourseGroupCodesAndBlankValues() {
        assertNull(SemesterUtils.parseTermSemester(null))
        assertNull(SemesterUtils.parseTermSemester(""))
        assertNull(SemesterUtils.parseTermSemester("   "))
        assertNull(SemesterUtils.parseTermSemester("1A"))
        assertNull(SemesterUtils.parseTermSemester("2B"))
        assertNull(SemesterUtils.parseTermSemester("3D"))
        assertNull(SemesterUtils.parseTermSemester("General"))
    }

    @Test
    fun resolveFacultySemesterCount_andCatalogMapping_supportsNSemestersAndNamedTerms() {
        val twoTerms = listOf(
            TermCatalogEntry(id = "1C", name = "Primer Cuatrimestre", path = "1C.json"),
            TermCatalogEntry(id = "2C", name = "Segundo Cuatrimestre", path = "2C.json")
        )
        assertEquals(2, SemesterUtils.resolveFacultySemesterCount(twoTerms))
        assertEquals(mapOf("1C" to 1, "2C" to 2), SemesterUtils.resolveCatalogTermSemesters(twoTerms))
        assertTrue(SemesterUtils.extractCustomCatalogLabels(twoTerms).isEmpty())

        val threeTrimesters = listOf(
            TermCatalogEntry(id = "1T", name = "1er Trimestre", path = "1T.json"),
            TermCatalogEntry(id = "2T", name = "2º Trimestre", path = "2T.json"),
            TermCatalogEntry(id = "3T", name = "3er Trimestre", path = "3T.json")
        )
        assertEquals(3, SemesterUtils.resolveFacultySemesterCount(threeTrimesters))
        assertEquals(mapOf("1T" to 1, "2T" to 2, "3T" to 3), SemesterUtils.resolveCatalogTermSemesters(threeTrimesters))

        val namedTerms = listOf(
            TermCatalogEntry(id = "fall", name = "Fall", path = "fall.json"),
            TermCatalogEntry(id = "winter", name = "Winter", path = "winter.json"),
            TermCatalogEntry(id = "spring", name = "Spring", path = "spring.json"),
            TermCatalogEntry(id = "summer", name = "Summer", path = "summer.json")
        )
        assertEquals(4, SemesterUtils.resolveFacultySemesterCount(namedTerms))
        assertEquals(
            mapOf("fall" to 1, "winter" to 2, "spring" to 3, "summer" to 4),
            SemesterUtils.resolveCatalogTermSemesters(namedTerms)
        )
        assertEquals(
            mapOf(1 to "Fall", 2 to "Winter", 3 to "Spring", 4 to "Summer"),
            SemesterUtils.extractCustomCatalogLabels(namedTerms)
        )

        val annualOnly = listOf(
            TermCatalogEntry(id = "ANUAL", name = "Curso Anual", path = "ANUAL.json")
        )
        assertEquals(1, SemesterUtils.resolveFacultySemesterCount(annualOnly))

        val singlePartialCuat = listOf(
            TermCatalogEntry(id = "1C", name = "Primer Cuatrimestre", path = "1C.json")
        )
        assertEquals(2, SemesterUtils.resolveFacultySemesterCount(singlePartialCuat))

        val singlePartialTrim = listOf(
            TermCatalogEntry(id = "1T", name = "1er Trimestre", path = "1T.json")
        )
        assertEquals(3, SemesterUtils.resolveFacultySemesterCount(singlePartialTrim))
    }

    @Test
    fun findSummaryForSemester_matchesExactTermAndNeverCrossesNumberedSemesters() {
        val summaries = listOf(
            RemoteScheduleSummary(id = "1T", title = "T1", path = "1T.json", hash = "h1"),
            RemoteScheduleSummary(id = "3T", title = "T3", path = "3T.json", hash = "h3")
        )
        assertEquals("h1", SemesterUtils.findSummaryForSemester(summaries, 1)?.hash)
        assertNull(SemesterUtils.findSummaryForSemester(summaries, 2))
        assertEquals("h3", SemesterUtils.findSummaryForSemester(summaries, 3)?.hash)
    }

    @Test
    fun extractIncomingHashForSemester_supportsVariousPayloadFormats() {
        val fcmData = mapOf(
            "hash_1T" to "abc1",
            "hash_2T" to "abc2",
            "hash_3T" to "abc3",
            "hash_Q4" to "abc4"
        )
        assertEquals("abc1", SemesterUtils.extractIncomingHashForSemester(fcmData, 1))
        assertEquals("abc3", SemesterUtils.extractIncomingHashForSemester(fcmData, 3))
        assertEquals("abc4", SemesterUtils.extractIncomingHashForSemester(fcmData, 4))
        assertNull(SemesterUtils.extractIncomingHashForSemester(fcmData, 5))

        val singleSemPayload = mapOf("semester" to "3T", "hash" to "single_3t_hash")
        assertEquals("single_3t_hash", SemesterUtils.extractIncomingHashForSemester(singleSemPayload, 3))
        assertNull(SemesterUtils.extractIncomingHashForSemester(singleSemPayload, 1))
    }
}
