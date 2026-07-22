package com.example.clementime

import android.os.Bundle
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.clementime.ui.components.AppDrawerContent
import com.example.clementime.ui.navigation.AddEditMatterRoute
import com.example.clementime.ui.navigation.ImportRoute
import com.example.clementime.ui.navigation.MattersRoute
import com.example.clementime.ui.navigation.ScheduleListRoute
import com.example.clementime.ui.navigation.SettingsRoute
import com.example.clementime.ui.screens.ScheduleScreen
import com.example.clementime.ui.screens.SettingsScreen
import com.example.clementime.ui.screens.matter.AddEditMatterScreen
import com.example.clementime.ui.screens.matter.MattersScreen
import com.example.clementime.ui.screens.scheduleimport.ImportScreen
import com.example.clementime.ui.theme.ClemenTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.example.clementime.data.SettingsRepository
import javax.inject.Inject
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import java.util.Locale

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
            startDestination = ScheduleListRoute(),
            modifier = Modifier.fillMaxSize()
        ) {
            composable<ScheduleListRoute> {
                ScheduleScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onClickMatter = { matterId, slotId ->
                        navController.navigate(AddEditMatterRoute(matterId, slotId))
                    },
                )
            }

            composable<ImportRoute> {
                ImportScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateBack = {
                        navController.popBackStack<ScheduleListRoute>(inclusive = false)
                    }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateToImport = {
                        navController.navigate(ImportRoute)
                    }
                )
            }

            composable<MattersRoute> {
                MattersScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateToAddEditMatter = { matterId ->
                        navController.navigate(AddEditMatterRoute(matterId))
                    },
                    onNavigateToSchedule = { dayOfWeek ->
                        navController.navigate(ScheduleListRoute(dayOfWeek = dayOfWeek.name))
                    }
                )
            }

            composable<AddEditMatterRoute> {
                AddEditMatterScreen(
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
