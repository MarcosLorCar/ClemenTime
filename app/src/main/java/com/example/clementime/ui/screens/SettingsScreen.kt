package com.example.clementime.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clementime.R
import com.example.clementime.ui.navigation.ClemenTimeTopBar
import com.example.clementime.ui.theme.ClemenTimeTheme

@Composable
fun SettingsScreen(
    syncPath: String?,
    isCompactMode: Boolean,
    onSelectSyncPath: (Uri) -> Unit,
    onToggleCompactMode: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onMenuClick: () -> Unit
) {
    // Android Framework folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onSelectSyncPath(it) }
    }

    Scaffold(
        topBar = {
            ClemenTimeTopBar(
                title = stringResource(id = R.string.settings_screen_title),
                onMenuClick = onMenuClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Data & Storage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Preference Item 1: Sync Folder Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { folderPickerLauncher.launch(null) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Directory",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = syncPath ?: "No directory selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Preference Item 2: Database Backup
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportBackup
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Backup",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Dump local Room SQLite database to JSON",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Interface",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCompact,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Compact Layout Mode",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isCompactMode,
                        onCheckedChange = onToggleCompactMode
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleSettingCard(icon: ImageVector, name: String) = Card(
    modifier = Modifier.fillMaxWidth()
) {

}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ClemenTimeTheme {
        SettingsScreen(
            syncPath = "a",
            isCompactMode = false,
            onSelectSyncPath = {},
            onToggleCompactMode = {},
            onExportBackup = {  },
            onMenuClick = {  }
        )
    }
}