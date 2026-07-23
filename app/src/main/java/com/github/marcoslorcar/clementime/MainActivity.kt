package com.github.marcoslorcar.clementime

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.marcoslorcar.clementime.data.SettingsRepository
import com.github.marcoslorcar.clementime.ui.navigation.AddEditSubjectRoute
import com.github.marcoslorcar.clementime.ui.navigation.ConflictResolverRoute
import com.github.marcoslorcar.clementime.ui.navigation.ImportRoute
import com.github.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.github.marcoslorcar.clementime.ui.navigation.SettingsRoute
import com.github.marcoslorcar.clementime.ui.navigation.SubjectsRoute
import com.github.marcoslorcar.clementime.ui.screens.schedule.ScheduleScreen
import com.github.marcoslorcar.clementime.ui.screens.settings.SettingsScreen
import com.github.marcoslorcar.clementime.ui.screens.conflictresolver.ConflictResolverScreen
import com.github.marcoslorcar.clementime.ui.screens.scheduleimport.ImportScreen
import com.github.marcoslorcar.clementime.ui.screens.subject.AddEditSubjectScreen
import com.github.marcoslorcar.clementime.ui.screens.subject.SubjectsScreen
import com.github.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeFlow.collectAsState(initial = "system")
            val selectedTheme by settingsRepository.selectedThemeFlow.collectAsState(initial = "clementine")

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            ClemenTimeTheme(
                darkTheme = darkTheme,
                selectedTheme = selectedTheme
            ) {
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

    val isNavVisible = currentDestination?.let { dest ->
        dest.hasRoute(ScheduleListRoute::class) || 
        dest.hasRoute(SubjectsRoute::class) || 
        dest.hasRoute(SettingsRoute::class)
    } ?: true

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = if (!isNavVisible) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    }

    val items = listOf(
        Triple(stringResource(R.string.schedule_screen_title), Icons.Default.CalendarMonth, ScheduleListRoute::class),
        Triple(stringResource(R.string.subjects_screen_title), Icons.Default.School, SubjectsRoute::class),
        Triple(stringResource(R.string.settings_screen_title), Icons.Default.Settings, SettingsRoute::class)
    )

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            items.forEach { (label, icon, routeClass) ->
                item(
                    selected = currentDestination?.hasRoute(routeClass) == true,
                    onClick = {
                        val route = when(routeClass) {
                            ScheduleListRoute::class -> ScheduleListRoute()
                            SubjectsRoute::class -> SubjectsRoute
                            SettingsRoute::class -> SettingsRoute
                            else -> return@item
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label) }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = ScheduleListRoute(),
            modifier = Modifier.fillMaxSize()
        ) {
            composable<ScheduleListRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ScheduleListRoute>()
                ScheduleScreen(
                    targetDayOfWeek = route.dayOfWeek,
                    targetHighlightSlotId = route.highlightSlotId,
                    onClickSubject = { subjectId, slotId ->
                        navController.navigate(AddEditSubjectRoute(subjectId, slotId))
                    },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    },
                    onNavigateToConflictResolver = {
                        navController.navigate(ConflictResolverRoute)
                    }
                )
            }

            composable<ConflictResolverRoute> {
                ConflictResolverScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<ImportRoute> {
                ImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<SubjectsRoute> {
                SubjectsScreen(
                    onNavigateToAddEditSubject = { subjectId ->
                        navController.navigate(AddEditSubjectRoute(subjectId))
                    },
                    onNavigateToSchedule = { dayOfWeek, slotId ->
                        navController.navigate(ScheduleListRoute(dayOfWeek = dayOfWeek.name, highlightSlotId = slotId)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = false
                        }
                    },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<AddEditSubjectRoute> {
                AddEditSubjectScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSchedule = { dayOfWeek, slotId ->
                        navController.navigate(ScheduleListRoute(dayOfWeek = dayOfWeek.name, highlightSlotId = slotId)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = false
                        }
                    }
                )
            }
        }
    }
}
