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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import com.marcoslorcar.clementime.ui.navigation.ScheduleListRoute
import com.marcoslorcar.clementime.ui.navigation.SubjectsRoute
import com.marcoslorcar.clementime.ui.screens.conflictresolver.ConflictResolverScreen
import com.marcoslorcar.clementime.ui.screens.onboarding.OnboardingScreen
import com.marcoslorcar.clementime.ui.screens.schedule.ScheduleScreen
import com.marcoslorcar.clementime.ui.screens.schedule.UpdateSubjectsBottomSheet
import com.marcoslorcar.clementime.ui.screens.scheduleimport.ImportScreen
import com.marcoslorcar.clementime.ui.screens.settings.MoreScreen
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
            val hasPendingUpdate by settingsRepository.hasPendingScheduleUpdateFlow.collectAsState(initial = false)

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
                            ClemenTimeApp(isOnboardingCompleted!!, hasPendingUpdate)
                        }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClemenTimeApp(isOnboardingCompleted: Boolean, hasPendingUpdate: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var showUpdateSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(hasPendingUpdate) {
        showUpdateSheet = hasPendingUpdate
    }

    if (showUpdateSheet) {
        UpdateSubjectsBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showUpdateSheet = false }
        )
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
                    fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 3 }
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

            composable<MoreRoute> {
                MoreScreen(
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
                    onNavigateToSchedule = { dayOfWeek, slotId ->
                        navigateToTab(ScheduleListRoute(dayOfWeek = dayOfWeek.name, highlightSlotId = slotId))
                    },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<AddEditSubjectRoute> {
                AddEditSubjectScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSchedule = { dayOfWeek: java.time.DayOfWeek, slotId: Long? ->
                        navigateToTab(ScheduleListRoute(dayOfWeek = dayOfWeek.name, highlightSlotId = slotId))
                    }
                )
            }
        }
    }
}
