package expo.modules.callerid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import expo.modules.callerid.database.CallerRepository
import kotlinx.coroutines.Runnable
import java.lang.ref.WeakReference

data class CallerInfo(
    val name: String = "",
    val appointment: String = "",
    val city: String = "",
    val iosRow: String = ""
)

interface GetCallerHandler {
    fun onGetCaller(callerInfo: CallerInfo?)
}

class CallReceiver : BroadcastReceiver() {
    companion object {
        private var isShowingOverlay = false
        private var overlay: WeakReference<View>? = null
        var callServiceNumber: String? = null
        private var autoDismissHandler: Handler? = null
        private var autoDismissRunnable: Runnable? = null
        private const val AUTO_DISMISS_DELAY = 5000L // 5 seconds
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }

        // Check if popup display is enabled in settings
        val sharedPreferences = context.getSharedPreferences("caller_id_settings", Context.MODE_PRIVATE)
        val showPopup = sharedPreferences.getBoolean("show_popup", true)
        
        if (!showPopup) {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!isShowingOverlay) {
                    // Note: EXTRA_INCOMING_NUMBER is deprecated in API 29+
                    // For production apps targeting API 29+, consider using CallScreeningService
                    @Suppress("DEPRECATION")
                    var phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (phoneNumber == null) {
                        phoneNumber = callServiceNumber
                    }

                    if (phoneNumber == null) {
                        return
                    }
                    isShowingOverlay = true

                    getCallerName(context, phoneNumber, object : GetCallerHandler {
                        override fun onGetCaller(callerInfo: CallerInfo?) {
                            if (callerInfo != null) {
                                showCallerInfo(
                                    context,
                                    callerInfo.name,
                                    callerInfo.appointment,
                                    callerInfo.city
                                )
                            }
                        }
                    })
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isShowingOverlay) {
                    isShowingOverlay = false
                    callServiceNumber = null
                    cancelAutoDismiss()
                    dismissCallerInfo(context)
                }
            }
        }
    }

    private fun getApplicationName(context: Context): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) {
            applicationInfo.nonLocalizedLabel.toString()
        } else {
            context.getString(stringId)
        }
    }

    private fun showCallerInfo(
        context: Context,
        callerName: String,
        callerAppointment: String,
        callerCity: String
    ) {
        val appName = getApplicationName(context)

        Handler(Looper.getMainLooper()).postDelayed({
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (overlay?.get() == null) {
                val inflater = LayoutInflater.from(context)
                val overlayView = inflater.inflate(R.layout.caller_info_dialog, null)
                overlay = WeakReference(overlayView)
            }
            val typeParam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Handle deprecated FLAG_SHOW_WHEN_LOCKED for different API levels
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // For API 27+, FLAG_SHOW_WHEN_LOCKED is deprecated
                // Use FLAG_KEEP_SCREEN_ON and FLAG_TURN_SCREEN_ON instead
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            } else {
                // For older versions, use the deprecated flag with suppression
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                typeParam,
                flags,
                PixelFormat.TRANSLUCENT
            )
            overlay?.get()?.let { overlayView ->
                // Fill layout with data first
                fillLayout(context, appName, callerName, callerAppointment, callerCity)

                // Add view to window manager
                windowManager.addView(overlayView, params)

                // Setup auto-dismiss
                setupAutoDismiss(context)
            }
        }, 1000)
    }

    private fun fillLayout(
        context: Context,
        appName: String,
        callerName: String,
        callerAppointment: String,
        callerCity: String
    ) {
        overlay?.get()?.let { overlayView ->
            // Set close button listener
            try {
                val closeButton = overlayView.findViewById<ImageButton>(R.id.close_btn)
                closeButton?.setOnClickListener {
                    Log.d("CallReceiver", "Close button clicked")
                    isShowingOverlay = false
                    cancelAutoDismiss()
                    dismissCallerInfo(context)
                }
            } catch (e: Exception) {
                Log.e("CallReceiver", "Error setting close button listener", e)
            }

            // Set app name
            try {
                val textViewAppName = overlayView.findViewById<TextView>(R.id.appName)
                textViewAppName?.text = appName
            } catch (e: Exception) {
                // Handle exception silently
            }

            // Set caller name
            try {
                val textViewCallerName = overlayView.findViewById<TextView>(R.id.callerName)
                textViewCallerName?.text = callerName
            } catch (e: Exception) {
                // Handle exception silently
            }

            // Set caller appointment
            try {
                val textViewCallerAppointment =
                    overlayView.findViewById<TextView>(R.id.callerAppointment)
                if (callerAppointment.isNotEmpty()) {
                    textViewCallerAppointment?.text = callerAppointment
                } else {
                    textViewCallerAppointment?.visibility = View.GONE
                }
            } catch (e: Exception) {
                // Handle exception silently
            }

            // Set caller city
            try {
                val textViewCallerCity = overlayView.findViewById<TextView>(R.id.callerCity)
                if (callerCity.isNotEmpty()) {
                    textViewCallerCity?.text = callerCity
                } else {
                    textViewCallerCity?.visibility = View.GONE
                }
            } catch (e: Exception) {
                // Handle exception silently
            }

            // Set app icon
            try {
                val appIconImage = overlayView.findViewById<ImageView>(R.id.appIcon)
                try {
                    val icon = context.packageManager.getApplicationIcon(context.packageName)
                    appIconImage?.setImageDrawable(icon)
                } catch (e: PackageManager.NameNotFoundException) {
                    appIconImage?.visibility = View.GONE
                }
            } catch (e: Exception) {
                // Handle exception silently
            }
        }
    }

    private fun dismissCallerInfo(context: Context) {
        Handler(Looper.getMainLooper()).post {
            overlay?.get()?.let { overlayView ->
                try {
                    val windowManager =
                        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    windowManager?.removeView(overlayView)
                    Log.d("CallReceiver", "Overlay view removed from window manager")
                } catch (e: Exception) {
                    Log.e("CallReceiver", "Error removing overlay view", e)
                }
            }
            overlay = null
        }
    }

    private fun setupAutoDismiss(context: Context) {
        cancelAutoDismiss() // Cancel any existing auto-dismiss

        Log.d("CallReceiver", "Setting up auto-dismiss for ${AUTO_DISMISS_DELAY}ms")

        autoDismissHandler = Handler(Looper.getMainLooper())
        autoDismissRunnable = Runnable {
            Log.d("CallReceiver", "Auto-dismiss triggered, isShowingOverlay: $isShowingOverlay")
            if (isShowingOverlay) {
                isShowingOverlay = false
                dismissCallerInfo(context)
            }
        }

        autoDismissHandler?.postDelayed(autoDismissRunnable!!, AUTO_DISMISS_DELAY)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { runnable ->
            autoDismissHandler?.removeCallbacks(runnable)
        }
        autoDismissHandler = null
        autoDismissRunnable = null
    }

    private fun getCallerName(
        context: Context,
        phoneNumberInString: String,
        callback: GetCallerHandler
    ) {
        try {
            // Remove leading + if present
            val correctedPhoneNumber = if (phoneNumberInString.startsWith("+")) {
                phoneNumberInString.substring(1)
            } else {
                phoneNumberInString
            }

            // Use Room database to get caller information synchronously
            val callerRepository = CallerRepository(context)
            var callerEntity = callerRepository.getCallerInfoSync(correctedPhoneNumber)

            if (callerEntity == null) {
                // Try with original phone number format
                callerEntity = callerRepository.getCallerInfoSync(phoneNumberInString)
            }

            if (callerEntity != null) {
                Log.d("CallReceiver", "Caller Number found: ${callerEntity.phoneNumber}")
                val callerInfo = CallerInfo(
                    name = callerEntity.name,
                    appointment = callerEntity.appointment,
                    city = callerEntity.city,
                    iosRow = callerEntity.iosRow
                )
                callback.onGetCaller(callerInfo)
            } else {
                callback.onGetCaller(null)
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Error getting caller name", e)
            callback.onGetCaller(null)
        }
    }
}
