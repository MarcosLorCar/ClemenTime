package com.marcoslorcar.clementime.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.PeriodType

@Composable
fun periodLabel(
    semester: Int,
    type: PeriodType,
    compact: Boolean = false
): String {
    return when (type) {
        PeriodType.ANNUAL -> stringResource(
            if (compact) R.string.period_annual_compact else R.string.period_annual_label
        )
        PeriodType.SEMESTERS -> when (semester) {
            1 -> stringResource(if (compact) R.string.period_semester_compact_1 else R.string.semester_1_label)
            2 -> stringResource(if (compact) R.string.period_semester_compact_2 else R.string.semester_2_label)
            else -> stringResource(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.TRIMESTERS -> when (semester) {
            1 -> stringResource(if (compact) R.string.period_trimester_compact_1 else R.string.period_trimester_1)
            2 -> stringResource(if (compact) R.string.period_trimester_compact_2 else R.string.period_trimester_2)
            3 -> stringResource(if (compact) R.string.period_trimester_compact_3 else R.string.period_trimester_3)
            else -> stringResource(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.QUARTERS -> when (semester) {
            1 -> stringResource(if (compact) R.string.period_quarter_compact_1 else R.string.period_quarter_1)
            2 -> stringResource(if (compact) R.string.period_quarter_compact_2 else R.string.period_quarter_2)
            3 -> stringResource(if (compact) R.string.period_quarter_compact_3 else R.string.period_quarter_3)
            4 -> stringResource(if (compact) R.string.period_quarter_compact_4 else R.string.period_quarter_4)
            else -> stringResource(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.CUSTOM -> stringResource(
            if (compact) R.string.period_custom_compact else R.string.period_custom_label,
            semester
        )
    }
}

@Composable
fun periodTypeTitle(type: PeriodType): String {
    return when (type) {
        PeriodType.ANNUAL -> stringResource(R.string.period_annual_title)
        PeriodType.SEMESTERS -> stringResource(R.string.period_semesters_title)
        PeriodType.TRIMESTERS -> stringResource(R.string.period_trimesters_title)
        PeriodType.QUARTERS -> stringResource(R.string.period_quarters_title)
        PeriodType.CUSTOM -> stringResource(R.string.period_custom_title)
    }
}

@Composable
fun periodConfigSummaryLabel(config: PeriodConfig): String {
    val title = periodTypeTitle(config.type)
    return if (config.type == PeriodType.ANNUAL) {
        title
    } else {
        stringResource(R.string.period_config_summary_with_count, title, config.count)
    }
}

fun Context.getPeriodLabel(
    semester: Int,
    type: PeriodType,
    compact: Boolean = false
): String {
    return when (type) {
        PeriodType.ANNUAL -> getString(
            if (compact) R.string.period_annual_compact else R.string.period_annual_label
        )
        PeriodType.SEMESTERS -> when (semester) {
            1 -> getString(if (compact) R.string.period_semester_compact_1 else R.string.semester_1_label)
            2 -> getString(if (compact) R.string.period_semester_compact_2 else R.string.semester_2_label)
            else -> getString(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.TRIMESTERS -> when (semester) {
            1 -> getString(if (compact) R.string.period_trimester_compact_1 else R.string.period_trimester_1)
            2 -> getString(if (compact) R.string.period_trimester_compact_2 else R.string.period_trimester_2)
            3 -> getString(if (compact) R.string.period_trimester_compact_3 else R.string.period_trimester_3)
            else -> getString(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.QUARTERS -> when (semester) {
            1 -> getString(if (compact) R.string.period_quarter_compact_1 else R.string.period_quarter_1)
            2 -> getString(if (compact) R.string.period_quarter_compact_2 else R.string.period_quarter_2)
            3 -> getString(if (compact) R.string.period_quarter_compact_3 else R.string.period_quarter_3)
            4 -> getString(if (compact) R.string.period_quarter_compact_4 else R.string.period_quarter_4)
            else -> getString(
                if (compact) R.string.period_custom_compact else R.string.period_custom_label,
                semester
            )
        }
        PeriodType.CUSTOM -> getString(
            if (compact) R.string.period_custom_compact else R.string.period_custom_label,
            semester
        )
    }
}
