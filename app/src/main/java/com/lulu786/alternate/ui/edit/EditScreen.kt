package com.lulu786.alternate.ui.edit

import com.lulu786.alternate.ui.Sym
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.LocalContainer
import com.lulu786.alternate.data.ContactEntity
import com.lulu786.alternate.data.PhoneEntity
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.ContactAvatar
import com.lulu786.alternate.ui.components.CountryPickerSheet
import com.lulu786.alternate.ui.components.GroupPickerSheet
import com.lulu786.alternate.ui.sharedKey
import com.lulu786.alternate.util.Countries
import com.lulu786.alternate.util.Country
import com.lulu786.alternate.util.simCountry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

private class PhoneForm(val key: Long, country: Country, number: String = "", label: String = "Mobile") {
    var country by mutableStateOf(country)
    var number by mutableStateOf(number)
    var label by mutableStateOf(label)
}

private fun countryOf(code: String) = Countries.byCode(code) ?: Countries.byCode("IN")!!

private val builtInLabels = listOf("Mobile", "Work", "Home", "Main", "Other")
private val extraFields = listOf("Prefix", "Suffix", "Nickname", "Email", "Website", "Birthday", "Notes")

/** Create (id = 0) and edit in one screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(vm: AppViewModel, id: Long, prefillName: String?, onClose: () -> Unit, onSaved: (Long) -> Unit, onViewExisting: (Long) -> Unit) {
    val context = LocalContext.current
    val photos = LocalContainer.current.photos
    val scope = rememberCoroutineScope()
    val sim = remember { context.simCountry() ?: "IN" }
    // The seed behind the avatar preview; saved as-is so what you see is what you get, and survives rotation.
    val newSeed = rememberSaveable { Random.nextInt(Int.MAX_VALUE) }

    var loaded by remember { mutableStateOf(id == 0L) }
    var seed by remember { mutableStateOf(newSeed) }
    var original by remember { mutableStateOf<ContactEntity?>(null) }
    var name by remember { mutableStateOf(prefillName.orEmpty()) }
    var appointment by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val extras = remember { mutableStateMapOfStrings() }
    val visibleExtras = remember { mutableStateListOf<String>() }
    val phones = remember { mutableStateListOf(PhoneForm(0, countryOf(sim))) }
    var groupIds by remember { mutableStateOf(emptySet<Long>()) }
    var photo by remember { mutableStateOf("") }
    var origPhoto by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            vm.repo.detail(id)?.let { d ->
                original = d.contact; seed = d.contact.avatarSeed
                name = d.contact.name; appointment = d.contact.appointment; location = d.contact.location
                mapOf("Prefix" to d.contact.prefix, "Suffix" to d.contact.suffix, "Nickname" to d.contact.nickname, "Email" to d.contact.email,
                    "Website" to d.contact.website, "Birthday" to d.contact.birthday, "Notes" to d.contact.notes)
                    .forEach { (k, v) -> extras[k] = v; if (v.isNotEmpty()) visibleExtras += k }
                phones.clear()
                d.phones.forEachIndexed { i, p -> phones += Countries.forNumber(p.countryCode, p.fullPhoneNumber).let { c -> PhoneForm(i + 1L, c ?: countryOf(sim), if (c == Countries.NONE) p.fullPhoneNumber else p.phoneNumber, p.label) } }
                if (phones.isEmpty()) phones += PhoneForm(0, countryOf(sim))
                groupIds = d.groups.map { it.id }.toSet()
                photo = d.contact.photoPath; origPhoto = photo
            }
            loaded = true
        }
    }

    // Duplicate numbers: shown inline as "Already saved as X".
    var conflicts by remember { mutableStateOf<Map<String, Pair<Long, String>>>(emptyMap()) }
    val entities = phones.map { phoneEntity(it) }
    LaunchedEffect(entities.map { it.fullPhoneNumber }) {
        delay(150)
        conflicts = vm.repo.conflicts(entities.filter { it.phoneNumber.isNotEmpty() }, id)
    }

    val valid = name.isNotBlank() && entities.any { it.phoneNumber.isNotEmpty() } && conflicts.isEmpty() &&
        entities.map { it.fullPhoneNumber }.filter { it.isNotEmpty() }.let { it.size == it.toSet().size }

    var confirmDiscard by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    var groupSheet by remember { mutableStateOf(false) }
    var fieldSheet by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }

    fun close() {
        if (photo != origPhoto) photos.delete(photo) // an unsaved new photo is just a stray file
        onClose()
    }
    fun requestClose() { if (dirty) confirmDiscard = true else close() }
    BackHandler { requestClose() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { photos.saveFromUri(it) }?.let { new -> if (photo != origPhoto) photos.delete(photo); photo = new; dirty = true }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        bmp?.let { photos.saveBitmap(it) }?.let { new -> if (photo != origPhoto) photos.delete(photo); photo = new; dirty = true }
    }

    fun save() = scope.launch {
        val base = original ?: ContactEntity(name = "", avatarSeed = seed)
        val contact = base.copy(
            name = name.trim(), appointment = appointment.trim(), location = location.trim(),
            prefix = extras["Prefix"].orEmpty().trim(), suffix = extras["Suffix"].orEmpty().trim(), nickname = extras["Nickname"].orEmpty().trim(),
            email = extras["Email"].orEmpty().trim(), website = extras["Website"].orEmpty().trim(), birthday = extras["Birthday"].orEmpty(),
            notes = extras["Notes"].orEmpty().trim(), photoPath = photo, avatarSeed = seed,
        )
        val saved = vm.repo.save(contact, entities.filter { it.phoneNumber.isNotEmpty() }, groupIds.toList())
        if (photo != origPhoto) photos.delete(origPhoto)
        onSaved(saved)
    }

    Column(Modifier.fillMaxSize().imePadding().sharedKey(if (id == 0L) "create" else "edit-$id")
        .background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text(if (id == 0L) "Create contact" else "Edit contact") },
            navigationIcon = { IconButton(::requestClose) { Icon(Sym.Close, "Close") } },
            actions = { Button(::save, enabled = valid && loaded, modifier = Modifier.padding(end = 8.dp)) { Text("Save") } },
        )
        if (!loaded) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ContactAvatar(seed, name, photo, 96.dp, expressive = true)
                TextButton({ photoSheet = true }) { Text(if (photo.isEmpty()) "Add picture" else "Change picture") }
            }
            FieldRow(Sym.Person) { OutlinedTextField(name, { name = it; dirty = true }, Modifier.fillMaxWidth(), label = { Text("Name") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true) }
            FieldRow(Sym.Work) { OutlinedTextField(appointment, { appointment = it; dirty = true }, Modifier.fillMaxWidth(), label = { Text("Appointment") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true) }
            FieldRow(Sym.LocationOn) { OutlinedTextField(location, { location = it; dirty = true }, Modifier.fillMaxWidth(), label = { Text("Location") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true) }

            phones.forEachIndexed { i, p -> androidx.compose.runtime.key(p.key) {
                PhoneRow(
                    p, first = i == 0,
                    error = conflicts[entities[i].fullPhoneNumber],
                    duplicateInForm = entities[i].phoneNumber.isNotEmpty() && entities.count { it.fullPhoneNumber == entities[i].fullPhoneNumber } > 1,
                    sim = sim, canRemove = phones.size > 1,
                    onChange = { dirty = true }, onRemove = { phones.removeAt(i); dirty = true }, onView = onViewExisting,
                )
            } }
            TextButton({ phones += PhoneForm(System.nanoTime(), countryOf(sim), label = if (phones.size == 1) "Work" else "Other"); dirty = true }, Modifier.padding(start = 56.dp)) {
                Icon(Sym.Add, null); Spacer(Modifier.width(8.dp)); Text("Add phone")
            }

            val groups by vm.groups.collectAsStateWithLifecycle()
            FieldRow(Sym.Label) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.orEmpty().filter { it.id in groupIds }.forEach { g ->
                        InputChip(true, { groupIds = groupIds - g.id; dirty = true }, { Text(g.name) }, trailingIcon = { Icon(Sym.Close, "Remove ${g.name}", Modifier.size(18.dp)) })
                    }
                    AssistChip({ groupSheet = true }, { Text("Add to group") }, leadingIcon = { Icon(Sym.Add, null, Modifier.size(18.dp)) })
                }
            }

            visibleExtras.forEach { key ->
                AnimatedVisibility(true, enter = expandVertically(), exit = shrinkVertically()) {
                    FieldRow(null) {
                        if (key == "Birthday") {
                            OutlinedTextField(
                                extras[key].orEmpty(), {}, Modifier.fillMaxWidth().clickable { datePicker = true }, readOnly = true, enabled = false,
                                label = { Text("Birthday") }, trailingIcon = { IconButton({ datePicker = true }) { Icon(Sym.ArrowDropDown, "Pick date") } },
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        } else OutlinedTextField(
                            extras[key].orEmpty(), { extras[key] = it; dirty = true }, Modifier.fillMaxWidth(), label = { Text(key) },
                            singleLine = key != "Notes", minLines = if (key == "Notes") 3 else 1,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (key) { "Email" -> KeyboardType.Email; "Website" -> KeyboardType.Uri; else -> KeyboardType.Text },
                                capitalization = when (key) { "Email", "Website" -> KeyboardCapitalization.None; "Notes" -> KeyboardCapitalization.Sentences; else -> KeyboardCapitalization.Words },
                            ),
                        )
                    }
                }
            }
            TextButton({ fieldSheet = true }, Modifier.padding(start = 56.dp)) { Icon(Sym.Add, null); Spacer(Modifier.width(8.dp)); Text("Add more fields") }
        }
    }

    if (confirmDiscard) ConfirmDialog("Discard changes?", "Your edits to this contact won't be saved.", "Discard", { confirmDiscard = false; close() }, { confirmDiscard = false })
    if (groupSheet) GroupPickerSheet(vm, groupIds, { groupSheet = false }) { groupIds = it; dirty = true }
    if (photoSheet) PhotoSheet(
        hasPhoto = photo.isNotEmpty(), onDismiss = { photoSheet = false },
        onCamera = { photoSheet = false; camera.launch(null) },
        onGallery = { photoSheet = false; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemove = { photoSheet = false; if (photo != origPhoto) photos.delete(photo); photo = ""; dirty = true },
    )
    if (fieldSheet) ModalBottomSheet({ fieldSheet = false }) {
        Text("Add more fields", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        extraFields.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { if (f !in visibleExtras) visibleExtras += f; fieldSheet = false }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text(f, Modifier.weight(1f)); if (f in visibleExtras) Text("Added", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (datePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            { datePicker = false },
            confirmButton = { TextButton({
                // DatePicker reports UTC midnight; reading it in UTC avoids the old off-by-one-day bug.
                state.selectedDateMillis?.let { extras["Birthday"] = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString(); dirty = true }
                datePicker = false
            }) { Text("OK") } },
            dismissButton = { TextButton({ extras["Birthday"] = ""; datePicker = false }) { Text("Clear") } },
        ) { DatePicker(state) }
    }
}

private fun mutableStateMapOfStrings() = androidx.compose.runtime.mutableStateMapOf<String, String>()

private fun phoneEntity(p: PhoneForm): PhoneEntity {
    // "As typed" keeps every digit, leading zeros included; a number with a country code drops the trunk zero.
    val raw = p.country == Countries.NONE
    val digits = p.number.filter(Char::isDigit).let { if (raw) it else it.trimStart('0') }
    val dial = p.country.dialCode
    return PhoneEntity(contactId = 0, fullPhoneNumber = if (digits.isEmpty()) "" else dial + digits, phoneNumber = digits, countryCode = p.country.code, label = p.label.ifBlank { "Mobile" })
}

@Composable
private fun FieldRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box56(icon)
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable private fun Box56(icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    androidx.compose.foundation.layout.Box(Modifier.width(40.dp).height(56.dp), Alignment.CenterStart) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhoneRow(
    p: PhoneForm, first: Boolean, error: Pair<Long, String>?, duplicateInForm: Boolean, sim: String, canRemove: Boolean,
    onChange: () -> Unit, onRemove: () -> Unit, onView: (Long) -> Unit,
) {
    var picker by remember { mutableStateOf(false) }
    var labelMenu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(p.label !in builtInLabels) }
    val country = p.country
    FieldRow(if (first) Sym.Call else null) {
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                p.number, { p.number = it.filter { c -> c.isDigit() || c == ' ' || c == '-' }; onChange() }, Modifier.weight(1f), label = { Text("Phone") }, singleLine = true,
                // The country sits inside the field so both share one baseline and border.
                leadingIcon = { TextButton({ picker = true }) { Text(if (country == Countries.NONE) "${country.flag} No code" else "${country.flag} +${country.dialCode}"); Icon(Sym.ArrowDropDown, null) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = error != null || duplicateInForm,
                supportingText = {
                    when {
                        error != null -> Row(verticalAlignment = Alignment.CenterVertically) { Text("Already saved as ${error.second}"); TextButton({ onView(error.first) }) { Text("View") } }
                        duplicateInForm -> Text("Number entered twice")
                        country == Countries.NONE -> Text("Saved and dialled exactly as typed")
                    }
                },
            )
            if (canRemove) IconButton(onRemove, Modifier.padding(top = 4.dp)) { Icon(Sym.Close, "Remove number") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box {
                TextButton({ labelMenu = true }) { Text(if (custom) "Custom" else p.label); Icon(Sym.ArrowDropDown, null) }
                DropdownMenu(labelMenu, { labelMenu = false }) {
                    builtInLabels.forEach { l -> DropdownMenuItem({ Text(l) }, { p.label = l; custom = false; labelMenu = false; onChange() }) }
                    DropdownMenuItem({ Text("Custom…") }, { custom = true; labelMenu = false; if (p.label in builtInLabels) p.label = "" })
                }
            }
            if (custom) OutlinedTextField(p.label, { p.label = it; onChange() }, Modifier.weight(1f), label = { Text("Label") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true)
        }
    }
    if (picker) CountryPickerSheet(p.country, sim, { p.country = it; picker = false; onChange() }, { picker = false })
}

@Composable
private fun PhotoSheet(hasPhoto: Boolean, onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        listOf("Take photo" to onCamera, "Choose from gallery" to onGallery).forEach { (t, a) ->
            Text(t, Modifier.fillMaxWidth().clickable(onClick = a).padding(24.dp, 16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        if (hasPhoto) Text("Remove photo", Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(24.dp, 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
    }
}
