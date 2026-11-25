package com.marcoslorcar.clementime.data

import com.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary

/**
 * Academic period structure presets plus custom N-period support.
 */
enum class PeriodType(val defaultCount: Int, val codeSuffix: String) {
    ANNUAL(1, "A"),
    SEMESTERS(2, "C"),
    TRIMESTERS(3, "T"),
    QUARTERS(4, "Q"),
    CUSTOM(2, "P")
}

/**
 * Persisted configuration describing how many academic periods a year has and how they are named.
 */
data class PeriodConfig(
    val type: PeriodType = PeriodType.SEMESTERS,
    val count: Int = 2
) {
    fun expandToInclude(semester: Int): PeriodConfig {
        val current = normalized(type, count)
        if (semester <= current.count) return current
        val target = semester.coerceIn(MIN_PERIOD_COUNT, MAX_PERIOD_COUNT)
        return when {
            target == 3 && current.type == PeriodType.SEMESTERS -> PeriodConfig(PeriodType.TRIMESTERS, 3)
            target == 4 && (current.type == PeriodType.SEMESTERS || current.type == PeriodType.TRIMESTERS) ->
                PeriodConfig(PeriodType.QUARTERS, 4)
            else -> PeriodConfig(PeriodType.CUSTOM, target)
        }
    }

    companion object {
        const val MIN_PERIOD_COUNT = 1
        const val MAX_PERIOD_COUNT = 8

        val DEFAULT = PeriodConfig(PeriodType.SEMESTERS, 2)

        fun normalized(type: PeriodType, count: Int): PeriodConfig {
            val resolvedCount = if (type == PeriodType.CUSTOM) {
                count.coerceIn(MIN_PERIOD_COUNT, MAX_PERIOD_COUNT)
            } else {
                type.defaultCount
            }
            return PeriodConfig(type = type, count = resolvedCount)
        }

        fun inferFromCatalog(
            periodTypeRaw: String?,
            periodCountRaw: Int?,
            termIds: List<String>
        ): PeriodConfig? {
            val explicitType = periodTypeRaw?.trim()?.uppercase()?.let { raw ->
                when (raw) {
                    "ANNUAL", "ANUAL", "YEAR" -> PeriodType.ANNUAL
                    "SEMESTER", "SEMESTERS", "CUATRIMESTRE", "CUATRIMESTRES", "SEMESTRE", "SEMESTRES" -> PeriodType.SEMESTERS
                    "TRIMESTER", "TRIMESTERS", "TRIMESTRE", "TRIMESTRES" -> PeriodType.TRIMESTERS
                    "QUARTER", "QUARTERS", "CUARTO", "CUARTOS" -> PeriodType.QUARTERS
                    "CUSTOM", "PERSONALIZADO", "PERIOD", "PERIODS", "PERIODO", "PERIODOS" -> PeriodType.CUSTOM
                    else -> runCatching { PeriodType.valueOf(raw) }.getOrNull()
                }
            }

            if (explicitType != null) {
                val count = periodCountRaw ?: explicitType.defaultCount
                return normalized(explicitType, count)
            }

            if (periodCountRaw != null) {
                val clamped = periodCountRaw.coerceIn(MIN_PERIOD_COUNT, MAX_PERIOD_COUNT)
                val inferredType = when (clamped) {
                    1 -> PeriodType.ANNUAL
                    2 -> PeriodType.SEMESTERS
                    3 -> PeriodType.TRIMESTERS
                    4 -> PeriodType.QUARTERS
                    else -> PeriodType.CUSTOM
                }
                return normalized(inferredType, clamped)
            }

            if (termIds.isEmpty()) return null

            val normalizedIds = termIds.map { it.trim().uppercase() }.filter { it.isNotBlank() }
            if (normalizedIds.isEmpty()) return null

            val maxIndex = normalizedIds.mapNotNull { TermCodeParser.parseTermIndex(it) }.maxOrNull()
                ?: normalizedIds.size.coerceIn(MIN_PERIOD_COUNT, MAX_PERIOD_COUNT)

            val tokens = normalizedIds.map { id ->
                if (id.contains("_")) id.substringAfterLast("_") else id
            }

            val hasAnnual = tokens.any { it == "ANUAL" || it == "ANNUAL" || it == "YEAR" || it == "1A" || it == "A1" }
            val hasT = tokens.any { it.endsWith("T") || it.startsWith("T") }
            val hasQ = tokens.any { it.endsWith("Q") || it.startsWith("Q") }
            val hasCOrS = tokens.any { it.endsWith("C") || it.endsWith("S") || it.startsWith("C") || it.startsWith("S") }

            return when {
                hasAnnual && maxIndex <= 1 -> PeriodConfig(PeriodType.ANNUAL, 1)
                hasT -> if (maxIndex <= 3) PeriodConfig(PeriodType.TRIMESTERS, 3) else normalized(PeriodType.CUSTOM, maxIndex)
                hasQ -> if (maxIndex <= 4) PeriodConfig(PeriodType.QUARTERS, 4) else normalized(PeriodType.CUSTOM, maxIndex)
                hasCOrS -> if (maxIndex <= 2) PeriodConfig(PeriodType.SEMESTERS, 2) else normalized(PeriodType.CUSTOM, maxIndex)
                else -> {
                    val count = maxOf(normalizedIds.size, maxIndex).coerceIn(MIN_PERIOD_COUNT, MAX_PERIOD_COUNT)
                    when (count) {
                        1 -> PeriodConfig(PeriodType.ANNUAL, 1)
                        2 -> PeriodConfig(PeriodType.SEMESTERS, 2)
                        3 -> PeriodConfig(PeriodType.TRIMESTERS, 3)
                        4 -> PeriodConfig(PeriodType.QUARTERS, 4)
                        else -> normalized(PeriodType.CUSTOM, count)
                    }
                }
            }
        }
    }
}

