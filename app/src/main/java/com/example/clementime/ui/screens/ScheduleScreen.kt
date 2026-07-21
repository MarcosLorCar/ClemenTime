package com.example.clementime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clementime.R
import com.example.clementime.data.ClassSlot
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.MatterWithSlots
import com.example.clementime.ui.navigation.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme
import com.example.clementime.utils.getNarrowLabel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onMenuClick: () -> Unit
) {
    val items by viewModel.scheduleItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    ScheduleContent(
        mattersWithSlots = items,
        selectedTab = selectedTab,
        onChangeTab = viewModel::changeTab,
        onAddItemClick = {
            viewModel.addSlot(
                matterId = 1,
                startTime = LocalTime.now(),
                endTime = LocalTime.now().plusHours(2)
            )
        },
        onDeleteSlot = viewModel::deleteSlot,
        onMenuClick = onMenuClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(
    mattersWithSlots: List<MatterWithSlots>,
    selectedTab: ScheduleTab,
    onChangeTab: (ScheduleTab) -> Unit,
    onAddItemClick: () -> Unit,
    onDeleteSlot: (ClassSlot) -> Unit,
    onMenuClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                ClemenTimeTopBar(
                    onMenuClick = onMenuClick,
                    title = stringResource(R.string.schedule_screen_title)
                ) {
                    IconButton(onClick = onAddItemClick) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Slot")
                    }
                }

                val locale = LocalConfiguration.current.locales[0]

                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    ScheduleTab.entries.forEachIndexed { index, day ->
                        val selected = index == selectedTab.ordinal
                        Tab(
                            selected = selected,
                            onClick = { onChangeTab(day) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.dayOfWeek.getNarrowLabel(locale).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Flatten matters into pairs of (Matter, ClassSlot) matching the current day
        val daySlots = mattersWithSlots.flatMap { matterWithSlots ->
            matterWithSlots.slots
                .filter { it.dayOfWeek == selectedTab.dayOfWeek && it.isSelected }
                .map { slot -> matterWithSlots.matter to slot }
        }.sortedBy { it.second.startTime }

        if (daySlots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No classes scheduled for today.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = daySlots,
                    key = { (_, slot) -> slot.id }
                ) { (matter, slot) ->
                    ClassSlotRow(
                        matter = matter,
                        slot = slot,
                        onDelete = { onDeleteSlot(slot) }
                    )
                }
            }
        }
    }
}

enum class ScheduleTab(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    TUESDAY(DayOfWeek.TUESDAY),
    WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY),
    FRIDAY(DayOfWeek.FRIDAY),
}

@Composable
fun ClassSlotRow(
    matter: Matter,
    slot: ClassSlot,
    onDelete: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(matter.color), CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = matter.name,
                    style = MaterialTheme.typography.titleMedium
                )

                val timeRange = "${slot.startTime.format(timeFormatter)} - ${slot.endTime.format(timeFormatter)}"
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.bodySmall
                )

                if (!slot.classroom.isNullOrEmpty() || !slot.labGroupName.isNullOrEmpty()) {
                    val detail = listOfNotNull(slot.labGroupName, slot.classroom).joinToString(" • ")
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (slot.entryType == EntryType.LAB) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "LAB",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Slot",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// =====================================================================
// 3. COMPOSE PREVIEW
// =====================================================================
@Preview(showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    val previewMatter = Matter(
        id = 1,
        name = "Computer Architecture",
        color = 0xFF6200EE.toInt(),
        code = "ARCH"
    )

    val previewData = listOf(
        MatterWithSlots(
            matter = previewMatter,
            slots = listOf(
                ClassSlot(
                    id = 1,
                    matterId = 1,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(11, 0),
                    classroom = "A1.1",
                    labGroupName = "Teoría",
                    isSelected = true
                ),
                ClassSlot(
                    id = 2,
                    matterId = 1,
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(11, 30),
                    endTime = LocalTime.of(13, 30),
                    classroom = "L1.2",
                    labGroupName = "Lab A1",
                    entryType = EntryType.LAB,
                    isSelected = true
                )
            )
        )
    )

    ClemenTimeTheme {
        ScheduleContent(
            mattersWithSlots = previewData,
            selectedTab = ScheduleTab.MONDAY,
            onChangeTab = {},
            onAddItemClick = {},
            onDeleteSlot = {},
            onMenuClick = {}
        )
    }
}