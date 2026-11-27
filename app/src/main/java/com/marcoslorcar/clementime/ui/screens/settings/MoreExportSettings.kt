package com.marcoslorcar.clementime.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.ui.components.periodLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreExportSettings(periodConfig: PeriodConfig, actions: MoreActions) {
    val context = LocalContext.current
    var icsExportStep by remember { mutableIntStateOf(0) } // 0: Idle, 1..N+1: Boundary dates
    var icsBoundaries by remember { mutableStateOf<List<LocalDate>>(emptyList()) }
    val totalIcsSteps = periodConfig.count + 1

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            actions.onExportData(context, uri) { status ->
                when (status) {
                    is ExportStatus.Success -> Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                    is ExportStatus.Error -> Toast.makeText(context, status.error, Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
        }
    }

    val icsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null && icsBoundaries.size == totalIcsSteps) {
            actions.onExportIcsBoundaries(context, uri, icsBoundaries) { status ->
                when (status) {
                    is ExportStatus.Success -> Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                    is ExportStatus.Error -> Toast.makeText(context, status.error, Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
        }
    }

    if (icsExportStep > 0) {
        key(icsExportStep) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = {
                    icsExportStep = 0
                    icsBoundaries = emptyList()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val millis = datePickerState.selectedDateMillis
                            if (millis != null) {
                                val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                val updated = icsBoundaries.take(icsExportStep - 1) + date
                                icsBoundaries = updated

                                if (icsExportStep < totalIcsSteps) {
                                    icsExportStep += 1
                                } else {
                                    icsExportStep = 0
                                    icsExportLauncher.launch("ClemenTime_Full_Schedule.ics")
                                }
                            }
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) {
                        Text(if (icsExportStep < totalIcsSteps) stringResource(R.string.onboarding_next) else stringResource(R.string.save_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        icsExportStep = 0
                        icsBoundaries = emptyList()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    title = {
                        AnimatedContent(
                            targetState = icsExportStep,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it }).togetherWith(fadeOut() + slideOutVertically { -it })
                            },
                            label = "icsStepAnimation"
                        ) { step ->
                            if (step <= 0) return@AnimatedContent
                            Column(
                                modifier = Modifier
                                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.export_ics_step_indicator, step, totalIcsSteps).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                val titleText = when {
                                    step == 1 -> stringResource(R.string.export_ics_step_course_start)
                                    step >= totalIcsSteps -> stringResource(R.string.export_ics_step_course_end)
                                    else -> stringResource(
                                        R.string.export_ics_step_period_start,
                                        periodLabel(semester = step, type = periodConfig.type)
                                    )
                                }
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    },
                    showModeToggle = false
                )
            }
        }
    }
    // Data & Storage
    Text(
        text = stringResource(R.string.data_storage_header),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )

    SettingItem(
        icon = Icons.Default.SaveAlt,
        title = stringResource(R.string.export_backup_setting_title),
        subtitle = stringResource(R.string.export_backup_desc),
        onClick = { exportLauncher.launch("ClemenTime_Backup_${System.currentTimeMillis()}.json") }
    )

    SettingItem(
        icon = Icons.Default.CalendarMonth,
        title = stringResource(R.string.export_ics_setting_title),
        subtitle = stringResource(R.string.export_ics_desc),
        onClick = {
            icsBoundaries = emptyList()
            icsExportStep = 1
        }
    )
}