/**
 * Pure-Kotlin parser/formatter for wire term codes (1C, 2C, 3T, 4Q, 1A, P5, ANUAL, scoped IDs, etc.).
 */
object TermCodeParser {
    private val trailingCodeRegex = Regex("""^(\d+)[CTSQPA]?$""")
    private val leadingCodeRegex = Regex("""^[CTSQPA](\d+)$""")

    fun parseTermIndex(raw: String?): Int? {
        val clean = raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null

        if (clean.contains("_")) {
            val tail = clean.substringAfterLast("_")
            parseSingleToken(tail)?.let { return it }
        }
        return parseSingleToken(clean)
    }

    private fun parseSingleToken(token: String): Int? {
        if (token == "ANUAL" || token == "ANNUAL" || token == "YEAR" || token == "1A" || token == "A1") {
            return 1
        }

        trailingCodeRegex.matchEntire(token)?.let { match ->
            val value = match.groupValues[1].toIntOrNull() ?: return null
            if (value in PeriodConfig.MIN_PERIOD_COUNT..PeriodConfig.MAX_PERIOD_COUNT) return value
        }

        leadingCodeRegex.matchEntire(token)?.let { match ->
            val value = match.groupValues[1].toIntOrNull() ?: return null
            if (value in PeriodConfig.MIN_PERIOD_COUNT..PeriodConfig.MAX_PERIOD_COUNT) return value
        }

        val ordinalPrefix = when {
            token.startsWith("1ER") || token.startsWith("1º") || token.startsWith("1ST") || token.startsWith("PRIMER") -> 1
            token.startsWith("2º") || token.startsWith("2ND") || token.startsWith("SEGUND") -> 2
            token.startsWith("3ER") || token.startsWith("3º") || token.startsWith("3RD") || token.startsWith("TERCER") -> 3
            token.startsWith("4º") || token.startsWith("4TH") || token.startsWith("CUART") -> 4
            else -> null
        }
        if (ordinalPrefix != null) return ordinalPrefix

        return null
    }

    fun formatTermCode(semester: Int, type: PeriodType): String {
        val clamped = semester.coerceIn(PeriodConfig.MIN_PERIOD_COUNT, PeriodConfig.MAX_PERIOD_COUNT)
        return if (type == PeriodType.ANNUAL) {
            "1A"
        } else {
            "$clamped${type.codeSuffix}"
        }
    }

    fun matchesSemester(raw: String?, semester: Int): Boolean {
        return parseTermIndex(raw) == semester
    }

    fun matchesSummary(summary: RemoteScheduleSummary, semester: Int): Boolean {
        if (parseTermIndex(summary.id) == semester) return true
        val fileStem = summary.path
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
        return parseTermIndex(fileStem) == semester
    }
}
