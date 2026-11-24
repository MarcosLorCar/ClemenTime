package com.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.utils.fadingEdges

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SemesterSwitcher(
    selectedSemester: Int,
    onSemesterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    periodConfig: PeriodConfig = PeriodConfig.DEFAULT,
    minVisibleCount: Int = periodConfig.count
) {
    val effectiveCount = maxOf(periodConfig.count, minVisibleCount)
        .coerceIn(PeriodConfig.MIN_PERIOD_COUNT, PeriodConfig.MAX_PERIOD_COUNT)

    if (effectiveCount <= 1) return

    if (effectiveCount <= 3) {
        val compact = effectiveCount == 3
        // ButtonGroup's content lambda is not @Composable, so labels are resolved up front.
        val options = (1..effectiveCount).map { semester ->
            semester to periodLabel(semester = semester, type = periodConfig.type, compact = compact)
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
    } else {
        val listState = rememberLazyListState()
        val periods = (1..effectiveCount).toList()

        LaunchedEffect(selectedSemester, effectiveCount) {
            val targetIndex = (selectedSemester - 1).coerceIn(0, effectiveCount - 1)
            listState.animateScrollToItem(targetIndex)
        }

        LazyRow(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .fadingEdges(
                    state = listState,
                    topEdgeHeight = 16.dp,
                    bottomEdgeHeight = 16.dp,
                    horizontal = true
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(periods, key = { it }) { semester ->
                val label = periodLabel(
                    semester = semester,
                    type = periodConfig.type,
                    compact = false
                )
                FilterChip(
                    selected = selectedSemester == semester,
                    onClick = { onSemesterSelected(semester) },
                    label = { Text(label) }
                )
            }
        }
    }
}
