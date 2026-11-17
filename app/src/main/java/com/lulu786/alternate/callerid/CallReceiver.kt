package com.lulu786.alternate.callerid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import com.lulu786.alternate.AlternateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallReceiver : BroadcastReceiver() {
    companion object {
        /** Set by [CallDetectScreeningService] when the broadcast carries no number. */
        @Volatile var callServiceNumber: String? = null
        @Volatile private var callActive = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) return
        val settings = context.getSharedPreferences(context.packageName + ".settings", Context.MODE_PRIVATE)

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING ->
                if (settings.getBoolean("show_incoming_popup", true)) start(context, intent)
            TelephonyManager.EXTRA_STATE_OFFHOOK ->
                if (settings.getBoolean("show_outgoing_popup", true)) start(context, intent)
            TelephonyManager.EXTRA_STATE_IDLE -> {
                callActive = false
                callServiceNumber = null
                CallOverlay.dismiss(context)
            }
        }
    }

    private fun start(context: Context, intent: Intent) {
        if (callActive || CallOverlay.isShowing) return
        @Suppress("DEPRECATION")
        val raw = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: callServiceNumber ?: return
        callActive = true

        val app = context.applicationContext
        val iso = (app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.let { it.networkCountryIso.ifEmpty { it.simCountryIso } }?.uppercase()
        val pending = goAsync()
        // The lookup runs off the main thread; 2.x blocked it with runBlocking.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = (app as AlternateApp).container.repo
                val hit = repo.lookup(PhoneNormalizer.normalize(raw, iso))
                delay(500) // same settle time as 2.x so the dialer UI is up first
                withContext(Dispatchers.Main) {
                    if (hit != null && callActive) CallOverlay.show(app, hit)
                }
            } catch (_: Exception) {
                // A failed lookup must never crash the call flow.
            } finally {
                pending.finish()
            }
        }
    }
}
