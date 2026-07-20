package com.example.clementime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState
            ) {

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
                SettingsScreen()
            }
        }
    }
}