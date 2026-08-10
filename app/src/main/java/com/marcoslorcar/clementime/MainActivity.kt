package com.marcoslorcar.clementime

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import com.marcoslorcar.clementime.ui.navigation.ScheduleFocus
import com.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.marcoslorcar.clementime.ui.navigation.SubjectsRoute
import com.marcoslorcar.clementime.ui.screens.conflictresolver.ConflictResolverScreen
import com.marcoslorcar.clementime.ui.screens.onboarding.OnboardingScreen
import com.marcoslorcar.clementime.ui.screens.schedule.ScheduleScreen
import com.marcoslorcar.clementime.ui.screens.scheduleimport.ImportScreen
import com.marcoslorcar.clementime.ui.screens.settings.MoreScreen
import com.marcoslorcar.clementime.ui.screens.subject.AddEditSubjectScreen
import com.marcoslorcar.clementime.ui.screens.subject.SubjectsScreen
import com.marcoslorcar.clementime.ui.theme.ClemenTimeTheme
import com.marcoslorcar.clementime.worker.ScheduleUpdateWorker
import com.marcoslorcar.clementime.worker.ScheduleUpdateWorker.Companion.EXTRA_SHOW_SCHEDULE_DIFF
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        // Re-arm background sync. Onboarding and Settings enqueue it when the interval
        // changes, but neither runs for a user who onboarded before this feature existed,
        // and WorkManager state does not survive "clear data" or some restores.
        lifecycleScope.launch {
            // Must precede the read below: it may write the interval this install relies on.
            settingsRepository.migrateLegacyAutoUpdateDefault()
            val intervalMinutes = settingsRepository.autoUpdateIntervalMinutesFlow.first()
            ScheduleUpdateWorker.ensurePeriodicWorkScheduled(this@MainActivity, intervalMinutes)
        }

        // Read once: the extra lives on the Activity's intent for its whole lifetime, so
        // re-reading it after a config change would reopen the sheet the user dismissed.
        val showScheduleDiff = intent?.getBooleanExtra(EXTRA_SHOW_SCHEDULE_DIFF, false) == true
        intent?.removeExtra(EXTRA_SHOW_SCHEDULE_DIFF)

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
                        key(isOnboardingCompleted) {
                            ClemenTimeApp(
                                isOnboardingCompleted = isOnboardingCompleted!!,
                                showScheduleDiffOnLaunch = showScheduleDiff
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils.updateWidget(this)
    }
}

private data class NavigationSuiteItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeClass: KClass<*>
)

private val TabRoutes = listOf(
    ScheduleListRoute::class,
    SubjectsRoute::class,
    MoreRoute::class
)

private fun getTabIndex(entry: NavBackStackEntry?): Int {
    return entry?.destination?.let { dest ->
        TabRoutes.indexOfFirst { dest.hasRoute(it) }
    } ?: -1
}

@Composable
fun ClemenTimeApp(
    isOnboardingCompleted: Boolean,
    showScheduleDiffOnLaunch: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    androidx.compose.runtime.LaunchedEffect(showScheduleDiffOnLaunch) {
        if (showScheduleDiffOnLaunch) {
            navController.navigate(MoreRoute(showDiff = true))
        }
    }

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
            selectedIcon = Icons.Filled.School,
            unselectedIcon = Icons.Outlined.School,
            routeClass = SubjectsRoute::class
        ),
        NavigationSuiteItem(
            label = stringResource(R.string.more_screen_title),
            selectedIcon = PageInfoFilled,
            unselectedIcon = PageInfoOutlined,
            routeClass = MoreRoute::class
        )
    )

    val navigateToTab = { route: Any ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    // Held here, above the NavHost, rather than passed as route arguments - see ScheduleFocus.
    var pendingScheduleFocus by remember { mutableStateOf<ScheduleFocus?>(null) }

    val focusScheduleSlot = { day: java.time.DayOfWeek, slotId: Long? ->
        pendingScheduleFocus = ScheduleFocus(dayOfWeek = day, slotId = slotId)
        navigateToTab(ScheduleListRoute())
    }

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
                                MoreRoute::class -> MoreRoute()
                                else -> return@item
                            }
                            navigateToTab(route)
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = null
                            )
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
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val fromIndex = getTabIndex(initialState)
                val toIndex = getTabIndex(targetState)
                
                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    if (toIndex > fromIndex) {
                        fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 }
                    } else {
                        fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 3 }
                    }
                } else {
                    fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 }
                }
            },
            exitTransition = {
                val fromIndex = getTabIndex(initialState)
                val toIndex = getTabIndex(targetState)
                
                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    if (toIndex > fromIndex) {
                        fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 3 }
                    } else {
                        fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 3 }
                    }
                } else {
                    fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 3 }
                }
            },
            popEnterTransition = {
                val fromIndex = getTabIndex(initialState)
                val toIndex = getTabIndex(targetState)
                
                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    if (toIndex > fromIndex) {
                        fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 }
                    } else {
                        fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 3 }
                    }
                } else {
                    fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 3 }
                }
            },
            popExitTransition = {
                val fromIndex = getTabIndex(initialState)
                val toIndex = getTabIndex(targetState)
                
                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    if (toIndex > fromIndex) {
                        fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 3 }
                    } else {
                        fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 3 }
                    }
                } else {
                    fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 3 }
                }
            }
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

            composable<ScheduleListRoute> {
                ScheduleScreen(
                    focus = pendingScheduleFocus,
                    onFocusConsumed = { pendingScheduleFocus = null },
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
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ImportRoute> {
                ImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onImportSuccess = {
                        navigateToTab(SubjectsRoute)
                    }
                )
            }

            composable<MoreRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<MoreRoute>()
                MoreScreen(
                    showDiffOnLaunch = route.showDiff,
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<SubjectsRoute> {
                SubjectsScreen(
                    onNavigateToSubject = { subjectId: Long? ->
                        navController.navigate(AddEditSubjectRoute(subjectId))
                    },
                    onNavigateToSchedule = focusScheduleSlot,
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<AddEditSubjectRoute> {
                AddEditSubjectScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSchedule = focusScheduleSlot
                )
            }
        }
    }
}
