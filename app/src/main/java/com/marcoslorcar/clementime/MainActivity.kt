package com.marcoslorcar.clementime

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.ui.components.PageInfoFilled
import com.marcoslorcar.clementime.ui.components.PageInfoOutlined
import com.marcoslorcar.clementime.ui.components.ViewWeekFilled
import com.marcoslorcar.clementime.ui.components.ViewWeekOutlined
import com.marcoslorcar.clementime.ui.navigation.AddEditSubjectRoute
import com.marcoslorcar.clementime.ui.navigation.ConflictResolverRoute
import com.marcoslorcar.clementime.ui.navigation.ImportRoute
import com.marcoslorcar.clementime.ui.navigation.MoreRoute
import com.marcoslorcar.clementime.ui.navigation.OnboardingRoute
import com.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.marcoslorcar.clementime.ui.navigation.SubjectsRoute
import com.marcoslorcar.clementime.ui.screens.schedule.ScheduleScreen
import com.marcoslorcar.clementime.ui.screens.settings.MoreScreen
import com.marcoslorcar.clementime.ui.screens.conflictresolver.ConflictResolverScreen
import com.marcoslorcar.clementime.ui.screens.onboarding.OnboardingScreen
import com.marcoslorcar.clementime.ui.screens.scheduleimport.ImportScreen
import com.marcoslorcar.clementime.ui.screens.subject.AddEditSubjectScreen
import com.marcoslorcar.clementime.ui.screens.subject.SubjectsScreen
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.reflect.KClass

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
            val isOnboardingCompleted by settingsRepository.isOnboardingCompletedFlow.collectAsState(initial = null)

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
                    if (isOnboardingCompleted != null) {
                        ClemenTimeApp(isOnboardingCompleted!!)
                    }
                }
            }
        }
    }
}

private data class NavigationSuiteItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeClass: KClass<*>
)

@Composable
fun ClemenTimeApp(isOnboardingCompleted: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isNavVisible = currentDestination?.let { dest ->
        (dest.hasRoute(ScheduleListRoute::class) || 
        dest.hasRoute(SubjectsRoute::class) || 
        dest.hasRoute(MoreRoute::class)) && !dest.hasRoute(OnboardingRoute::class)
    } ?: true

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = if (!isNavVisible) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    }

    val items = listOf(
        NavigationSuiteItem(
            label = stringResource(R.string.schedule_screen_title),
            selectedIcon = ViewWeekFilled,
            unselectedIcon = ViewWeekOutlined,
            routeClass = ScheduleListRoute::class
        ),
        NavigationSuiteItem(
            label = stringResource(R.string.subjects_screen_title),
            selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
            unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
            routeClass = SubjectsRoute::class
        ),
        NavigationSuiteItem(
            label = stringResource(R.string.more_screen_title),
            selectedIcon = PageInfoFilled,
            unselectedIcon = PageInfoOutlined,
            routeClass = MoreRoute::class
        )
    )

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            if (isOnboardingCompleted) {
                items.forEach { item ->
                    val isSelected = currentDestination?.hasRoute(item.routeClass) == true
                    item(
                        selected = isSelected,
                        onClick = {
                            val route = when (item.routeClass) {
                                ScheduleListRoute::class -> ScheduleListRoute()
                                SubjectsRoute::class -> SubjectsRoute
                                MoreRoute::class -> MoreRoute
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
                        icon = {
                            AnimatedContent(
                                targetState = isSelected,
                                label = "navIcon"
                            ) { selected ->
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null
                                )
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = if (isOnboardingCompleted) ScheduleListRoute() else OnboardingRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(ScheduleListRoute()) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    }
                )
            }

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

            composable<MoreRoute> {
                MoreScreen(
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
