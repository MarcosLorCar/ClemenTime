package com.marcoslorcar.clementime.data

import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicPeriodTest {

    @Test
    fun periodConfig_normalized_clampsCustomAndEnforcesPresetCounts() {
        assertEquals(PeriodConfig(PeriodType.ANNUAL, 1), PeriodConfig.normalized(PeriodType.ANNUAL, 5))
        assertEquals(PeriodConfig(PeriodType.SEMESTERS, 2), PeriodConfig.normalized(PeriodType.SEMESTERS, 3))
        assertEquals(PeriodConfig(PeriodType.TRIMESTERS, 3), PeriodConfig.normalized(PeriodType.TRIMESTERS, 1))
        assertEquals(PeriodConfig(PeriodType.QUARTERS, 4), PeriodConfig.normalized(PeriodType.QUARTERS, 2))
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 1), PeriodConfig.normalized(PeriodType.CUSTOM, 0))
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 6), PeriodConfig.normalized(PeriodType.CUSTOM, 6))
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 8), PeriodConfig.normalized(PeriodType.CUSTOM, 12))
    }

    @Test
    fun periodConfig_expandToInclude_transitionsNaturally() {
        val semesters = PeriodConfig(PeriodType.SEMESTERS, 2)
        assertEquals(semesters, semesters.expandToInclude(2))
        assertEquals(PeriodConfig(PeriodType.TRIMESTERS, 3), semesters.expandToInclude(3))
        assertEquals(PeriodConfig(PeriodType.QUARTERS, 4), semesters.expandToInclude(4))
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 6), semesters.expandToInclude(6))

        val trimesters = PeriodConfig(PeriodType.TRIMESTERS, 3)
        assertEquals(PeriodConfig(PeriodType.QUARTERS, 4), trimesters.expandToInclude(4))
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 5), trimesters.expandToInclude(5))

        val annual = PeriodConfig(PeriodType.ANNUAL, 1)
        assertEquals(PeriodConfig(PeriodType.CUSTOM, 3), annual.expandToInclude(3))
    }

    @Test
    fun periodConfig_inferFromCatalog_handlesExplicitAndImplicitTerms() {
        assertEquals(
            PeriodConfig(PeriodType.TRIMESTERS, 3),
            PeriodConfig.inferFromCatalog("trimesters", null, emptyList())
        )
        assertEquals(
            PeriodConfig(PeriodType.CUSTOM, 5),
            PeriodConfig.inferFromCatalog("custom", 5, emptyList())
        )
        assertEquals(
            PeriodConfig(PeriodType.TRIMESTERS, 3),
            PeriodConfig.inferFromCatalog(null, null, listOf("1T", "2T", "3T"))
        )
        assertEquals(
            PeriodConfig(PeriodType.QUARTERS, 4),
            PeriodConfig.inferFromCatalog(null, null, listOf("1Q", "2Q", "3Q", "4Q"))
        )
        // Single 1C term should not collapse a 2-semester faculty into Annual
        assertEquals(
            PeriodConfig(PeriodType.SEMESTERS, 2),
            PeriodConfig.inferFromCatalog(null, null, listOf("1C"))
        )
        assertEquals(
            PeriodConfig(PeriodType.ANNUAL, 1),
            PeriodConfig.inferFromCatalog(null, null, listOf("ANUAL"))
        )
        assertEquals(
            PeriodConfig(PeriodType.CUSTOM, 6),
            PeriodConfig.inferFromCatalog(null, null, listOf("1P", "2P", "3P", "4P", "5P", "6P"))
        )
        assertNull(PeriodConfig.inferFromCatalog(null, null, emptyList()))
    }

    @Test
    fun termCodeParser_parsesVariousFormatsAndScopedIds() {
        assertEquals(1, TermCodeParser.parseTermIndex("1C"))
        assertEquals(2, TermCodeParser.parseTermIndex("2c"))
        assertEquals(3, TermCodeParser.parseTermIndex("3T"))
        assertEquals(4, TermCodeParser.parseTermIndex("4Q"))
        assertEquals(5, TermCodeParser.parseTermIndex("5P"))
        assertEquals(3, TermCodeParser.parseTermIndex("P3"))
        assertEquals(1, TermCodeParser.parseTermIndex("ANUAL"))
        assertEquals(1, TermCodeParser.parseTermIndex("1A"))
        assertEquals(2, TermCodeParser.parseTermIndex("uclm_esi_2C"))
        assertEquals(3, TermCodeParser.parseTermIndex("uni_fac_3T"))
        assertEquals(7, TermCodeParser.parseTermIndex("7"))
        assertNull(TermCodeParser.parseTermIndex("0"))
        assertNull(TermCodeParser.parseTermIndex("9C"))
        assertNull(TermCodeParser.parseTermIndex(""))
        assertNull(TermCodeParser.parseTermIndex(null))
    }

    @Test
    fun termCodeParser_formatAndSummaryMatching() {
        assertEquals("1A", TermCodeParser.formatTermCode(1, PeriodType.ANNUAL))
        assertEquals("2C", TermCodeParser.formatTermCode(2, PeriodType.SEMESTERS))
        assertEquals("3T", TermCodeParser.formatTermCode(3, PeriodType.TRIMESTERS))
        assertEquals("4Q", TermCodeParser.formatTermCode(4, PeriodType.QUARTERS))
        assertEquals("5P", TermCodeParser.formatTermCode(5, PeriodType.CUSTOM))

        val summary = RemoteScheduleSummary(
            id = "3T",
            title = "3er Trimestre",
            path = "schedules/uni/fac/3T.json"
        )
        assertTrue(TermCodeParser.matchesSummary(summary, 3))
        assertFalse(TermCodeParser.matchesSummary(summary, 2))
    }
}
