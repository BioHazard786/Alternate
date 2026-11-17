package com.lulu786.alternate.ui.contacts

import com.lulu786.alternate.ui.Sym
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.AppFab
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.ContactListRow
import com.lulu786.alternate.ui.components.EmptyState
import com.lulu786.alternate.ui.components.FastScroller
import com.lulu786.alternate.ui.components.highlighted
import com.lulu786.alternate.ui.sharedKey
import com.lulu786.alternate.util.simCountry
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCreate: (String?) -> Unit, onSetup: () -> Unit) {
    val model by vm.list.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { it?.let { u -> vm.exportTo(context, u) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importFrom(context, u, context.simCountry()) } }

    Box(Modifier.fillMaxSize()) {
        val m = model
        when {
            m == null -> Unit // first Room emission arrives in a frame or two; nothing to show yet
            m.total == 0 -> EmptyContacts(onCreate) { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar(vm, settings?.lock?.enabled == true, onOpen, onCreate, { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, { exportLauncher.launch("contacts.vcf") })
                Box(Modifier.weight(1f)) {
                    ContactList(vm, m, state, onOpen)
                    if (m.total > 30) FastScroller(state, m.letterStarts, Modifier.align(Alignment.CenterEnd), PaddingValues(top = 8.dp, bottom = 88.dp))
                }
            }
        }
        if (!vm.selecting) {
            AppFab(Sym.Add, "Create contact", { onCreate(null) }, Modifier.align(Alignment.BottomEnd).padding(16.dp).sharedKey("create"))
        }
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

@Composable
private fun TopBar(
    vm: AppViewModel, lockOn: Boolean, onOpen: (Long) -> Unit, onCreate: (String?) -> Unit,
    import: () -> Unit, export: () -> Unit,
) {
    if (vm.selecting) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::clearSelection) { Icon(Sym.Close, "Cancel selection") }
            Text("${vm.selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(vm::selectAll) { Icon(Sym.SelectAll, "Select all") }
        }
        return
    }
    val searchState = rememberSearchBarState()
    val text = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { snapshotFlow { text.text.toString() }.collect { vm.query.value = it } }
    var menu by remember { mutableStateOf(false) }

    val input = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text, searchBarState = searchState,
            onSearch = { },
            placeholder = { Text("Search contacts") },
            leadingIcon = {
                if (searchState.targetValue == androidx.compose.material3.SearchBarValue.Expanded)
                    IconButton({ scope.launch { searchState.animateToCollapsed() } }) { Icon(Sym.ArrowBack, "Close search") }
                else Icon(Sym.Search, null)
            },
            trailingIcon = {
                if (searchState.targetValue == androidx.compose.material3.SearchBarValue.Expanded) {
                    if (text.text.isNotEmpty()) IconButton({ text.edit { replace(0, length, "") } }) { Icon(Sym.Close, "Clear") }
                } else Row {
                    if (lockOn) IconButton(vm::lockNow) { Icon(Sym.Lock, "Lock app now") }
                    Box {
                        IconButton({ menu = true }) { Icon(Sym.MoreVert, "More options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Select") }, { menu = false; vm.startSelecting() })
                            DropdownMenuItem({ Text("Import from file") }, { menu = false; import() })
                            DropdownMenuItem({ Text("Export all") }, { menu = false; export() })
                        }
                    }
                }
            },
        )
    }
    SearchBar(searchState, input, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    ExpandedFullScreenSearchBar(searchState, input) {
        SearchResults(vm, text.text.toString(), onOpen = { id -> scope.launch { searchState.animateToCollapsed() }; onOpen(id) }, onCreate = onCreate)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactList(vm: AppViewModel, m: com.lulu786.alternate.ui.ContactListModel, state: LazyListState, onOpen: (Long) -> Unit) {
    val selected = vm.selected
    LazyColumn(
        Modifier.fillMaxSize(), state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "count", contentType = "count") {
            Text("${m.total} contacts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
            stickyHeader(key = "h$letter", contentType = "header") { SectionLabel(letter.toString(), sticky = true) }
            rows(list, "c", vm, selected, onOpen)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rows(
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
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .then(if (sticky) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchResults(vm: AppViewModel, query: String, onOpen: (Long) -> Unit, onCreate: (String?) -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val byId = remember(rows) { rows.orEmpty().associateBy { it.id } }
    if (query.isBlank()) return
    if (results.isEmpty()) {
        EmptyState(Sym.Search, "No results for “$query”") {
            Button({ onCreate(query) }) { Icon(Sym.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Create contact “$query”") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results.size, key = { results[it].id }) { i ->
            val r = results[i]; val row = byId[r.id] ?: return@items
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(row.avatarSeed, row.name, row.photoPath, 40.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(highlighted(row.displayName, r.nameRanges), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.matchedField != null)
                        Text(buildLabel(r.matchedField, r.snippet, r.snippetRanges), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else if (row.subtitle.isNotEmpty())
                        Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun buildLabel(field: String, snippet: String, ranges: List<IntRange>): androidx.compose.ui.text.AnnotatedString {
    val prefix = "$field: "
    val shifted = ranges.map { (it.first + prefix.length)..(it.last + prefix.length) }
    return highlighted(prefix + snippet, shifted)
}

