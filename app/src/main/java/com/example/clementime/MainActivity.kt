package com.example.clementime

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.clementime.data.SettingsRepository
import com.example.clementime.ui.navigation.AddEditSubjectRoute
import com.example.clementime.ui.navigation.ImportRoute
import com.example.clementime.ui.navigation.ScheduleListRoute
import com.example.clementime.ui.navigation.SettingsRoute
import com.example.clementime.ui.navigation.SubjectsRoute
import com.example.clementime.ui.screens.ScheduleScreen
import com.example.clementime.ui.screens.SettingsScreen
import com.example.clementime.ui.screens.scheduleimport.ImportScreen
import com.example.clementime.ui.screens.subject.AddEditSubjectScreen
import com.example.clementime.ui.screens.subject.SubjectsScreen
import com.example.clementime.ui.theme.ClemenTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeFlow.collectAsState(initial = "system")
            val appLanguage by settingsRepository.languageFlow.collectAsState(initial = "en")

            LaunchedEffect(appLanguage) {
                val locale = Locale.forLanguageTag(appLanguage)
                Locale.setDefault(locale)
                
                val config = resources.configuration
                config.setLocale(locale)
                resources.updateConfiguration(config, resources.displayMetrics)

                val appContext = applicationContext
                val appConfig = appContext.resources.configuration
                appConfig.setLocale(locale)
                appContext.resources.updateConfiguration(appConfig, appContext.resources.displayMetrics)
            }

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            LocaleWrapper(localeCode = appLanguage) {
                ClemenTimeTheme(darkTheme = darkTheme) {
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
}

class LocaleContextWrapper(base: Context, private val configContext: Context) : ContextWrapper(base) {
    override fun getResources(): Resources = configContext.resources
    override fun getAssets(): AssetManager = configContext.assets
}

@Composable
fun LocaleWrapper(localeCode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val localeContext = remember(localeCode, currentConfiguration) {
        val locale = Locale.forLanguageTag(localeCode)
        Locale.setDefault(locale)
        val config = Configuration(currentConfiguration)
        config.setLocale(locale)
        val configContext = context.createConfigurationContext(config)
        LocaleContextWrapper(context, configContext)
    }

    CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalConfiguration provides localeContext.resources.configuration,
        content = content
    )
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
            composable<ScheduleListRoute> {
                ScheduleScreen(
                    onClickSubject = { subjectId, slotId ->
                        navController.navigate(AddEditSubjectRoute(subjectId, slotId))
                    },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<ImportRoute> {
                ImportScreen(
                    onNavigateBack = {
                        navController.popBackStack<ScheduleListRoute>(inclusive = false)
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
                    onNavigateToSchedule = { dayOfWeek ->
                        navController.navigate(ScheduleListRoute(dayOfWeek = dayOfWeek.name))
                    },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<AddEditSubjectRoute> {
                AddEditSubjectScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSchedule = { dayOfWeek ->
                        navController.navigate(ScheduleListRoute(dayOfWeek = dayOfWeek.name)) {
                            popUpTo<ScheduleListRoute> { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
