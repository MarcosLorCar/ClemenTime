package com.marcoslorcar.clementime.ui.screens.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.marcoslorcar.clementime.R

@Composable
internal fun MoreDebugSection(actions: MoreActions) {
    val context = LocalContext.current
    MoreSectionTitle(R.string.developer_settings_header)

    val debugActionTriggeredMsg = stringResource(R.string.debug_action_triggered)
    var debugStatusDialogText by remember { mutableStateOf<String?>(null) }

    if (debugStatusDialogText != null) {
        AlertDialog(
            onDismissRequest = { debugStatusDialogText = null },
            title = { Text(stringResource(R.string.debug_fcm_status_title)) },
            text = { Text(debugStatusDialogText ?: "") },
            confirmButton = {
                TextButton(onClick = { debugStatusDialogText = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    SettingItem(
        icon = Icons.Default.Info,
        title = stringResource(R.string.debug_fcm_status_title),
        subtitle = stringResource(R.string.debug_fcm_status_desc),
        onClick = {
            actions.onDebugShowFcmStatus { report ->
                debugStatusDialogText = report
            }
        }
    )

    SettingItem(
        icon = Icons.Default.RestartAlt,
        title = stringResource(R.string.debug_reset_hashes_title),
        subtitle = stringResource(R.string.debug_reset_hashes_desc),
        onClick = {
            actions.onDebugResetHashes()
            Toast.makeText(context, debugActionTriggeredMsg, Toast.LENGTH_SHORT).show()
        }
    )

    SettingItem(
        icon = Icons.Default.BugReport,
        title = stringResource(R.string.debug_simulate_diff_title),
        subtitle = stringResource(R.string.debug_simulate_diff_desc),
        onClick = {
            actions.onDebugSimulateDiff()
        }
    )

    SettingItem(
        icon = Icons.Default.CleaningServices,
        title = stringResource(R.string.debug_reset_onboarding_title),
        subtitle = stringResource(R.string.debug_reset_onboarding_desc),
        onClick = {
            actions.onDebugResetOnboarding()
            Toast.makeText(context, debugActionTriggeredMsg, Toast.LENGTH_SHORT).show()
        }
    )
}
