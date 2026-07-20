package com.example.clementime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.clementime.ui.navigation.ScheduleListRoute
import com.example.clementime.ui.navigation.SettingsRoute
import com.example.clementime.ui.screens.ScheduleScreen
import com.example.clementime.ui.screens.ScheduleViewModel
import com.example.clementime.ui.screens.SettingsScreen
import com.example.clementime.ui.theme.ClemenTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClemenTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ClemenTimeApp()
                }
            }
        }
    }
}

@Composable
fun ClemenTimeApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                modifier = Modifier.width(260.dp),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close navigation menu"
                        )
                    }

                    Text(
                        text = "ClemenTime",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.padding(8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavigationDrawerItem(
                        badge = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                        label = {
                            Text(stringResource(R.string.schedule_screen_title))
                        },
                        selected = currentDestination?.hasRoute<ScheduleListRoute>() == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentDestination?.hasRoute<ScheduleListRoute>() != true)
                                navController.navigate(ScheduleListRoute)
                        },
                        shape = ShapeDefaults.Medium
                    )

                    NavigationDrawerItem(
                        badge = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = {
                            Text(stringResource(R.string.settings_screen_title))
                        },
                        selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentDestination?.hasRoute<SettingsRoute>() != true)
                                navController.navigate(SettingsRoute)
                        },
                        shape = ShapeDefaults.Medium
                    )
                }
            }
        },
        gesturesEnabled = drawerState.currentValue == DrawerValue.Open || drawerState.isAnimationRunning
    ) {
        NavHost(
            navController = navController,
            startDestination = ScheduleListRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<ScheduleListRoute> {
                val viewModel: ScheduleViewModel = hiltViewModel()
                val items by viewModel.scheduleItems.collectAsState()
                val tab by viewModel.selectedTab.collectAsState()

                ScheduleScreen(
                    items = items,
                    onAddItemClick = {
                        viewModel.addItem(
                            title = "Task ${System.currentTimeMillis()}",
                            description = "Description here",
                            startTime = System.currentTimeMillis(),
                            endTime = System.currentTimeMillis() + 3600000
                        )
                    },
                    onToggle = { viewModel.toggleCompletion(it) },
                    onDelete = { viewModel.deleteItem(it) },
                    selectedTab = tab,
                    onChangeTab = viewModel::changeTab,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    syncPath = "a",
                    isCompactMode = false,
                    onSelectSyncPath = {},
                    onToggleCompactMode = {},
                    onExportBackup = {  },
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}