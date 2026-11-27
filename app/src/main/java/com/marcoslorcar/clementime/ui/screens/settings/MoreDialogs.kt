package com.marcoslorcar.clementime.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.ui.components.periodTypeTitle

@Composable
internal fun AcademicPeriodsDialog(
    config: PeriodConfig,
    subjectCounts: Map<Int, Int>,
    onConfirm: (PeriodConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(config.type) }
    var customCount by remember { mutableIntStateOf(config.count) }
    val candidateConfig = PeriodConfig.normalized(selectedType, customCount)
    val hiddenCount = subjectCounts
        .filterKeys { it > candidateConfig.count }
        .values
        .sum()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.academic_periods_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    PeriodType.ANNUAL,
                    PeriodType.SEMESTERS,
                    PeriodType.TRIMESTERS,
                    PeriodType.QUARTERS,
                    PeriodType.CUSTOM
                ).forEach { type ->
                    val label = if (type == PeriodType.ANNUAL) {
                        periodTypeTitle(type)
                    } else if (type == PeriodType.CUSTOM) {
                        periodTypeTitle(type)
                    } else {
                        stringResource(R.string.period_config_summary_with_count, periodTypeTitle(type), type.defaultCount)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (selectedType == PeriodType.CUSTOM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 8.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.academic_periods_custom_count_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (customCount > PeriodConfig.MIN_PERIOD_COUNT) customCount--
                                },
                                enabled = customCount > PeriodConfig.MIN_PERIOD_COUNT
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                            }
                            Text(
                                text = customCount.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (customCount < PeriodConfig.MAX_PERIOD_COUNT) customCount++
                                },
                                enabled = customCount < PeriodConfig.MAX_PERIOD_COUNT
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }
                }

                if (hiddenCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.academic_periods_hidden_subjects_warning, hiddenCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(candidateConfig)
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
internal fun RepositoryUrlDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempRepoUrl by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.github_repository_url_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.github_repository_url_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = tempRepoUrl,
                    onValueChange = { tempRepoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.online_repository_url_setting_title)) }
                )
                TextButton(
                    onClick = { tempRepoUrl = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.reset_to_default))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(tempRepoUrl)
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
