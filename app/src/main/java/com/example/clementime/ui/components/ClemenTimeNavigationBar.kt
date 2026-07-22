package com.example.clementime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.clementime.R
import com.example.clementime.ui.navigation.ScheduleListRoute
import com.example.clementime.ui.navigation.SettingsRoute
import com.example.clementime.ui.navigation.SubjectsRoute
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.clementime.ui.theme.ClemenTimeTheme
import kotlin.reflect.KClass

private data class NavigationItem<T : Any>(
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: T,
    val routeClass: KClass<T>
)

@Composable
private fun NavigationIcon(
    isSelected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
) {
    AnimatedContent(
        targetState = isSelected,
        label = "iconTransition"
    ) { selected ->
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = null
        )
    }
}

@Composable
fun ClemenTimeNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    onReselect: (KClass<*>) -> Unit = {}
) {
    val items = listOf(
        NavigationItem(
            labelResId = R.string.schedule_screen_title,
            selectedIcon = Icons.Filled.CalendarMonth,
            unselectedIcon = Icons.Outlined.CalendarMonth,
            route = ScheduleListRoute(),
            routeClass = ScheduleListRoute::class
        ),
        NavigationItem(
            labelResId = R.string.subjects_screen_title,
            selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
            unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
            route = SubjectsRoute,
            routeClass = SubjectsRoute::class
        ),
        NavigationItem(
            labelResId = R.string.settings_screen_title,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            route = SettingsRoute,
            routeClass = SettingsRoute::class
        )
    )

    // Dynamically fetch the system navigation bar inset height (e.g. for gesture pill or 3-buttons navigation)
    val systemNavBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    NavigationBar(
        // Set the height to 68.dp (compact content) + the dynamic system bottom inset
        modifier = modifier.height(68.dp + systemNavBarsPadding)
    ) {
        items.forEach { item ->
            val isSelected = currentDestination?.hasRoute(item.routeClass) == true
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else {
                        onReselect(item.routeClass)
                    }
                },
                icon = {
                    NavigationIcon(
                        isSelected = isSelected,
                        selectedIcon = item.selectedIcon,
                        unselectedIcon = item.unselectedIcon
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelResId),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Preview
@Composable
fun ClemenTimeNavigationBarPreview() {
    ClemenTimeTheme {
        ClemenTimeNavigationBar(
            navController = rememberNavController(),
            currentDestination = null
        )
    }
}
