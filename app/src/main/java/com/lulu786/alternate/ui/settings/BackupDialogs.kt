package com.lulu786.alternate.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lulu786.alternate.util.BackupCrypto
import com.lulu786.alternate.util.copyToClipboard

private const val MIN_PASSWORD = 8

/** Pick how backups are locked: a password the user chooses, or a recovery key we generate and show once. */
@Composable
fun BackupKeyDialog(onDismiss: () -> Unit, onConfirm: (mode: Int, secret: String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf<Int?>(null) }
    val recoveryKey = rememberSaveable { BackupCrypto.newRecoveryKey() }
    var password by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val cancel = @Composable { TextButton(onDismiss) { Text("Cancel") } }

    when (mode) {
        null -> AlertDialog(onDismiss, confirmButton = cancel, title = { Text("Encrypt backups") }, text = {
            Column {
                Text("Backups are encrypted. Choose how you will unlock them when you restore.")
                Spacer(Modifier.height(8.dp))
                TextButton({ mode = BackupCrypto.MODE_PASSWORD }, Modifier.fillMaxWidth()) { Text("Choose a password") }
                TextButton({ mode = BackupCrypto.MODE_RECOVERY }, Modifier.fillMaxWidth()) { Text("Generate a recovery key") }
            }
        })
        BackupCrypto.MODE_PASSWORD -> AlertDialog(
            onDismiss, title = { Text("Backup password") },
            dismissButton = cancel,
            confirmButton = { TextButton({ onConfirm(BackupCrypto.MODE_PASSWORD, password) }, enabled = password.length >= MIN_PASSWORD && password == again) { Text("Turn on") } },
            text = {
                Column {
                    Text("There is no way to recover a forgotten password, and without it the backups cannot be opened.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    SecretField("Password", password, { password = it }, supporting = "At least $MIN_PASSWORD characters")
                    Spacer(Modifier.height(8.dp))
                    SecretField("Repeat password", again, { again = it }, error = again.isNotEmpty() && again != password, supporting = if (again.isNotEmpty() && again != password) "Doesn’t match" else null)
                }
            },
        )
        else -> AlertDialog(
            onDismiss, title = { Text("Your recovery key") },
            dismissButton = cancel,
            confirmButton = { TextButton({ onConfirm(BackupCrypto.MODE_RECOVERY, recoveryKey) }, enabled = saved) { Text("Turn on") } },
            text = {
                Column {
                    Text("Write this down or keep it in a password manager. It is the only way to open your backups on another phone, and it is not shown again.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer { Text(recoveryKey, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace) }
                    TextButton({ context.copyToClipboard("Recovery key", recoveryKey) }) { Text("Copy") }
                    Row(Modifier.fillMaxWidth().toggleable(saved, onValueChange = { saved = it }, role = Role.Checkbox).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saved, null)
                        Text("I have saved this key", Modifier.padding(start = 12.dp))
                    }
                }
            },
        )
    }
}

/** Asks for the secret of the backup being restored. Stays open after a wrong try so it can be retyped. */
@Composable
fun RestoreDialog(mode: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var secret by rememberSaveable { mutableStateOf("") }
    val recovery = mode == BackupCrypto.MODE_RECOVERY
    AlertDialog(
        onDismiss, title = { Text("Restore backup") },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton({ onConfirm(secret) }, enabled = secret.isNotBlank()) { Text("Restore") } },
        text = {
            Column {
                Text("Contacts that are not in Alternate yet are added. Nothing is overwritten.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                SecretField(if (recovery) "Recovery key" else "Password", secret, { secret = it }, visible = recovery)
            }
        },
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false, supporting: String? = null, visible: Boolean = false) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = error,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (visible) KeyboardType.Ascii else KeyboardType.Password),
    )
}
