package com.example.clementime.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clementime.R
import com.example.clementime.data.ScheduleItem
import com.example.clementime.ui.navigation.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme
import com.example.clementime.utils.getNarrowLabel
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    items: List<ScheduleItem>,
    onAddItemClick: () -> Unit,
    onToggle: (ScheduleItem) -> Unit,
    onDelete: (ScheduleItem) -> Unit,
    selectedTab: ScheduleTab = ScheduleTab.MONDAY,
    onChangeTab: (ScheduleTab) -> Unit = {},
    onMenuClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    onMenuClick = onMenuClick,
                    title = stringResource(R.string.schedule_screen_title)
                )

                val locale = LocalConfiguration.current.locales[0]
                
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
//                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
//                    minTabWidth = 90.dp
                ) {
                    ScheduleTab.entries.forEachIndexed { index, day ->
                        Tab(
                            selected = index == selectedTab.ordinal,
                            onClick = { onChangeTab(day) },
                            selectedContentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                text = day.dayOfWeek.getNarrowLabel(locale).uppercase(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Your schedule is empty. Add something!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    ScheduleItemRow(
                        item = item,
                        onToggle = { onToggle(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

enum class ScheduleTab(
    val dayOfWeek: DayOfWeek
) {
    MONDAY(DayOfWeek.MONDAY),
    TUESDAY(DayOfWeek.TUESDAY),
    WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY),
    FRIDAY(DayOfWeek.FRIDAY),
//    SATURDAY(DayOfWeek.SATURDAY),
//    SUNDAY(DayOfWeek.SUNDAY)
}

@Composable
fun ScheduleItemRow(
    item: ScheduleItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Row {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClemenTimeAppPreview() {
    ClemenTimeTheme {
        ScheduleScreen(
            items = List(3) { i ->
                ScheduleItem(
                    id = i.toLong(),
                    title = "Task $i",
                    description = "Description for task $i",
                    startTime = 0,
                    endTime = 0,
                    isCompleted = i % 3 == 0
                )
            },
            onAddItemClick = {},
            onToggle = {},
            onDelete = {},
            onMenuClick = {},
        )
    }
}