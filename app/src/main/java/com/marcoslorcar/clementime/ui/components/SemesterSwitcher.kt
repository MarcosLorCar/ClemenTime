package com.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcoslorcar.clementime.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SemesterSwitcher(
    selectedSemester: Int,
    onSemesterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // ButtonGroup's content lambda is not @Composable, so labels are resolved up front.
    val options = listOf(
        1 to stringResource(R.string.semester_1_label),
        2 to stringResource(R.string.semester_2_label)
    )

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
