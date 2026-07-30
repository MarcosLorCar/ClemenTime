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
    modifier: Modifier = Modifier
) {
    val options = listOf(1, 2)
    
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
                        text = stringResource(if (semester == 1) R.string.semester_1_label else R.string.semester_2_label)
                    )
                }
            )
        }
    }
}
