package com.lulu786.alternate.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.Sym
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.EmptyState
import com.lulu786.alternate.ui.components.highlighted
import com.lulu786.alternate.ui.components.segmentedRowColors
import com.lulu786.alternate.util.formatPhone

/** The Search tab: field focused on arrival (keyboard up), results as a segmented list, keyboard drops on scroll. */
@Composable
fun SearchScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCreate: (String) -> Unit) {
    val text = rememberTextFieldState(vm.query.value)
    LaunchedEffect(Unit) { snapshotFlow { text.text.toString() }.collect { vm.query.value = it } }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // On arrival and on every repeat tap of the Search tab; show() is a no-op when the keyboard is already up.
    LaunchedEffect(vm.searchFocus) { focus.requestFocus(); keyboard?.show() }
    val list = rememberLazyListState()
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) keyboard?.hide() }

    val results by vm.results.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val byId = remember(rows) { rows.orEmpty().associateBy { it.id } }
    val query = text.text.toString()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = SearchBarDefaults.inputFieldShape, color = SearchBarDefaults.colors().containerColor,
        ) {
            SearchBarDefaults.InputField(
                state = text, onSearch = { keyboard?.hide() }, expanded = false, onExpandedChange = {},
                modifier = Modifier.focusRequester(focus),
                placeholder = { Text("Search contacts") },
                leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ text.clearText() }) { Icon(Sym.Close, "Clear") } },
            )
        }
        when {
            query.isBlank() -> EmptyState(Sym.Search, "Search your contacts", "Find anyone by name, number or any detail you saved.")
            results.isEmpty() -> EmptyState(Sym.Search, "No results for “$query”") {
                Button({ onCreate(query) }) { Icon(Sym.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Create contact “$query”") }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(), list,
                // The list runs under the keyboard; this lets its last rows scroll clear of it.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + WindowInsets.ime.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                items(results.size, key = { results[it].id }) { i ->
                    val r = results[i]; val row = byId[r.id] ?: return@items
                    val supporting = when {
                        r.matchedField != null -> buildLabel(r.matchedField, r.snippet, r.snippetRanges)
                        row.subtitle.isNotEmpty() -> AnnotatedString(row.subtitle)
                        else -> row.fullPhoneNumber?.let { AnnotatedString(formatPhone(it, row.countryCode)) } // same fallback as the contacts list
                    }
                    SegmentedListItem(
                        onClick = { onOpen(r.id) }, shapes = ListItemDefaults.segmentedShapes(i, results.size), colors = segmentedRowColors(),
                        leadingContent = { ContactAvatar(row.avatarSeed, row.name, row.photoPath, 40.dp, expressive = true) },
                        supportingContent = supporting?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    ) { Text(highlighted(row.displayName, r.nameRanges), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun buildLabel(field: String, snippet: String, ranges: List<IntRange>): AnnotatedString {
    val prefix = "$field: "
    val shifted = ranges.map { (it.first + prefix.length)..(it.last + prefix.length) }
    return highlighted(prefix + snippet, shifted)
}
