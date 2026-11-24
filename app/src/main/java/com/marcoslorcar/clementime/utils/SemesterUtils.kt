package com.marcoslorcar.clementime.utils

import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.marcoslorcar.clementime.data.importing.model.TermCatalogEntry

/**
 * Pure Kotlin utilities for N-semester / multi-term resolution, formatting, and catalog mapping.
 * Kept free of Android framework dependencies for fast JVM unit testing.
 */
object SemesterUtils {

    const val DEFAULT_SEMESTERS_PER_YEAR = 2
    const val MIN_SEMESTERS_PER_YEAR = 1
    const val MAX_CONFIGURABLE_SEMESTERS = 6

    val CONFIGURABLE_SEMESTER_COUNTS: List<Int> =
        (MIN_SEMESTERS_PER_YEAR..MAX_CONFIGURABLE_SEMESTERS).toList()

    private val ANNUAL_TOKENS = setOf(
        "ANUAL", "ANNUAL", "YEAR", "YEARLY", "FULL", "UNICO", "ÚNICO"
    )

    private val GENERIC_SPANISH_CUATRIMESTRE_NAMES = setOf(
        "PRIMER CUATRIMESTRE",
        "SEGUNDO CUATRIMESTRE",
        "TERCER CUATRIMESTRE",
        "CUARTO CUATRIMESTRE",
        "QUINTO CUATRIMESTRE",
        "SEXTO CUATRIMESTRE",
        "1º CUATRIMESTRE",
        "2º CUATRIMESTRE",
        "3º CUATRIMESTRE",
        "4º CUATRIMESTRE",
        "5º CUATRIMESTRE",
        "6º CUATRIMESTRE",
        "1ER CUATRIMESTRE",
        "2DO CUATRIMESTRE",
        "3ER CUATRIMESTRE"
    )

    // Prefix forms: C1, T2, S3, Q4, P1, SEM 2, TERM 3, TRIM 1, CUAT 2, QUARTER 4, PERIODO 2
    private val PREFIX_TERM_REGEX = Regex(
        """^(?:CUATRIMESTRE|TRIMESTRE|TRIMESTER|SEMESTER|SEMESTRE|QUARTER|PERIODO|PERIOD|CUAT|TRIM|TERM|TRI|SEM|[CTSQP])[-_\s]*([1-9]\d*)$""",
        RegexOption.IGNORE_CASE
    )

    // Suffix forms: 1C, 2T, 3S, 4Q, 1P, 1º, 2ª, 1ST, 2ND, 3RD, 4TH, 1ER, 2DO, 3RO, 4TO, 1º CUATRIMESTRE, 2ND SEMESTER
    private val SUFFIX_TERM_REGEX = Regex(
        """^([1-9]\d*)(?:[ºª]|ST|ND|RD|TH|ER|DO|RO|TO|MO)?[-_\s]*(?:CUATRIMESTRE|TRIMESTRE|TRIMESTER|SEMESTER|SEMESTRE|QUARTER|PERIODO|PERIOD|CUAT|TRIM|TERM|TRI|SEM|[CTSQP])?$""",
        RegexOption.IGNORE_CASE
    )

