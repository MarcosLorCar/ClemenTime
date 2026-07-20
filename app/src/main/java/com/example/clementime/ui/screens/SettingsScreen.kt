package com.example.clementime.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.example.clementime.ui.theme.ClemenTimeTheme

@Composable
fun SettingsScreen() {
    Box(
        contentAlignment = Alignment.Center
    ) {
        Text("TEST")
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    ClemenTimeTheme {
        SettingsScreen()
    }
}