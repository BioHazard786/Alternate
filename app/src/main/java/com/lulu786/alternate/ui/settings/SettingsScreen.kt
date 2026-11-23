package com.lulu786.alternate.ui.settings

import com.lulu786.alternate.ui.Sym
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.BuildConfig
import com.lulu786.alternate.data.AutoLock
import com.lulu786.alternate.data.ThemeMode
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.IconSwitch
import com.lulu786.alternate.ui.components.RoundedTitle
import com.lulu786.alternate.ui.components.segmentedRowColors
import com.lulu786.alternate.ui.lock.biometricAvailable
import com.lulu786.alternate.util.openWeb
import com.lulu786.alternate.util.simCountry
import kotlinx.coroutines.launch

private const val REPO = "https://github.com/BioHazard786/Alternate"

@Composable
fun SettingsScreen(vm: AppViewModel, onSetup: () -> Unit, onSetPin: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val popups = remember { context.getSharedPreferences(context.packageName + ".settings", Context.MODE_PRIVATE) }
    var incoming by remember { mutableStateOf(popups.getBoolean("show_incoming_popup", true)) }
    var outgoing by remember { mutableStateOf(popups.getBoolean("show_outgoing_popup", true)) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val overlay = remember(refresh) { Settings.canDrawOverlays(context) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { it?.let { u -> vm.exportTo(context, u) } }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.exportTo(context, u, csv = true) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importFrom(context, u, context.simCountry()) } }

    Scaffold(Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = { LargeFlexibleTopAppBar(title = { RoundedTitle("Settings") }, scrollBehavior = scroll) }, containerColor = MaterialTheme.colorScheme.surface) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 32.dp)) {
            if (settings.lockResetNotice) item {
                Group("Action needed", listOf(Row0(Sym.Lock, "Re-set your app lock", "We couldn’t carry your PIN over from the old version.", onClick = { vm.dismissLockNotice(); onSetPin() })))
            }
            item {
                Group("Caller ID", listOf(
                    Row0(Sym.CallReceived, "Incoming call popup", null, trailing = { IconSwitch(incoming) }, onClick = { incoming = !incoming; popups.edit { putBoolean("show_incoming_popup", incoming) } }),
                    Row0(Sym.CallMade, "Outgoing call popup", null, trailing = { IconSwitch(outgoing) }, onClick = { outgoing = !outgoing; popups.edit { putBoolean("show_outgoing_popup", outgoing) } }),
                    Row0(Sym.Layers, "Display over other apps", if (overlay) "Allowed ✓" else "Not allowed — tap to fix", onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                    }),
                    Row0(Sym.Build, "Caller ID setup", "Permissions for the popup", onClick = onSetup),
                ))
            }
            item {
                val bio = remember(refresh) { biometricAvailable(context) }
                Group("Security", listOfNotNull(
                    Row0(Sym.Lock, "App lock", if (settings.lock.enabled) "On" else "Off", trailing = { IconSwitch(settings.lock.enabled) },
                        onClick = { if (settings.lock.enabled) dialog = "removeLock" else onSetPin() }),
                    if (settings.lock.enabled && bio) Row0(Sym.Fingerprint, "Fingerprint unlock", null, trailing = { IconSwitch(settings.lock.biometric) },
                        onClick = { scope.launch { vm.prefs.setBiometric(!settings.lock.biometric) } }) else null,
                    if (settings.lock.enabled) Row0(Sym.Timer, "Auto-lock after", settings.lock.autoLock.label, onClick = { dialog = "autoLock" }) else null,
                ))
            }
            item {
                Group("Appearance", listOfNotNull(
                    Row0(Sym.Brightness6, "Theme", settings.theme.label, onClick = { dialog = "theme" }),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Row0(Sym.Palette, "Dynamic color", "Use your wallpaper colors", trailing = { IconSwitch(settings.dynamicColor) },
                        onClick = { scope.launch { vm.prefs.setDynamicColor(!settings.dynamicColor) } }) else null,
                ))
            }
            item {
                Group("Data", listOf(
                    Row0(Sym.Download, "Import from file", "VCF or CSV", onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel")) }),
                    Row0(Sym.Upload, "Export as VCF", "Contacts with photos", onClick = { exportLauncher.launch("contacts.vcf") }),
                    Row0(Sym.Upload, "Export as CSV", "For editing in a spreadsheet", onClick = { exportCsvLauncher.launch("contacts.csv") }),
                ))
            }
            item {
                Group("About", listOf(
                    Row0(Sym.Info, "Version", BuildConfig.VERSION_NAME, onClick = {}),
                    Row0(Sym.Code, "Source on GitHub", REPO, onClick = { context.openWeb(REPO) }),
                    Row0(Sym.PrivacyTip, "Privacy policy", null, onClick = { context.openWeb("$REPO/blob/main/PRIVACY_POLICY.md") }),
                    Row0(Sym.Description, "Open-source licenses", null, onClick = { dialog = "licenses" }),
                ))
            }
        }
    }

    when (dialog) {
        "autoLock" -> ChoiceDialog("Auto-lock after", AutoLock.entries, settings.lock.autoLock, { it.label }, { dialog = null }) { scope.launch { vm.prefs.setAutoLock(it) }; dialog = null }
        "theme" -> ChoiceDialog("Theme", ThemeMode.entries, settings.theme, { it.label }, { dialog = null }) { scope.launch { vm.prefs.setTheme(it) }; dialog = null }
        "removeLock" -> ConfirmDialog("Turn off app lock?", "Anyone who opens Alternate will see your contacts.", "Turn off", { scope.launch { vm.prefs.clearPin() }; dialog = null }, { dialog = null })
        "licenses" -> AlertDialog({ dialog = null }, confirmButton = { TextButton({ dialog = null }) { Text("Close") } }, title = { Text("Open-source licenses") },
            text = { Text("Alternate is built with AndroidX (Compose, Room, Navigation, DataStore, Biometric), Material Components for Android and Coil — all Apache License 2.0.") })
    }
}

private class Row0(val icon: ImageVector, val title: String, val subtitle: String?, val trailing: (@Composable () -> Unit)? = null, val onClick: () -> Unit)

@Composable
private fun Group(title: String, rows: List<Row0>) {
    Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, r ->
            SegmentedListItem(
                onClick = r.onClick, shapes = ListItemDefaults.segmentedShapes(i, rows.size), colors = segmentedRowColors(),
                leadingContent = { Icon(r.icon, null) },
                supportingContent = r.subtitle?.let { { Text(it) } },
                trailingContent = r.trailing,
            ) { Text(r.title) }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(onDismiss, confirmButton = { TextButton(onDismiss) { Text("Cancel") } }, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup()) {
            options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).selectable(o == selected, onClick = { onPick(o) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(o == selected, null); Spacer(Modifier.width(16.dp)); Text(label(o), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    })
}
