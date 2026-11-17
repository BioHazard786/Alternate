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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.R
import com.lulu786.alternate.data.ContactDetail
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.CircleAction
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.GroupPickerSheet
import com.lulu786.alternate.ui.components.SectionCard
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
            Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (nickname.isNotEmpty()) Text(nickname, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            if (first != null) {
                FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Arrangement.spacedBy(12.dp)) {
                    CircleAction("Call", { context.dial(intl(first.fullPhoneNumber, first.countryCode)) }) { Icon(Sym.Call, "Call $name, ${first.label}") }
                    CircleAction("Text", { context.sms(intl(first.fullPhoneNumber, first.countryCode)) }) { Icon(Sym.Sms, "Text $name") }
                    if (first.countryCode.isNotEmpty()) CircleAction("WhatsApp", { context.whatsApp(first.fullPhoneNumber) }) { Icon(Sym.Whatsapp, "WhatsApp $name", Modifier.size(28.dp), tint = Color.Unspecified) }
                    if (first.countryCode.isNotEmpty()) CircleAction("Telegram", { context.telegram(first.fullPhoneNumber) }) { Icon(Sym.Telegram, "Telegram $name", Modifier.size(28.dp), tint = Color.Unspecified) }
                    if (!c?.email.isNullOrEmpty()) CircleAction("Email", { context.mail(c!!.email) }) { Icon(Sym.Mail, "Email $name") }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (detail != null) {
                val d = detail!!
                SectionCard {
                    Text("Contact info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                    d.phones.forEach { p ->
                        val country = Countries.byCode(p.countryCode)
                        InfoRow(
                            icon = { Icon(Sym.Call, null) },
                            title = formatPhone(p.fullPhoneNumber, p.countryCode),
                            subtitle = listOfNotNull(p.label, country?.name).joinToString(" · "),
                            description = "Call ${d.contact.name}, ${p.label}",
                            onClick = { context.dial(intl(p.fullPhoneNumber, p.countryCode)) },
                            onLongClick = { context.copyToClipboard("Number", intl(p.fullPhoneNumber, p.countryCode)); vm.say("Number copied") },
                            trailing = { IconButton({ context.sms(intl(p.fullPhoneNumber, p.countryCode)) }) { Icon(Sym.Sms, "Text ${p.label} number") } },
                        )
                    }
                    if (d.contact.email.isNotEmpty()) InfoRow({ Icon(Sym.Mail, null) }, d.contact.email, null, "Email ${d.contact.email}", { context.mail(d.contact.email) }, { context.copyToClipboard("Email", d.contact.email); vm.say("Email copied") })
                    if (d.contact.website.isNotEmpty()) InfoRow({ Icon(Sym.Link, null) }, d.contact.website, null, "Open ${d.contact.website}", { context.openWeb(d.contact.website) }, { context.copyToClipboard("Website", d.contact.website); vm.say("Link copied") })
                    formatBirthday(d.contact.birthday)?.let { InfoRow({ Icon(Sym.Cake, null) }, it, "Birthday", "Birthday $it", {}, {}) }
                    if (d.contact.location.isNotEmpty()) InfoRow({ Icon(Sym.LocationOn, null) }, d.contact.location, null, d.contact.location, {}, { context.copyToClipboard("Location", d.contact.location); vm.say("Copied") })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text("Groups", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
                        d.groups.forEach { g -> AssistChip({ onOpenGroup(g.id) }, { Text(g.name) }) }
                        AssistChip({ groupSheet = true }, { Text("Add") }, leadingIcon = { Icon(Sym.Add, null, Modifier.size(18.dp)) })
                    }
                }
                if (d.contact.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionCard {
                        Text("Notes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            d.contact.notes, Modifier.clickable { expanded = !expanded }.padding(16.dp, 4.dp, 16.dp, 16.dp),
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
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

@Composable private fun Modifier.background0() = this.background(MaterialTheme.colorScheme.surface)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoRow(
    icon: @Composable () -> Unit, title: String, subtitle: String?, description: String,
    onClick: () -> Unit, onLongClick: () -> Unit, trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

private fun summary(d: ContactDetail): String = buildString {
    appendLine(displayName(d.contact.prefix, d.contact.name, d.contact.suffix))
    d.phones.forEach { appendLine("${it.label}: ${intl(it.fullPhoneNumber, it.countryCode)}") }
    if (d.contact.email.isNotEmpty()) appendLine(d.contact.email)
    if (d.contact.location.isNotEmpty()) appendLine(d.contact.location)
}.trim()
