package com.lulu786.alternate.ui.setup

import com.lulu786.alternate.ui.Sym
import android.Manifest
import android.content.Intent
import android.app.role.RoleManager
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.SectionCard

private val phonePerms = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)

/** First-run permission cards. Skippable, and reachable again from Settings. */
@Composable
fun SetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val phone = remember(refresh) { phonePerms.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED } }
    val overlay = remember(refresh) { Settings.canDrawOverlays(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val roleManager = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService(RoleManager::class.java) else null }
    val canScreen = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    val screening = remember(refresh) { roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    var disclosure by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Set up caller ID", style = MaterialTheme.typography.headlineMedium)
        Text("Alternate needs a few permissions to show who’s calling. You can change these later in Settings.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))
        PermissionCard("Phone & call log", "So Alternate can see who’s calling.", phone, "Allow") { disclosure = true }
        if (canScreen) {
            Spacer(Modifier.height(12.dp))
            PermissionCard("Caller ID app", "Make Alternate your phone’s caller ID app so the popup appears reliably for incoming calls.", screening, "Set") {
                askRole.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }
        Spacer(Modifier.height(12.dp))
        PermissionCard("Display over other apps", "To show the caller popup on top of the call screen.", overlay, "Allow") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Column(Modifier.padding(16.dp)) {
                Text("Your phone app", style = MaterialTheme.typography.titleMedium)
                Text("To see names in your dialer’s search, make sure your phone app is allowed to read Contacts. Alternate offers its numbers to it automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (disclosure) DisclosureDialog({ disclosure = false; ask.launch(phonePerms) }, { disclosure = false })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (phone && overlay) Button({ vm.finishSetup(); onDone() }) { Text("Done") }
            else TextButton({ vm.finishSetup(); onDone() }) { Text("Skip for now") }
        }
    }
}

@Composable
private fun DisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why Alternate needs phone access") },
        text = {
            Text(
                "Alternate is a caller ID app. To show who is calling, it reads the phone number of incoming and outgoing calls " +
                    "(Phone and Call log permissions) and looks it up in the contacts you saved inside Alternate.\n\n" +
                    "The number is only compared with your saved contacts, on this device. It is never stored, shared or sent anywhere, " +
                    "and Alternate has no internet access.",
            )
        },
        confirmButton = { TextButton(onAgree) { Text("Agree and continue") } },
        dismissButton = { TextButton(onDismiss) { Text("No thanks") } },
    )
}

@Composable
private fun PermissionCard(title: String, body: String, granted: Boolean, action: String, onClick: () -> Unit) {
    SectionCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) Icon(Sym.CheckCircleFilled, "Allowed", tint = MaterialTheme.colorScheme.primary)
            else OutlinedButton(onClick) { Text(action) }
        }
    }
}