    private val TRIMESTER_HINT_REGEX = Regex(
        """(?:^|[-_\s0-9])(TRIMESTRE|TRIMESTER|TRIM|TRI|T)(?:[-_\s0-9]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val QUARTER_HINT_REGEX = Regex(
        """(?:^|[-_\s0-9])(QUARTER|Q)(?:[-_\s0-9]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val SEMESTER_HINT_REGEX = Regex(
        """(?:^|[-_\s0-9])(CUATRIMESTRE|SEMESTER|SEMESTRE|CUAT|SEM|C|S)(?:[-_\s0-9]|$)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractCandidateToken(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.contains("/")) {
            cleaned = cleaned.substringAfterLast("/")
        }
        if (cleaned.contains("?")) {
            cleaned = cleaned.substringBefore("?")
        }
        if (cleaned.endsWith(".json", ignoreCase = true)) {
            cleaned = cleaned.dropLast(5)
        }
        return cleaned.trim()
    }

    /**
     * Parses a term/semester identifier (e.g., `"1C"`, `"3C"`, `"2T"`, `"Q4"`, `"S1"`, `"3"`,
     * `"uclm_esi_3T"`, `"https://.../3C.json"`, `"ANUAL"`) into a 1-based integer semester,
     * or `null` if it does not represent a recognizable term identifier.
     */
    fun parseTermSemester(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val token = extractCandidateToken(raw)
        if (token.isEmpty()) return null

        parseSingleToken(token)?.let { return it }

        if (token.contains("_")) {
            val suffix = token.substringAfterLast("_").trim()
            if (suffix.isNotEmpty()) {
                parseSingleToken(suffix)?.let { return it }
            }
        }
        return null
    }

    private fun parseSingleToken(token: String): Int? {
        val upper = token.uppercase()
        if (upper in ANNUAL_TOKENS) return 1

        token.toIntOrNull()?.takeIf { it >= 1 }?.let { return it }

        PREFIX_TERM_REGEX.matchEntire(token)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it >= 1 }
        }

        // Guard against matching course group codes like "1A", "2B", "3D":
        // SUFFIX_TERM_REGEX only allows [CTSQP] single-letter suffixes, not A/B/D/E/etc.
        // Note: "1C", "2C", "3C" are valid semester codes in cuatrimestre notation.
        SUFFIX_TERM_REGEX.matchEntire(token)?.let { match ->
            val hasExplicitUnitOrOrdinal = token.any { !it.isDigit() }
            if (hasExplicitUnitOrOrdinal) {
                return match.groupValues[1].toIntOrNull()?.takeIf { it >= 1 }
            }
        }

        return null
    }

    fun isAnnualTermId(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val token = extractCandidateToken(raw)
        val lastPart = if (token.contains("_")) token.substringAfterLast("_") else token
        return token.uppercase() in ANNUAL_TOKENS || lastPart.uppercase() in ANNUAL_TOKENS
    }

    /**
     * Infers the minimum number of terms per year implied by a single term identifier
     * (e.g. `"1T"` -> 3, `"Q1"` -> 4, `"1C"` / `"1S"` -> 2, `"ANUAL"` -> 1).
     */
    fun inferMinSemestersFromTermId(raw: String?): Int {
        if (raw.isNullOrBlank()) return DEFAULT_SEMESTERS_PER_YEAR
        if (isAnnualTermId(raw)) return 1
        val token = extractCandidateToken(raw)
        val candidate = if (token.contains("_")) token.substringAfterLast("_") else token
        val parsed = parseSingleToken(candidate) ?: 1
        return when {
            QUARTER_HINT_REGEX.containsMatchIn(candidate) -> maxOf(parsed, 4)
            TRIMESTER_HINT_REGEX.containsMatchIn(candidate) -> maxOf(parsed, 3)
            SEMESTER_HINT_REGEX.containsMatchIn(candidate) -> maxOf(parsed, 2)
            else -> parsed
        }
    }

    /**
     * Maps each [TermCatalogEntry.id] to its 1-based semester index.
     * Uses [parseTermSemester] when the ID or path carries a term number, falling back
     * to positional 1-based order for custom non-numeric IDs (e.g., `fall`, `winter`, `spring`).
     */
    fun resolveCatalogTermSemesters(terms: List<TermCatalogEntry>): Map<String, Int> {
        if (terms.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Int>(terms.size)
        val used = mutableSetOf<Int>()

        for ((index, term) in terms.withIndex()) {
            val parsed = parseTermSemester(term.id) ?: parseTermSemester(term.path)
            val chosen = if (parsed != null && (parsed !in used || terms.size == 1)) {
                parsed
            } else {
                var candidate = index + 1
                while (candidate in used) candidate++
                candidate
            }
            result[term.id] = chosen
            used.add(chosen)
        }
        return result
    }

    /**
     * Resolves how many terms per year a faculty schedule uses based on its catalog entries.
     */
    fun resolveFacultySemesterCount(terms: List<TermCatalogEntry>): Int {
        if (terms.isEmpty()) return DEFAULT_SEMESTERS_PER_YEAR
        val resolved = resolveCatalogTermSemesters(terms).values
        if (terms.size == 1) {
            val single = terms.first()
            return maxOf(resolved.first(), inferMinSemestersFromTermId(single.id))
        }
        return maxOf(terms.size, resolved.maxOrNull() ?: terms.size)
    }

    /**
     * Extracts custom display labels from catalog terms when they are not generic default
     * `"Primer/Segundo/... Cuatrimestre"` placeholders (allowing standard cuatrimestres to use
     * localized app resources in English/Spanish while preserving custom names like `"Fall Quarter"`).
     */
    fun extractCustomCatalogLabels(terms: List<TermCatalogEntry>): Map<Int, String> {
        if (terms.isEmpty()) return emptyMap()
        val resolved = resolveCatalogTermSemesters(terms)
        val labels = mutableMapOf<Int, String>()
        for (term in terms) {
            val sem = resolved[term.id] ?: continue
            val cleanName = term.name.trim()
            if (cleanName.isEmpty()) continue
            val isGenericCuatrimestre = cleanName.uppercase() in GENERIC_SPANISH_CUATRIMESTRE_NAMES ||
                Regex("""^CUATRIMESTRE\s+\d+C?$""", RegexOption.IGNORE_CASE).matches(cleanName)
            if (!isGenericCuatrimestre) {
                labels[sem] = cleanName
            }
        }
        return labels
    }

    fun resolveSummarySemesters(summaries: List<RemoteScheduleSummary>): Map<String, Int> {
        if (summaries.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Int>(summaries.size)
        val used = mutableSetOf<Int>()
        for ((index, summary) in summaries.withIndex()) {
            val parsed = parseTermSemester(summary.id) ?: parseTermSemester(summary.path)
            val chosen = if (parsed != null && (parsed !in used || summaries.size == 1)) {
                parsed
            } else {
                var candidate = index + 1
                while (candidate in used) candidate++
                candidate
            }
            result[summary.id] = chosen
            used.add(chosen)
        }
        return result
    }

    /**
     * Finds the [RemoteScheduleSummary] corresponding to [semester].
     * Never falls back to another numbered semester's summary (e.g. won't return `1C` when `2` is requested).
     */
    fun findSummaryForSemester(
        summaries: List<RemoteScheduleSummary>,
        semester: Int
    ): RemoteScheduleSummary? {
        if (summaries.isEmpty()) return null

        summaries.find { parseTermSemester(it.id) == semester }?.let { return it }
        summaries.find { parseTermSemester(it.path) == semester }?.let { return it }

        val noneHaveNumericIds = summaries.all {
            parseTermSemester(it.id) == null && parseTermSemester(it.path) == null
        }
        if (noneHaveNumericIds) {
            return summaries.getOrNull(semester - 1)
        }
        return null
    }

    fun matchesSemester(slotCuatrimestre: String?, targetSemester: Int): Boolean {
        return parseTermSemester(slotCuatrimestre) == targetSemester
    }

    fun formatSemesterCode(semester: Int): String = "${semester.coerceAtLeast(1)}C"

    fun defaultSpanishSemesterTitle(semester: Int): String = when (semester) {
        1 -> "Primer Cuatrimestre"
        2 -> "Segundo Cuatrimestre"
        3 -> "Tercer Cuatrimestre"
        4 -> "Cuarto Cuatrimestre"
        5 -> "Quinto Cuatrimestre"
        6 -> "Sexto Cuatrimestre"
        else -> "${semester}º Cuatrimestre"
    }

    fun englishOrdinal(number: Int): String {
        val n = number.coerceAtLeast(1)
        val mod100 = n % 100
        val suffix = if (mod100 in 11..13) {
            "th"
        } else {
            when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$n$suffix"
    }

    /**
     * Extracts the remote schedule hash for [semester] from an incoming FCM data payload.
     * Supports `hash_1C`..`hash_NC`, `hash_1T`..`hash_NT`, `hash_Q1`..`hash_QN`, `hash_1`..`hash_N`,
     * as well as single-semester `{"semester": "3T", "hash": "..."}` payloads.
     */
    fun extractIncomingHashForSemester(data: Map<String, String>, semester: Int): String? {
        data["hash_${semester}C"]?.takeIf { it.isNotBlank() }?.let { return it }
        data["hash_$semester"]?.takeIf { it.isNotBlank() }?.let { return it }

        for ((key, value) in data) {
            if (value.isNotBlank() && key.startsWith("hash_", ignoreCase = true)) {
                val termPart = key.substring(5)
                if (parseTermSemester(termPart) == semester) {
                    return value
                }
            }
        }

        val payloadSem = data["semester"]
        if (!payloadSem.isNullOrBlank() && parseTermSemester(payloadSem) == semester) {
            return data["hash"]?.takeIf { it.isNotBlank() }
        }

        return null
    }
}
