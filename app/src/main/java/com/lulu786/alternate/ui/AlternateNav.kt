package com.lulu786.alternate.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lulu786.alternate.data.AppSettings
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.GroupPickerSheet
import com.lulu786.alternate.ui.contacts.ContactsScreen
import com.lulu786.alternate.ui.detail.DetailScreen
import com.lulu786.alternate.ui.edit.EditScreen
import com.lulu786.alternate.ui.groups.GroupDetailScreen
import com.lulu786.alternate.ui.groups.GroupsScreen
import com.lulu786.alternate.ui.groups.MemberPickerScreen
import com.lulu786.alternate.ui.lock.PinSetupScreen
import com.lulu786.alternate.ui.settings.SettingsScreen
import com.lulu786.alternate.ui.setup.SetupScreen
import com.lulu786.alternate.util.copyToClipboard
import kotlinx.serialization.Serializable

@Serializable data object Contacts
@Serializable data object Groups
@Serializable data object Settings
@Serializable data class Detail(val id: Long)
@Serializable data class Edit(val id: Long = 0, val name: String? = null)
@Serializable data class GroupDetail(val id: Long)
@Serializable data class MemberPicker(val groupId: Long)
@Serializable data object Setup
@Serializable data object PinSetup

private class Tab(val route: Any, val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab(Contacts, "Contacts", Icons.Filled.People, Icons.Outlined.People),
    Tab(Groups, "Groups", Icons.Filled.Group, Icons.Outlined.Group),
    Tab(Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AlternateNav(vm: AppViewModel, settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it) } }

    // Frozen on first composition: finishing Setup flips settings.setupDone, which must not rebuild the nav graph.
    val start = remember { if (settings.setupDone) Contacts else Setup }
    val dest = entry?.destination
    val onTab = dest != null && tabs.any { t -> dest.hierarchy.any { it.hasRoute(t.route::class) } }
    var groupSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            Scaffold(
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                    when {
                        onTab && vm.selecting && dest?.hasRoute<Contacts>() == true -> {
                            val ids = vm.selected
                            HorizontalFloatingToolbar(expanded = true, modifier = Modifier.padding(bottom = 8.dp), content = {
                                IconButton({ vm.share(context, ids) }) { Icon(Icons.Filled.Share, "Share selected") }
                                IconButton({
                                    context.copyToClipboard("Contacts", vm.rows.value.orEmpty().filter { it.id in ids }.joinToString("\n") { "${it.name} ${it.fullPhoneNumber?.let { n -> "+$n" }.orEmpty()}" })
                                    vm.clearSelection(); vm.say("Copied")
                                }) { Icon(Icons.Filled.ContentCopy, "Copy selected") }
                                IconButton({ groupSheet = true }) { Icon(Icons.Filled.GroupAdd, "Add selected to group") }
                                IconButton({ vm.star(ids, true) }) { Icon(Icons.Filled.Star, "Star selected") }
                                IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete selected") }
                            })
                        }
                        onTab -> ShortNavigationBar {
                            tabs.forEach { t ->
                                val selected = dest.hierarchy.any { it.hasRoute(t.route::class) }
                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = { nav.navigate(t.route) { popUpTo(Contacts) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    icon = { Icon(if (selected) t.on else t.off, null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(
                    nav, startDestination = start, modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
                    enterTransition = { fadeIn() }, exitTransition = { fadeOut() }, popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() },
                ) {
                    composable<Contacts> { Anim { ContactsScreen(vm, { nav.navigate(Detail(it)) }, { nav.navigate(Edit(0, it)) }, { nav.navigate(Setup) }) } }
                    composable<Groups> { Anim { GroupsScreen(vm) { nav.navigate(GroupDetail(it)) } } }
                    composable<Settings> { Anim { SettingsScreen(vm, { nav.navigate(Setup) }, { nav.navigate(PinSetup) }) } }
                    composable<Detail> { e ->
                        val id = e.toRoute<Detail>().id
                        Anim { DetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate(Edit(id)) }, { nav.navigate(GroupDetail(it)) }) }
                    }
                    composable<Edit> { e ->
                        val r = e.toRoute<Edit>()
                        Anim {
                            EditScreen(vm, r.id, r.name, { nav.popBackStack() },
                                { saved -> if (r.id == 0L) nav.navigate(Detail(saved)) { popUpTo<Edit> { inclusive = true } } else nav.popBackStack() },
                                { existing -> nav.navigate(Detail(existing)) })
                        }
                    }
                    composable<GroupDetail> { e ->
                        val id = e.toRoute<GroupDetail>().id
                        Anim { GroupDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate(Detail(it)) }, { nav.navigate(MemberPicker(id)) }) }
                    }
                    composable<MemberPicker> { e -> Anim { MemberPickerScreen(vm, e.toRoute<MemberPicker>().groupId) { nav.popBackStack() } } }
                    composable<Setup> { Anim { SetupScreen(vm) { if (!nav.popBackStack()) nav.navigate(Contacts) { popUpTo(Setup) { inclusive = true } } } } }
                    composable<PinSetup> { Anim { PinSetupScreen(vm) { nav.popBackStack() } } }
                }
            }
        }
    }

    if (groupSheet) GroupPickerSheet(vm, emptySet(), { groupSheet = false }) { ids -> ids.forEach { vm.addToGroup(it, vm.selected) } }
    if (confirmDelete) ConfirmDialog("Delete ${vm.selected.size} contacts?", "They’ll be removed from Alternate.", "Delete", { vm.delete(vm.selected.toList()); confirmDelete = false }, { confirmDelete = false })
}

/** Provides this destination's AnimatedVisibilityScope to [sharedKey]. */
@Composable
private fun androidx.compose.animation.AnimatedContentScope.Anim(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalAnimScope provides this) { content() }
