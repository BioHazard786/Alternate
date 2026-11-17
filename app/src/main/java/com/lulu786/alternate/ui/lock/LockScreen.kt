package com.lulu786.alternate.ui.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lulu786.alternate.data.LockState
import com.lulu786.alternate.data.Prefs
import com.lulu786.alternate.ui.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val PIN_LENGTH = 4

fun biometricAvailable(activity: android.content.Context) =
    BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Alternate").setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build(),
    )
}

/** Covers the whole app until the right PIN (or fingerprint) is given. */
@Composable
fun LockScreen(vm: AppViewModel, lock: LockState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableStateOf(lock.lockoutUntil) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val remaining = ((lockedUntil - now) / 1000 + 1).coerceAtLeast(0)
    val lockedOut = lockedUntil > now

    LaunchedEffect(lockedUntil) { while (lockedUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val useBio = lock.biometric && biometricAvailable(context)
    LaunchedEffect(Unit) { if (useBio) (context as? FragmentActivity)?.let { promptBiometric(it, vm::unlockWithBiometric) } }

    PinEntry(
        title = "Enter PIN",
        subtitle = if (lockedOut) "Try again in $remaining s" else null,
        error = error, shake = shake, enabled = !lockedOut,
        onFingerprint = if (useBio) ({ (context as? FragmentActivity)?.let { promptBiometric(it, vm::unlockWithBiometric) } }) else null,
        onComplete = { pin ->
            scope.launch {
                when (val r = vm.tryPin(pin)) {
                    is Prefs.PinResult.Ok -> Unit
                    is Prefs.PinResult.Wrong -> { error = true; shake++ }
                    is Prefs.PinResult.LockedOut -> { error = true; shake++; lockedUntil = r.until; now = System.currentTimeMillis() }
                }
            }
        },
        onEdit = { error = false },
    )
}

/** Set a new PIN: enter, then confirm. */
@Composable
fun PinSetupScreen(vm: AppViewModel, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    BackHandler { if (first != null) first = null else onDone() }
    PinEntry(
        title = if (first == null) "Create a PIN" else "Confirm your PIN",
        subtitle = if (error) "PINs didn’t match, try again" else null, error = error, shake = shake, enabled = true,
        onFingerprint = null, onEdit = { error = false },
        onComplete = { pin ->
            val f = first
            when {
                f == null -> first = pin
                f == pin -> scope.launch { vm.prefs.setPin(pin); vm.say("App lock turned on"); onDone() }
                else -> { first = null; error = true; shake++ }
            }
        },
    )
}

@Composable
private fun PinEntry(
    title: String, subtitle: String?, error: Boolean, shake: Int, enabled: Boolean,
    onFingerprint: (() -> Unit)?, onComplete: (String) -> Unit, onEdit: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) {
            for (x in listOf(-24f, 24f, -16f, 16f, -8f, 8f, 0f)) offset.animateTo(x, spring(stiffness = 900f))
            pin = ""
        }
    }
    LaunchedEffect(title) { pin = "" }
    fun press(d: String) {
        if (!enabled || pin.length >= PIN_LENGTH) return
        onEdit(); pin += d
        if (pin.length == PIN_LENGTH) { onComplete(pin) }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.offset(x = offset.value.dp).semantics { contentDescription = "${pin.length} of $PIN_LENGTH digits entered" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < pin.length
                val scale by animateFloatAsState(if (filled) 1f else 0.7f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "dot")
                Box(Modifier.size(16.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(if (error) MaterialTheme.colorScheme.error else if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.height(32.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) { r.forEach { Key(it) { press(it) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            if (onFingerprint != null) Key(null, Icons.Filled.Fingerprint, "Use fingerprint", onFingerprint) else Spacer(Modifier.size(72.dp))
            Key("0") { press("0") }
            Key(null, Icons.AutoMirrored.Filled.Backspace, "Delete") { if (pin.isNotEmpty()) { pin = pin.dropLast(1); onEdit() } }
        }
    }
}

/** Circular key that morphs toward a squircle while pressed. */
@Composable
private fun Key(label: String?, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, description: String? = null, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 22.dp else 36.dp, spring(dampingRatio = 0.5f, stiffness = 600f), label = "corner")
    Box(
        Modifier.size(72.dp).background(if (pressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(corner))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = description ?: label.orEmpty() },
        Alignment.Center,
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.headlineMedium) else if (icon != null) Icon(icon, null)
    }
}
