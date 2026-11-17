package com.lulu786.alternate.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** Opens [primary]; if no app handles it, opens [fallback] (e.g. a web link) instead. */
fun Context.startSafely(primary: Intent, fallback: Intent? = null): Boolean = try {
    startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) {
    fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false
}

private fun view(uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri())

fun Context.dial(full: String) = startSafely(Intent(Intent.ACTION_DIAL, "tel:+$full".toUri()))
fun Context.sms(full: String) = startSafely(Intent(Intent.ACTION_SENDTO, "smsto:+$full".toUri()))
fun Context.mail(address: String) = startSafely(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))
fun Context.openWeb(url: String) =
    startSafely(view(if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"))
fun Context.whatsApp(full: String) = startSafely(view("whatsapp://send?phone=$full"), view("https://wa.me/$full"))
fun Context.telegram(full: String) = startSafely(view("tg://resolve?phone=$full"), view("https://t.me/+$full"))

fun Context.copyToClipboard(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Writes [text] to cache/share and shares it through the FileProvider. */
fun Context.shareText(fileName: String, mime: String, text: String) {
    val dir = File(cacheDir, "share").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeText(text) }
    val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** SIM (or network) country as an ISO code, e.g. "IN"; null when unavailable. */
fun Context.simCountry(): String? =
    (getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)
        ?.let { it.simCountryIso.ifEmpty { it.networkCountryIso } }?.uppercase()?.ifEmpty { null }
