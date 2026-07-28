package com.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcoslorcar.clementime.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSwitcher(
    selectedSemester: Int,
    onSemesterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showBothOption: Boolean = false
) {
    val options = if (showBothOption) listOf(1, 2, 3) else listOf(1, 2)
    
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        options.forEachIndexed { index, semester ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSemesterSelected(semester) },
                selected = selectedSemester == semester,
                label = {
                    Text(
                        text = when (semester) {
                            1 -> if (showBothOption) stringResource(R.string.semester_1_short) else stringResource(R.string.semester_1_label)
                            2 -> if (showBothOption) stringResource(R.string.semester_2_short) else stringResource(R.string.semester_2_label)
                            else -> stringResource(R.string.semester_both_short)
                        }
                    )
                }
            )
        }
    }
}
