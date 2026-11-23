package com.lulu786.alternate.ui.contacts

import com.lulu786.alternate.ui.Sym
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.ContactListModel
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.ContactListRow
import com.lulu786.alternate.ui.components.EmptyState
import com.lulu786.alternate.ui.FLAT
import com.lulu786.alternate.ui.components.FastScroller
import com.lulu786.alternate.ui.components.RoundedTitle
import com.lulu786.alternate.ui.sharedKey
import com.lulu786.alternate.util.simCountry

@Composable
fun ContactsScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onOpenGroup: (Long) -> Unit, onCreate: (String?) -> Unit, onRecentCalls: () -> Unit) {
    val model by vm.list.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { it?.let { u -> vm.exportTo(context, u) } }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.exportTo(context, u, csv = true) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importFrom(context, u, context.simCountry()) } }
    val import = { importLauncher.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel")) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        val m = model
        when {
            m == null -> Unit // first Room emission arrives in a frame or two; nothing to show yet
            m.all == 0 -> EmptyContacts(onCreate, import)
            else -> Scaffold(
                Modifier.nestedScroll(scroll.nestedScrollConnection),
                topBar = { TopBar(vm, m, settings?.lock?.enabled == true, scroll, { sheet = it }, import) { csv -> if (csv) exportCsvLauncher.launch("contacts.csv") else exportLauncher.launch("contacts.vcf") } },
                contentWindowInsets = WindowInsets(0, 0, 0, 0), // the tab bar already consumed the nav-bar inset
                containerColor = MaterialTheme.colorScheme.surface,
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
                    ContactList(vm, m, state, onOpen)
                    if (m.total > 30 && m.letterStarts.isNotEmpty()) FastScroller(state, m.letterStarts, Modifier.align(Alignment.CenterEnd), PaddingValues(top = 8.dp, bottom = 88.dp))
                }
            }
        }
        if (!vm.selecting) {
            CreateFabMenu({ onCreate(null) }, onRecentCalls, Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
    }
    when (sheet) {
        "groups" -> GroupsSheet(vm, onOpenGroup) { sheet = null }
        "filter" -> FilterSheet(vm) { sheet = null }
        "sort" -> SortSheet(vm) { sheet = null }
    }
}

