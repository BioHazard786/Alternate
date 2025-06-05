package expo.modules.callerid

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.provider.Settings
import androidx.core.net.toUri
import expo.modules.callerid.database.CallerRepository
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit

class CallerIdModule : Module() {

    // Repository for database operations
    private lateinit var callerRepository: CallerRepository
    private lateinit var sharedPreferences: SharedPreferences

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    override fun definition() = ModuleDefinition {
        // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
        // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
        // The module will be accessible from `requireNativeModule('CallerId')` in JavaScript.
        Name("CallerId")

        // Initialize Room database repository when module is created
        OnCreate {
            val context =
                appContext.reactContext ?: throw IllegalStateException("React context is null")
            callerRepository = CallerRepository(context)
            sharedPreferences =
                context.getSharedPreferences("caller_id_settings", Context.MODE_PRIVATE)
        }

        AsyncFunction("hasOverlayPermission") { promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve(false)
            } else {
                val granted = Settings.canDrawOverlays(context)
                promise.resolve(granted)
            }
        }

        AsyncFunction("requestOverlayPermission") { promise: Promise ->
            val activity = appContext.currentActivity
            if (activity == null) {
                promise.resolve(false)
            } else {
                try {
                    val builder = android.app.AlertDialog.Builder(activity)
                    builder.setTitle("Overlay Permission Required")
                    builder.setMessage("This app needs permission to draw over other apps. Do you want to allow this?")
                    builder.setPositiveButton("Yes") { dialog, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        intent.data = ("package:" + activity.packageName).toUri()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                        dialog.dismiss()
                        promise.resolve(true)
                    }
                    builder.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                        promise.resolve(false)
                    }
                    builder.setCancelable(false)
                    val dialog = builder.create()
                    dialog.show()
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error in requestOverlayPermission: ${e.message}",
                        e
                    )
                    promise.resolve(false)
                }
            }
        }

        // Store caller information in Room database
        AsyncFunction("storeCallerInfo") { phoneNumber: String, countryCode: String, name: String, appointment: String, city: String, iosRow: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = callerRepository.storeCallerInfo(
                        phoneNumber,
                        countryCode,
                        name,
                        appointment,
                        city,
                        iosRow,

                        )
                    promise.resolve(result)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error storing caller info: ${e.message}",
                        e
                    )
                    promise.resolve(false)
                }
            }
        }

        // Get caller information from Room database
        AsyncFunction("getCallerInfo") { phoneNumber: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val callerEntity = callerRepository.getCallerInfo(phoneNumber)
                    if (callerEntity != null) {
                        promise.resolve(
                            mapOf(
                                "name" to callerEntity.name,
                                "appointment" to callerEntity.appointment,
                                "city" to callerEntity.city,
                                "iosRow" to callerEntity.iosRow,
                                "countryCode" to callerEntity.countryCode,
                                "phoneNumber" to callerEntity.phoneNumber
                            )
                        )
                    } else {
                        promise.resolve(null)
                    }
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error getting caller info: ${e.message}",
                        e
                    )
                    promise.resolve(null)
                }
            }
        }

        // Remove caller information from Room database
        AsyncFunction("removeCallerInfo") { phoneNumber: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = callerRepository.removeCallerInfo(phoneNumber)
                    promise.resolve(result)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error removing caller info: ${e.message}",
                        e
                    )
                    promise.resolve(false)
                }
            }
        }

        // Get all caller info from Room database
        AsyncFunction("getAllCallerInfo") { promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val callerEntities = callerRepository.getAllCallerInfo()
                    // Convert CallerEntity objects to JavaScript-friendly format
                    val result = callerEntities.map { entity ->
                        mapOf(
                            "phoneNumber" to entity.phoneNumber,
                            "countryCode" to entity.countryCode,
                            "name" to entity.name,
                            "appointment" to entity.appointment,
                            "city" to entity.city,
                            "iosRow" to entity.iosRow
                        )
                    }
                    promise.resolve(result)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error getting all caller info: ${e.message}",
                        e
                    )
                    promise.resolve(emptyList<Map<String, String>>())
                }
            }
        }

        // Get all stored phone numbers from Room database
        AsyncFunction("getAllStoredNumbers") { promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val phoneNumbers = callerRepository.getAllStoredNumbers()
                    promise.resolve(phoneNumbers)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error getting stored numbers: ${e.message}",
                        e
                    )
                    promise.resolve(emptyList<String>())
                }
            }
        }

        // Clear all caller information from Room database
        AsyncFunction("clearAllCallerInfo") { promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = callerRepository.clearAllCallerInfo()
                    promise.resolve(result)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error clearing caller info: ${e.message}",
                        e
                    )
                    promise.resolve(false)
                }
            }
        }

        // Settings functions
        Function("setShowPopup") { showPopup: Boolean ->
            try {
                getPreferences().edit { putBoolean("show_popup", showPopup) }
                true
            } catch (e: Exception) {
                Log.e("CallerIdModule", "Error setting show popup: ${e.message}", e)
                false
            }
        }

        Function("getShowPopup") {
            try {
                val showPopup = getPreferences().getBoolean("show_popup", true)
                showPopup
            } catch (e: Exception) {
                Log.e("CallerIdModule", "Error getting show popup: ${e.message}", e)
                true
            }
        }
    }

    // Helper property to get context safely
    private val context
        get() = requireNotNull(appContext.reactContext) { "React context is null" }
    
    private fun getPreferences(): SharedPreferences {
    return context.getSharedPreferences(context.packageName + ".settings", Context.MODE_PRIVATE)
  }
}
