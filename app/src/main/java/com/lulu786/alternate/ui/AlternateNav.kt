package com.lulu786.alternate.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.lulu786.alternate.ui.calllog.RecentCallsScreen
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
import com.lulu786.alternate.util.intl
import kotlinx.serialization.Serializable

@Serializable data object Contacts
@Serializable data object Groups
@Serializable data object Settings
@Serializable data class Detail(val id: Long)
@Serializable data class Edit(val id: Long = 0, val name: String? = null, val number: String? = null)
@Serializable data object RecentCalls
@Serializable data class GroupDetail(val id: Long)
@Serializable data class MemberPicker(val groupId: Long)
@Serializable data object Setup
@Serializable data object PinSetup

private class Tab(val route: Any, val label: String, val icon: @Composable (selected: Boolean) -> ImageVector)

private val tabs = listOf(
    Tab(Contacts, "Contacts") { if (it) Sym.PersonFilled else Sym.Person },
    Tab(Groups, "Groups") { if (it) Sym.GroupFilled else Sym.Group },
    Tab(Settings, "Settings") { if (it) Sym.SettingsFilled else Sym.Settings },
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
                            // Full width, same surface as the navigation bar it replaces.
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                                    val any = ids.isNotEmpty()
                                    IconButton({ vm.share(context, ids) }, enabled = any) { Icon(Sym.Share, "Share selected") }
                                    IconButton({
                                        context.copyToClipboard("Contacts", vm.rows.value.orEmpty().filter { it.id in ids }.joinToString("\n") { "${it.name} ${it.fullPhoneNumber?.let { n -> intl(n, it.countryCode) }.orEmpty()}" })
                                        vm.clearSelection(); vm.say("Copied")
                                    }, enabled = any) { Icon(Sym.ContentCopy, "Copy selected") }
                                    IconButton({ groupSheet = true }, enabled = any) { Icon(Sym.GroupAdd, "Add selected to group") }
                                    IconButton({ vm.star(ids, true) }, enabled = any) { Icon(Sym.Star, "Star selected") }
                                    IconButton({ confirmDelete = true }, enabled = any) { Icon(Sym.Delete, "Delete selected") }
                                }
                            }
                        }
                        onTab -> ShortNavigationBar {
                            tabs.forEach { t ->
                                val selected = dest.hierarchy.any { it.hasRoute(t.route::class) }
                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = { nav.navigate(t.route) { popUpTo(Contacts) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                    icon = { Icon(t.icon(selected), null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(
                    nav, startDestination = start, modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
                    enterTransition = { fadeIn(tween(200)) }, exitTransition = { fadeOut(tween(120)) }, popEnterTransition = { fadeIn(tween(200)) }, popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable<Contacts> { Anim { ContactsScreen(vm, { nav.navigate(Detail(it)) }, { nav.navigate(Edit(0, it)) }, { nav.navigate(RecentCalls) }, { nav.navigate(Setup) }) } }
                    composable<Groups> { Anim { GroupsScreen(vm) { nav.navigate(GroupDetail(it)) } } }
                    composable<Settings> { Anim { SettingsScreen(vm, { nav.navigate(Setup) }, { nav.navigate(PinSetup) }) } }
                    composable<Detail> { e ->
                        val id = e.toRoute<Detail>().id
                        Anim { DetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate(Edit(id)) }, { nav.navigate(GroupDetail(it)) }) }
                    }
                    composable<Edit> { e ->
                        val r = e.toRoute<Edit>()
                        Anim {
                            EditScreen(vm, r.id, r.name, r.number, { nav.popBackStack() },
                                { saved -> if (r.id == 0L) nav.navigate(Detail(saved)) { popUpTo<Edit> { inclusive = true } } else nav.popBackStack() },
                                { existing -> nav.navigate(Detail(existing)) })
                        }
                    }
                    composable<RecentCalls> {
                        Anim {
                            RecentCallsScreen({ nav.popBackStack() }) { number, name ->
                                nav.navigate(Edit(0, name.ifEmpty { null }, number)) { popUpTo<RecentCalls> { inclusive = true } }
                            }
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

    // After NavHost so it wins over the nav back handler (e.g. selecting inside a group).
    BackHandler(enabled = vm.selecting) { vm.clearSelection() }

    if (groupSheet) GroupPickerSheet(vm, emptySet(), { groupSheet = false }) { ids -> ids.forEach { vm.addToGroup(it, vm.selected) } }
    if (confirmDelete) ConfirmDialog("Delete ${vm.selected.size} contacts?", "They’ll be removed from Alternate.", "Delete", { vm.delete(vm.selected.toList()); confirmDelete = false }, { confirmDelete = false })
}

/** Provides this destination's AnimatedVisibilityScope to [sharedKey]. */
@Composable
private fun androidx.compose.animation.AnimatedContentScope.Anim(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalAnimScope provides this) { content() }
