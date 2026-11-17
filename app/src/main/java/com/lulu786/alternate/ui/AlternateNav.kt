package com.lulu786.alternate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable data object Contacts
@Serializable data object Groups
@Serializable data object Settings

private class Tab(val route: Any, val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab(Contacts, "Contacts", Icons.Filled.People, Icons.Outlined.People),
    Tab(Groups, "Groups", Icons.Filled.Group, Icons.Outlined.Group),
    Tab(Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AlternateNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                tabs.forEach { t ->
                    val selected = entry?.destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(Contacts) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) t.on else t.off, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Contacts, modifier = Modifier.padding(pad)) {
            composable<Contacts> { Placeholder("Contacts") }
            composable<Groups> { Placeholder("Groups") }
            composable<Settings> { Placeholder("Settings") }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(title, style = MaterialTheme.typography.headlineMedium) }
}
