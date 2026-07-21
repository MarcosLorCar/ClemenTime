package com.example.clementime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.clementime.ui.components.AppDrawerContent
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

@Preview(showBackground = true)
@Composable
fun DrawerExpandedPreview() {
    ClemenTimeTheme {
        val drawerState = rememberDrawerState(DrawerValue.Open)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    isRouteSelected = { it == ScheduleListRoute::class },
                    onNavigate = {},
                    onCloseDrawer = {}
                )
            }
        ) {
            Scaffold { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Main Content Area")
                }
            }
        }
    }
}

@Composable
fun ClemenTimeApp(
    initialDrawerValue: DrawerValue = DrawerValue.Closed
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val drawerState = rememberDrawerState(initialDrawerValue)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                isRouteSelected = { routeClass ->
                    currentDestination?.hasRoute(routeClass) == true
                },
                onNavigate = { route ->
                    navController.navigate(route)
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
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
                    syncPath = "",
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
