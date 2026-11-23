package com.lulu786.alternate.ui.detail

import com.lulu786.alternate.ui.Sym
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.data.ContactDetail
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.GroupPickerSheet
import com.lulu786.alternate.ui.components.segment
import com.lulu786.alternate.ui.components.segmentedRowColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.ImageVector
import com.lulu786.alternate.ui.sharedKey
import com.lulu786.alternate.util.Countries
import com.lulu786.alternate.util.copyToClipboard
import com.lulu786.alternate.util.dial
import com.lulu786.alternate.util.displayName
import com.lulu786.alternate.util.formatBirthday
import com.lulu786.alternate.util.formatPhone
import com.lulu786.alternate.util.intl
import com.lulu786.alternate.util.mail
import com.lulu786.alternate.util.openWeb
import com.lulu786.alternate.util.sms
import com.lulu786.alternate.util.telegram
import com.lulu786.alternate.util.whatsApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onOpenGroup: (Long) -> Unit) {
    val context = LocalContext.current
    val detail by remember(id) { vm.repo.observeDetail(id) }.collectAsStateWithLifecycle(null)
    val row = vm.rows.collectAsStateWithLifecycle().value?.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var groupSheet by remember { mutableStateOf(false) }
    var photoFull by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val c = detail?.contact
    // Render instantly from the list row; the full record replaces it a frame later.
    val name = c?.let { displayName(it.prefix, it.name, it.suffix) } ?: row?.displayName.orEmpty()
    // Initial for the avatar comes from the bare name, not "Mr Zaid, Khan".
    val bareName = c?.name ?: row?.name.orEmpty()
    val seed = c?.avatarSeed ?: row?.avatarSeed ?: 0
    val photo = c?.photoPath ?: row?.photoPath.orEmpty()
    val starred = c?.starred ?: row?.starred ?: false
    val subtitle = c?.let { listOf(it.appointment, it.location).filter(String::isNotBlank).joinToString(" · ") } ?: row?.subtitle.orEmpty()
    val nickname = c?.nickname.orEmpty()
    val first = detail?.phones?.firstOrNull()

    Column(Modifier.fillMaxSize().sharedKey("contact-$id").background0()) {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onBack) { Icon(Sym.ArrowBack, "Back") } },
            actions = {
                IconButton({ vm.star(listOf(id), !starred) }) {
                    Icon(if (starred) Sym.StarFilled else Sym.Star, if (starred) "Remove from favorites" else "Add to favorites",
                        tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onEdit) { Icon(Sym.Edit, "Edit contact") }
                androidx.compose.foundation.layout.Box {
                    IconButton({ menu = true }) { Icon(Sym.MoreVert, "More options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Share as VCF") }, { menu = false; vm.share(context, listOf(id)) })
                        DropdownMenuItem({ Text("Add to group") }, { menu = false; groupSheet = true })
                        DropdownMenuItem({ Text("Copy all") }, {
                            menu = false
                            context.copyToClipboard("Contact", detail?.let(::summary).orEmpty()); vm.say("Copied")
                        })
                        DropdownMenuItem({ Text("Delete") }, { menu = false; confirmDelete = true })
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactAvatar(seed, bareName, photo, 112.dp, expressive = true, modifier = Modifier.clickable(enabled = photo.isNotEmpty()) { photoFull = true })
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineMediumEmphasized, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (nickname.isNotEmpty()) Text(nickname, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            if (first != null) {
                val number = intl(first.fullPhoneNumber, first.countryCode)
                val messengers = first.countryCode.isNotEmpty()
                QuickActions(listOfNotNull(
                    QuickAction("Call", "Call $name, ${first.label}", { context.dial(number) }) { Icon(Sym.Call, null) },
                    QuickAction("Text", "Text $name", { context.sms(number) }) { Icon(Sym.Sms, null) },
                    if (messengers) QuickAction("WhatsApp", "WhatsApp $name", { context.whatsApp(first.fullPhoneNumber) }) { Icon(Sym.Whatsapp, null, Modifier.size(28.dp), tint = Color.Unspecified) } else null,
                    if (messengers) QuickAction("Telegram", "Telegram $name", { context.telegram(first.fullPhoneNumber) }) { Icon(Sym.Telegram, null, Modifier.size(28.dp), tint = Color.Unspecified) } else null,
                    if (!c?.email.isNullOrEmpty()) QuickAction("Email", "Email $name", { context.mail(c!!.email) }) { Icon(Sym.Mail, null) } else null,
                ))
                Spacer(Modifier.height(24.dp))
            }

            // Groups sit under the actions, like Google Contacts; adding to a group is the editor's job.
            detail?.groups?.takeIf { it.isNotEmpty() }?.let { gs ->
                FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    gs.forEach { g -> AssistChip({ onOpenGroup(g.id) }, { Text(g.name) }, leadingIcon = { Icon(Sym.Label, null, Modifier.size(18.dp)) }) }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (detail != null) {
                val d = detail!!
                val info = buildList {
                    d.phones.forEach { p ->
                        val number = intl(p.fullPhoneNumber, p.countryCode)
                        add(Info(Sym.Call, formatPhone(p.fullPhoneNumber, p.countryCode), listOfNotNull(p.label, Countries.byCode(p.countryCode)?.name).joinToString(" · "),
                            "Call ${d.contact.name}, ${p.label}", { context.dial(number) }, { context.copyToClipboard("Number", number); vm.say("Number copied") },
                            trailing = { IconButton({ context.sms(number) }) { Icon(Sym.Sms, "Text ${p.label} number") } }))
                    }
                    if (d.contact.email.isNotEmpty()) add(Info(Sym.Mail, d.contact.email, "Email", "Email ${d.contact.email}", { context.mail(d.contact.email) }, { context.copyToClipboard("Email", d.contact.email); vm.say("Email copied") }))
                    if (d.contact.location.isNotEmpty()) add(Info(Sym.LocationOn, d.contact.location, "Location", d.contact.location, null, { context.copyToClipboard("Location", d.contact.location); vm.say("Copied") }))
                    formatBirthday(d.contact.birthday)?.let { add(Info(Sym.Cake, it, "Birthday", "Birthday $it", null, null)) }
                    if (d.contact.website.isNotEmpty()) add(Info(Sym.Link, d.contact.website, "Website", "Open ${d.contact.website}", { context.openWeb(d.contact.website) }, { context.copyToClipboard("Website", d.contact.website); vm.say("Link copied") }))
                }
                // Google-Contacts style: every detail is its own segment of one group.
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    info.forEachIndexed { i, it ->
                        val shapes = ListItemDefaults.segmentedShapes(i, info.size)
                        val content: @Composable () -> Unit = { Text(it.title) }
                        val mod = Modifier.semantics { contentDescription = it.description }
                        if (it.onClick == null && it.onLongClick == null) SegmentedListItem(shapes, mod, colors = segmentedRowColors(),
                            leadingContent = { Icon(it.icon, null) }, supportingContent = { Text(it.subtitle) }, content = content)
                        else SegmentedListItem(
                            onClick = it.onClick ?: {}, onLongClick = it.onLongClick, shapes = shapes, modifier = mod, colors = segmentedRowColors(),
                            leadingContent = { Icon(it.icon, null) }, supportingContent = { Text(it.subtitle) }, trailingContent = it.trailing, content = content,
                        )
                    }
                }
                if (d.contact.notes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HeaderedSegment("Notes") {
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            d.contact.notes, Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                stampsLine(d.contact.createdAt, d.contact.updatedAt)?.let {
                    Text(it, Modifier.fillMaxWidth().padding(top = 16.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) ConfirmDialog("Delete contact?", "$name will be removed from Alternate.", "Delete", { confirmDelete = false; vm.delete(listOf(id)); onBack() }, { confirmDelete = false })
    if (groupSheet) GroupPickerSheet(vm, current = detail?.groups?.map { it.id }.orEmpty().toSet(), onDismiss = { groupSheet = false }) { ids ->
        scope.launch {
            val d = detail ?: return@launch
            vm.repo.save(d.contact, d.phones, ids.toList())
        }
    }
    if (photoFull && photo.isNotEmpty()) Dialog({ photoFull = false }) {
        ContactAvatar(seed, bareName, photo, 320.dp, expressive = true, modifier = Modifier.clickable { photoFull = false })
    }
}

private class QuickAction(val label: String, val description: String, val onClick: () -> Unit, val icon: @Composable () -> Unit)

/** Expressive button group: the pressed button widens and squares off while its neighbours make room; overflow goes to a menu. */
@Composable
private fun QuickActions(actions: List<QuickAction>) {
    ButtonGroup(
        overflowIndicator = { menu ->
            FilledTonalIconButton({ menu.show() }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp)) { Icon(Sym.MoreVert, "More actions") }
        },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { a ->
            customItem(
                buttonGroupContent = {
                    val source = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).animateWidth(source), horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(
                            a.onClick, shapes = IconButtonDefaults.shapes(), interactionSource = source,
                            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = a.description },
                        ) { a.icon() }
                        Spacer(Modifier.height(4.dp))
                        Text(a.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                },
                menuContent = { menu -> DropdownMenuItem({ Text(a.label) }, { menu.dismiss(); a.onClick() }, leadingIcon = a.icon) },
            )
        }
    }
}

@Composable private fun Modifier.background0() = this.background(MaterialTheme.colorScheme.surface)

private class Info(
    val icon: ImageVector, val title: String, val subtitle: String, val description: String,
    val onClick: (() -> Unit)?, val onLongClick: (() -> Unit)?, val trailing: (@Composable () -> Unit)? = null,
)

/** A title segment over a content segment, like Google Contacts' "Notes" card. */
@Composable
private fun HeaderedSegment(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(
            title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().segment(0, 2).padding(16.dp),
        )
        Box(Modifier.fillMaxWidth().segment(1, 2)) { content() }
    }
}

private fun summary(d: ContactDetail): String = buildString {
    appendLine(displayName(d.contact.prefix, d.contact.name, d.contact.suffix))
    d.phones.forEach { appendLine("${it.label}: ${intl(it.fullPhoneNumber, it.countryCode)}") }
    if (d.contact.email.isNotEmpty()) appendLine(d.contact.email)
    if (d.contact.location.isNotEmpty()) appendLine(d.contact.location)
}.trim()

/** "Added 3 Nov 2026 · Edited 20 Nov 2026"; a part with an unknown (0) date is left out. */
internal fun stampsLine(created: Long, updated: Long): String? {
    val fmt = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    fun at(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
    return listOfNotNull(
        created.takeIf { it > 0 }?.let { "Added ${at(it)}" },
        updated.takeIf { it > 0 && it != created }?.let { "Edited ${at(it)}" },
    ).joinToString(" · ").ifEmpty { null }
}
