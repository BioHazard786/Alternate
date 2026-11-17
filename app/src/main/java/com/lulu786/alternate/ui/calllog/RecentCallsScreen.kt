package com.lulu786.alternate.ui.calllog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lulu786.alternate.ui.Sym
import com.lulu786.alternate.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Recent(val number: String, val name: String, val date: Long, val type: Int) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE || type == CallLog.Calls.BLOCKED_TYPE
}

/** Latest call per number, newest first. One row per person, not per call. */
private fun readRecent(context: Context): List<Recent> {
    val seen = HashSet<String>()
    val out = ArrayList<Recent>()
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE),
        null, null, "${CallLog.Calls.DATE} DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 100) {
            val number = c.getString(0)?.trim().orEmpty()
            // Private callers have no digits and SIP calls are addresses (user@host); neither can be saved. Last 10 digits folds "+91…", "091…" and "…" into one entry.
            if (number.none(Char::isDigit) || '@' in number || !seen.add(number.filter(Char::isDigit).takeLast(10))) continue
            out += Recent(number, c.getString(1).orEmpty(), c.getLong(2), c.getInt(3))
        }
    }
    return out
}

/** Pick a recent caller to save; the number (and the dialer's name for it, if any) lands in the create form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentCallsScreen(onBack: () -> Unit, onPick: (number: String, name: String) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val granted = remember(refresh) { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> denied = !ok; refresh++ }
    val calls by produceState<List<Recent>?>(null, granted) {
        value = if (granted) withContext(Dispatchers.IO) { runCatching { readRecent(context) }.getOrDefault(emptyList()) } else emptyList()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text("Recent calls") },
            navigationIcon = { IconButton(onBack) { Icon(Sym.ArrowBack, "Back") } },
        )
        val list = calls
        when {
            !granted -> EmptyState(
                Sym.Call, "Allow call log access",
                "Alternate reads your recent calls on this device so you can pick a number to save. Nothing is stored or sent anywhere.",
            ) {
                Button({
                    if (denied) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    else ask.launch(Manifest.permission.READ_CALL_LOG)
                }) { Text(if (denied) "Open app settings" else "Allow") }
            }
            list == null -> Box(Modifier.fillMaxSize())
            list.isEmpty() -> EmptyState(Sym.Call, "No recent calls")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                items(list, key = { it.number }) { r ->
                    val now = System.currentTimeMillis()
                    val whenText = DateUtils.getRelativeTimeSpanString(r.date, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                    ListItem(
                        headlineContent = { Text(r.name.ifEmpty { r.number }) },
                        supportingContent = { Text(if (r.name.isEmpty()) "$whenText" else "${r.number} · $whenText") },
                        leadingContent = {
                            Icon(
                                if (r.type == CallLog.Calls.OUTGOING_TYPE) Sym.CallMade else Sym.CallReceived, null,
                                tint = if (r.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { onPick(r.number, r.name) },
                    )
                }
            }
        }
    }
}
