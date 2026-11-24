package com.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.SettingsRepository

@Composable
fun resolveFullSemesterLabel(
    semester: Int,
    customLabels: Map<Int, String> = emptyMap()
): String {
    customLabels[semester]?.takeIf { it.isNotBlank() }?.let { return it }
    return when (semester) {
        1 -> stringResource(R.string.semester_1_label)
        2 -> stringResource(R.string.semester_2_label)
        3 -> stringResource(R.string.semester_3_label)
        4 -> stringResource(R.string.semester_4_label)
        5 -> stringResource(R.string.semester_5_label)
        6 -> stringResource(R.string.semester_6_label)
        else -> stringResource(R.string.semester_generic_label, semester)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SemesterSwitcher(
    selectedSemester: Int,
    onSemesterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semesterCount: Int = SettingsRepository.DEFAULT_SEMESTERS_PER_YEAR,
    customLabels: Map<Int, String> = emptyMap()
) {
    val effectiveCount = maxOf(semesterCount.coerceAtLeast(1), selectedSemester.coerceAtLeast(1))

    // ButtonGroup's content lambda is not @Composable, so labels are resolved up front.
    val options = (1..effectiveCount).map { semester ->
        val custom = customLabels[semester]?.takeIf { it.isNotBlank() }
        val label = when {
            effectiveCount <= 2 -> resolveFullSemesterLabel(semester, customLabels)
            effectiveCount <= 4 -> {
                if (custom != null && custom.length <= 12) custom
                else stringResource(R.string.semester_medium_format, semester)
            }
            else -> {
                if (custom != null && custom.length <= 5) custom
                else stringResource(R.string.semester_compact_format, semester)
            }
        }
        semester to label
    }

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth()
    ) {
        options.forEach { (semester, label) ->
            toggleableItem(
                checked = selectedSemester == semester,
                label = label,
                onCheckedChange = { if (it) onSemesterSelected(semester) },
                weight = 1f
            )
        }
    }
}
