package com.example.clementime.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.clementime.R
import com.example.clementime.ui.navigation.MattersRoute
import com.example.clementime.ui.navigation.ScheduleListRoute
import com.example.clementime.ui.navigation.SettingsRoute
import com.example.clementime.ui.theme.ClemenTimeTheme
import kotlin.reflect.KClass

private data class NavigationItem<T : Any>(
    val labelResId: Int,
    val icon: ImageVector,
    val route: T,
    val routeClass: KClass<T>
)

@Composable
fun AppDrawerContent(
    isRouteSelected: (KClass<*>) -> Boolean,
    onNavigate: (Any) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationItem(
            labelResId = R.string.schedule_screen_title,
            icon = Icons.Filled.Schedule,
            route = ScheduleListRoute(),
            routeClass = ScheduleListRoute::class
        ),
        NavigationItem(
            labelResId = R.string.matters_screen_title,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            route = MattersRoute,
            routeClass = MattersRoute::class
        ),
        NavigationItem(
            labelResId = R.string.settings_screen_title,
            icon = Icons.Filled.Settings,
            route = SettingsRoute,
            routeClass = SettingsRoute::class
        )
    )

    ModalDrawerSheet(
        modifier = modifier.width(260.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCloseDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Close Drawer")
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .padding(12.dp)
        ) {
            items.forEach { item ->
                val isSelected = isRouteSelected(item.routeClass)
                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.labelResId)) },
                    selected = isSelected,
                    onClick = {
                        onCloseDrawer()
                        if (!isSelected) {
                            onNavigate(item.route)
                        }
                    },
                    shape = ShapeDefaults.Medium
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppDrawerPreview() {
    ClemenTimeTheme {
        AppDrawerContent(
            isRouteSelected = { it == ScheduleListRoute::class },
            onNavigate = {},
            onCloseDrawer = {}
        )
    }
}
