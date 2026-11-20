package com.lulu786.alternate.ui.components

import com.lulu786.alternate.ui.Sym
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.util.Countries
import com.lulu786.alternate.util.Country
import com.lulu786.alternate.util.formatPhone

/** Applies the Google-Contacts grouped look to one row of a list: rounded outer corners, 4dp inner corners. */
@Composable
fun Modifier.segment(index: Int, count: Int, color: Color = MaterialTheme.colorScheme.surfaceContainer): Modifier {
    val shape = remember(index, count) { segmentedShape(index, count) }
    return this.clip(shape).background(color)
}

/** Non-list card made of one rounded group. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(segmentedShape(0, 1)).background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

/** M3 1.5 expressive segmented list item (the one on m3.material.io); it morphs its corners when pressed or selected. */
@Composable
fun ContactListRow(
    row: ContactRow,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
) {
    val subtitle = remember(row.subtitle, row.fullPhoneNumber) {
        row.subtitle.ifEmpty { row.fullPhoneNumber?.let { formatPhone(it, row.countryCode) }.orEmpty() }
    }
    SegmentedListItem(
        selected = selected, onClick = onClick, onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedRowColors(),
        modifier = modifier,
        leadingContent = { ContactAvatar(row.avatarSeed, row.name, row.photoPath, 40.dp, expressive = true, selected = selected) },
        supportingContent = if (subtitle.isEmpty()) null else { { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    ) { Text(row.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** App-bar title in the rounded face; LocalTextStyle keeps the bar's own expanded and collapsed sizes. */
@Composable
fun RoundedTitle(text: String) = Text(
    text, style = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = com.lulu786.alternate.ui.theme.RoundedFontFamily),
    maxLines = 1, overflow = TextOverflow.Ellipsis,
)

/** M3 defaults to surface items on a tinted page; this app's pages are surface, so the items take the tint. */
@Composable
fun segmentedRowColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
)

/** Display-only expressive switch (the row handles the click) with a check / cross on the thumb. */
@Composable
fun IconSwitch(checked: Boolean) = androidx.compose.material3.Switch(
    checked, null,
    thumbContent = { Icon(if (checked) Sym.Check else Sym.Close, null, Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)) },
)

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, actions: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

/** Builds a string with [ranges] in bold primary, for search results. */
@Composable
fun highlighted(text: String, ranges: List<IntRange>): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var at = 0
        for (r in ranges.sortedBy { it.first }) {
            if (r.first < at || r.last >= text.length) continue
            append(text.substring(at, r.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(text.substring(r.first, r.last + 1)) }
            at = r.last + 1
        }
        append(text.substring(at))
    }
}

/** Country chip sheet: search field pinned on top, SIM country first, then A-Z. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(current: Country, simCode: String?, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query, simCode) {
        val q = query.trim().lowercase()
        val filtered = Countries.all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.dialCode.contains(q.removePrefix("+")) || it.code.lowercase() == q }
        val sim = filtered.firstOrNull { it.code == simCode }
        val none = Countries.NONE.takeIf { q.isEmpty() || it.name.lowercase().contains(q) || "raw".startsWith(q) }
        listOfNotNull(none) + if (sim != null && q.isEmpty()) listOf(sim) + (filtered - sim) else filtered
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search country or code") }, leadingIcon = { Icon(Sym.Search, null) }, singleLine = true,
        )
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(list, key = { it.key }) { c ->
                val selected = c == current
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(c) }
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.flag, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(16.dp))
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (c.dialCode.isNotEmpty()) Text("+${c.dialCode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** The one FAB every screen uses, so size, colour and elevation match everywhere. */
@Composable
fun AppFab(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FloatingActionButton(
        onClick, modifier,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 3.dp, focusedElevation = 3.dp, hoveredElevation = 4.dp),
    ) { Icon(icon, label) }
}
