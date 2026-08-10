package com.marcoslorcar.clementime.ui.screens.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadialTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onTimeConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onTimeConfirm(time)
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.time_picker_dialog_title)) }
    ) {
        TimePicker(state = timePickerState)
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableIntStateOf((initialColor.red * 255).toInt()) }
    var green by remember { mutableIntStateOf((initialColor.green * 255).toInt()) }
    var blue by remember { mutableIntStateOf((initialColor.blue * 255).toInt()) }

    var hexText by remember {
        mutableStateOf(
            String.format("%02X%02X%02X", red, green, blue)
        )
    }

    val currentColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val sanitized = input.uppercase().take(6)
                        hexText = sanitized
                        if (sanitized.length == 6) {
                            runCatching {
                                val r = sanitized.substring(0, 2).toInt(16)
                                val g = sanitized.substring(2, 4).toInt(16)
                                val b = sanitized.substring(4, 6).toInt(16)
                                red = r
                                green = g
                                blue = b
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.hex_code_label)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(stringResource(R.string.color_red, red), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = red.toFloat(),
                        onValueChange = {
                            red = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }

                Column {
                    Text(stringResource(R.string.color_green, green), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = green.toFloat(),
                        onValueChange = {
                            green = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }

                Column {
                    Text(stringResource(R.string.color_blue, blue), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = blue.toFloat(),
                        onValueChange = {
                            blue = it.toInt()
                            hexText = String.format("%02X%02X%02X", red, green, blue)
                        },
                        valueRange = 0f..255f
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor) }) {
                Text(stringResource(R.string.select_color_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
