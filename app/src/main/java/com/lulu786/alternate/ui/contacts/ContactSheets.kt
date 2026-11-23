package com.lulu786.alternate.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.ContactFilter
import com.lulu786.alternate.ui.ContactSort
import com.lulu786.alternate.ui.Sym
import com.lulu786.alternate.ui.components.NameDialog
import com.lulu786.alternate.ui.components.segmentedRowColors
import com.lulu786.alternate.ui.facet
import com.lulu786.alternate.util.Countries

private const val FACET_LIMIT = 24 // the long tail of one-off locations/jobs is search's job, not a chip wall's

/** Google-Contacts-style group (label) sheet: "New group" on top, then every group with its size. */
@Composable
fun GroupsSheet(vm: AppViewModel, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val list = groups.orEmpty()
    ModalBottomSheet(onDismiss) {
        Text("Groups", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val n = list.size + 1
            SegmentedListItem(
                onClick = { creating = true }, shapes = ListItemDefaults.segmentedShapes(0, n), colors = segmentedRowColors(),
                leadingContent = { Icon(Sym.Add, null) },
            ) { Text("New group") }
            list.forEachIndexed { i, g ->
                SegmentedListItem(
                    onClick = { onDismiss(); onOpen(g.id) }, shapes = ListItemDefaults.segmentedShapes(i + 1, n), colors = segmentedRowColors(),
                    leadingContent = { Icon(Sym.Label, null) },
                    trailingContent = { Text("${g.count}", style = MaterialTheme.typography.labelLarge) },
                ) { Text(g.name) }
            }
        }
    }
    if (creating) NameDialog("New group", "", "Create", { creating = false }) { name -> creating = false; vm.createGroup(name) }
}

/** Faceted filter: every facet lists only values that exist in the user's contacts, with counts. */
@Composable
fun FilterSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val f by vm.filter.collectAsStateWithLifecycle()
    val model by vm.list.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val countries = remember(rows) { facet(rows.orEmpty()) { it.countryCode } }
    val locations = remember(rows) { facet(rows.orEmpty()) { it.location }.take(FACET_LIMIT) }
    val work = remember(rows) { facet(rows.orEmpty()) { it.appointment }.take(FACET_LIMIT) }
    fun set(change: ContactFilter.() -> ContactFilter) { vm.filter.value = vm.filter.value.change() }

    ModalBottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                if (f.active) TextButton({ vm.filter.value = ContactFilter() }) { Text("Clear all") }
            }
            Facet("Show") {
                Chip("Favorites", null, f.favorites) { set { copy(favorites = !favorites) } }
                Chip("With photo", null, f.photo == true) { set { copy(photo = if (photo == true) null else true) } }
                Chip("Without photo", null, f.photo == false) { set { copy(photo = if (photo == false) null else false) } }
            }
            groups.orEmpty().takeIf { it.isNotEmpty() }?.let { gs ->
                Facet("Groups") { gs.forEach { g -> Chip(g.name, g.count, g.id in f.groups) { set { copy(groups = this.groups.toggle(g.id)) } } } }
            }
            if (countries.isNotEmpty()) Facet("Country") {
                countries.forEach { (code, n) ->
                    val c = Countries.byCode(code)
                    Chip(c?.let { "${it.flag} ${it.name}" } ?: code, n, code in f.countries) { set { copy(countries = this.countries.toggle(code)) } }
                }
            }
            if (locations.isNotEmpty()) Facet("Location") {
                locations.forEach { (v, n) -> Chip(v, n, v in f.locations) { set { copy(locations = this.locations.toggle(v)) } } }
            }
            if (work.isNotEmpty()) Facet("Work") {
                work.forEach { (v, n) -> Chip(v, n, v in f.work) { set { copy(work = this.work.toggle(v)) } } }
            }
            Spacer(Modifier.height(24.dp))
            val shown = model?.total ?: 0
            Button(onDismiss, Modifier.fillMaxWidth()) { Text(if (shown == 1) "Show 1 contact" else "Show $shown contacts") }
        }
    }
}

/** One radio row per [ContactSort]; picking one applies it and closes the sheet. */
@Composable
fun SortSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val current by vm.sort.collectAsStateWithLifecycle(ContactSort.NameAsc)
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(bottom = 8.dp))
            ContactSort.entries.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().selectable(s == current, onClick = { vm.setSort(s); onDismiss() }, role = Role.RadioButton).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(s == current, null)
                    Text(s.label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

@Composable
private fun Facet(title: String, chips: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
}

@Composable
private fun Chip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected, onClick,
    label = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            if (count != null) Text("  $count", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    },
    leadingIcon = if (selected) { { Icon(Sym.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
)

/** One removable chip per active filter value, shown above the filtered list. */
@Composable
fun ActiveFilters(vm: AppViewModel, f: ContactFilter) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    fun set(next: ContactFilter) { vm.filter.value = next }
    val chips = buildList<Pair<String, () -> Unit>> {
        if (f.favorites) add("Favorites" to { set(f.copy(favorites = false)) })
        f.photo?.let { add((if (it) "With photo" else "Without photo") to { set(f.copy(photo = null)) }) }
        f.groups.forEach { id -> add((groups.orEmpty().firstOrNull { it.id == id }?.name ?: "Group") to { set(f.copy(groups = f.groups - id)) }) }
        f.countries.forEach { code -> add((Countries.byCode(code)?.let { "${it.flag} ${it.name}" } ?: code) to { set(f.copy(countries = f.countries - code)) }) }
        f.locations.forEach { v -> add(v to { set(f.copy(locations = f.locations - v)) }) }
        f.work.forEach { v -> add(v to { set(f.copy(work = f.work - v)) }) }
    }
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { (label, remove) ->
            InputChip(true, remove, { Text(label) }, trailingIcon = { Icon(Sym.Close, "Remove $label filter", Modifier.size(18.dp)) })
        }
        item { TextButton({ set(ContactFilter()) }) { Text("Clear all") } }
    }
}
