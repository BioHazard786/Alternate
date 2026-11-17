package com.lulu786.alternate.ui.components

import com.lulu786.alternate.ui.Sym
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalFoundationApi::class)
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val subtitle = remember(row.subtitle, row.fullPhoneNumber) {
        row.subtitle.ifEmpty { row.fullPhoneNumber?.let { formatPhone(it, row.countryCode) }.orEmpty() }
    }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .segment(index, count, if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${row.displayName}, $subtitle" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(row.avatarSeed, row.name, row.photoPath, 40.dp, selected = selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty())
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Circular tonal action with a label under it; bounces when pressed. */
@Composable
fun CircleAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "bounce")
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick, Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }, interactionSource = source) {
            icon()
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

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
        if (sim != null && q.isEmpty()) listOf(sim) + (filtered - sim) else filtered
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search country or code") }, leadingIcon = { Icon(Sym.Search, null) }, singleLine = true,
        )
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(list, key = { it.code }) { c ->
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
                    Text("+${c.dialCode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
