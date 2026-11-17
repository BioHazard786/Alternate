package com.lulu786.alternate.ui.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Set up caller ID", style = MaterialTheme.typography.headlineMedium)
        Text("Alternate needs two permissions to show who’s calling. You can change these later in Settings.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(16.dp))
        PermissionCard("Phone & call log", "So Alternate can see who’s calling.", phone, "Allow") { ask.launch(phonePerms) }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (phone && overlay) Button({ vm.finishSetup(); onDone() }) { Text("Done") }
            else TextButton({ vm.finishSetup(); onDone() }) { Text("Skip for now") }
        }
    }
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
            if (granted) Icon(Icons.Filled.CheckCircle, "Allowed", tint = MaterialTheme.colorScheme.primary)
            else OutlinedButton(onClick) { Text(action) }
        }
    }
}