/** Expressive FAB menu: new contact, or start from someone who recently called. */
@Composable
private fun CreateFabMenu(onCreate: () -> Unit, onRecentCalls: () -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    if (expanded) Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = false },
    )
    FloatingActionButtonMenu(
        expanded, modifier = modifier,
        button = {
            ToggleFloatingActionButton(expanded, { expanded = it }, Modifier.sharedKey("create")) {
                Icon(
                    if (checkedProgress > 0.5f) Sym.Close else Sym.Add, if (expanded) "Close menu" else "Add contact",
                    Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem({ expanded = false; onRecentCalls() }, { Text("From recent calls") }, { Icon(Sym.Call, null) })
        FloatingActionButtonMenuItem({ expanded = false; onCreate() }, { Text("New contact") }, { Icon(Sym.PersonAdd, null) })
    }
}

@Composable
private fun EmptyContacts(onCreate: (String?) -> Unit, import: () -> Unit) {
    EmptyState(Sym.Person, "No contacts yet", "Contacts you save here show up when they call.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ onCreate(null) }) { Text("Create contact") }
            OutlinedButton(import) { Text("Import from file") }
        }
    }
}

/** Large flexible app bar with the contact count as its subtitle; becomes a selection bar while selecting. */
@Composable
private fun TopBar(
    vm: AppViewModel, m: ContactListModel, lockOn: Boolean, scroll: TopAppBarScrollBehavior,
    openSheet: (String) -> Unit, import: () -> Unit, export: (csv: Boolean) -> Unit,
) {
    if (vm.selecting) {
        TopAppBar(
            title = { Text("${vm.selected.size} selected") },
            navigationIcon = { IconButton(vm::clearSelection) { Icon(Sym.Close, "Cancel selection") } },
            actions = { IconButton(vm::selectAll) { Icon(Sym.SelectAll, "Select all") } },
        )
        return
    }
    var menu by remember { mutableStateOf(false) }
    LargeFlexibleTopAppBar(
        title = { RoundedTitle("Contacts") },
        subtitle = {
            val count = if (m.total == 1) "1 contact" else "${m.total} contacts"
            Text(if (m.filter.active) "$count of ${m.all}" else count)
        },
        scrollBehavior = scroll,
        actions = {
            IconButton({ openSheet("groups") }) { Icon(Sym.Label, "Groups") }
            IconButton({ openSheet("sort") }) { Icon(Sym.Sort, "Sort") }
            // Filled while a filter is on, so a shortened list is never a mystery.
            if (m.filter.active) FilledTonalIconButton({ openSheet("filter") }) { Icon(Sym.FilterList, "Filter (on)") }
            else IconButton({ openSheet("filter") }) { Icon(Sym.FilterList, "Filter") }
            if (lockOn) IconButton(vm::lockNow) { Icon(Sym.Lock, "Lock app now") }
            Box {
                IconButton({ menu = true }) { Icon(Sym.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Select") }, { menu = false; vm.startSelecting() })
                    DropdownMenuItem({ Text("Import from file") }, { menu = false; import() })
                    DropdownMenuItem({ Text("Export as VCF") }, { menu = false; export(false) })
                    DropdownMenuItem({ Text("Export as CSV") }, { menu = false; export(true) })
                }
            }
        },
    )
}

@Composable
private fun ContactList(vm: AppViewModel, m: ContactListModel, state: LazyListState, onOpen: (Long) -> Unit) {
    val selected = vm.selected
    LazyColumn(
        Modifier.fillMaxSize(), state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (m.filter.active) {
            item(key = "filters", contentType = "filters") { ActiveFilters(vm, m.filter) }
            if (m.total == 0) item(key = "nomatch") { EmptyState(Sym.FilterList, "No contacts match these filters") }
        }
        if (m.favorites.isNotEmpty()) {
            item(key = "fav", contentType = "header") { SectionLabel("Favorites") }
            item(key = "favgrid", contentType = "favorites") {
                Column(Modifier.fillMaxWidth()) {
                    m.favorites.chunked(4).forEach { chunk ->
                        Row(Modifier.fillMaxWidth()) {
                            chunk.forEach { r -> FavoriteTile(r, r.id in selected, { if (vm.selecting) vm.toggle(r.id) else onOpen(r.id) }, { vm.toggle(r.id) }, Modifier.weight(1f)) }
                            repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        m.sections.forEach { (letter, list) ->
            if (letter != FLAT) stickyHeader(key = "h$letter", contentType = "header") { SectionLabel(letter.toString(), sticky = true) }
            rows(list, "c", vm, selected, onOpen)
        }
    }
}

private fun LazyListScope.rows(
    list: List<ContactRow>, prefix: String, vm: AppViewModel, selected: Set<Long>, onOpen: (Long) -> Unit,
) {
    items(list.size, key = { "$prefix${list[it].id}" }, contentType = { "row" }) { i ->
        val row = list[i]
        ContactListRow(
            row, selected = row.id in selected, index = i, count = list.size,
            onClick = { if (vm.selecting) vm.toggle(row.id) else onOpen(row.id) },
            onLongClick = { vm.toggle(row.id) },
            modifier = Modifier.animateItem().sharedKey("contact-${row.id}"),
        )
    }
}

/** Avatar (in the contact's own shape) with the first name under it; the Favorites section shows nothing else. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(row: ContactRow, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactAvatar(row.avatarSeed, row.name, row.photoPath, 64.dp, expressive = true, selected = selected)
        Text(row.name.substringBefore(' '), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(text: String, sticky: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .then(if (sticky) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
