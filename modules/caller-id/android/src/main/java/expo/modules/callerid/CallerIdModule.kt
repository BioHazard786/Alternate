package expo.modules.callerid

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.net.toUri
import expo.modules.callerid.database.CallerEntity
import expo.modules.callerid.database.CallerRepository
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallerIdModule : Module() {

    // Repository for database operations
    private lateinit var callerRepository: CallerRepository

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    @RequiresApi(Build.VERSION_CODES.M)
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
        AsyncFunction("storeCallerInfo") { callerData: Map<String, Any?>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val callerEntity = CallerEntity(
                        fullPhoneNumber = callerData["fullPhoneNumber"] as? String ?: "",
                        phoneNumber = callerData["phoneNumber"] as? String ?: "",
                        countryCode = callerData["countryCode"] as? String ?: "",
                        name = callerData["name"] as? String ?: "",
                        appointment = callerData["appointment"] as? String ?: "",
                        location = callerData["location"] as? String ?: "",
                        iosRow = callerData["iosRow"] as? String ?: "",
                        suffix = callerData["suffix"] as? String ?: "",
                        prefix = callerData["prefix"] as? String ?: "",
                        email = callerData["email"] as? String ?: "",
                        notes = callerData["notes"] as? String ?: "",
                        website = callerData["website"] as? String ?: "",
                        birthday = callerData["birthday"] as? String ?: "",
                        labels = callerData["labels"] as? String ?: "",
                        nickname = callerData["nickname"] as? String ?: ""
                    )
                    val result = callerRepository.storeCallerInfo(callerEntity)
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

        // Store multiple caller information in Room database
        AsyncFunction("storeMultipleCallerInfo") { callerDataList: List<Map<String, Any?>>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val callerEntities = callerDataList.map { data ->
                        CallerEntity(
                            fullPhoneNumber = data["fullPhoneNumber"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            countryCode = data["countryCode"] as? String ?: "",
                            name = data["name"] as? String ?: "",
                            appointment = data["appointment"] as? String ?: "",
                            location = data["location"] as? String ?: "",
                            iosRow = data["iosRow"] as? String ?: "",
                            suffix = data["suffix"] as? String ?: "",
                            prefix = data["prefix"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            notes = data["notes"] as? String ?: "",
                            website = data["website"] as? String ?: "",
                            birthday = data["birthday"] as? String ?: "",
                            labels = data["labels"] as? String ?: "",
                            nickname = data["nickname"] as? String ?: ""
                        )
                    }
                    val result = callerRepository.storeMultipleCallerInfo(callerEntities)
                    promise.resolve(result)
                } catch (e: Exception) {
                    Log.e(
                        "CallerIdModule",
                        "Error storing multiple caller info: ${e.message}",
                        e
                    )
                    promise.resolve(false)
                }
            }
        }

        // Get caller information from Room database
        AsyncFunction("getCallerInfo") { fullPhoneNumber: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val callerEntity = callerRepository.getCallerInfo(fullPhoneNumber)
                    if (callerEntity != null) {
                        promise.resolve(
                            mapOf(
                                "fullPhoneNumber" to callerEntity.fullPhoneNumber,
                                "phoneNumber" to callerEntity.phoneNumber,
                                "countryCode" to callerEntity.countryCode,
                                "name" to callerEntity.name,
                                "appointment" to callerEntity.appointment,
                                "location" to callerEntity.location,
                                "iosRow" to callerEntity.iosRow,
                                "suffix" to callerEntity.suffix,
                                "prefix" to callerEntity.prefix,
                                "email" to callerEntity.email,
                                "notes" to callerEntity.notes,
                                "website" to callerEntity.website,
                                "birthday" to callerEntity.birthday,
                                "labels" to callerEntity.labels,
                                "nickname" to callerEntity.nickname
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
        AsyncFunction("removeCallerInfo") { fullPhoneNumber: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = callerRepository.removeCallerInfo(fullPhoneNumber)
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
                            "fullPhoneNumber" to entity.fullPhoneNumber,
                            "phoneNumber" to entity.phoneNumber,
                            "countryCode" to entity.countryCode,
                            "name" to entity.name,
                            "appointment" to entity.appointment,
                            "location" to entity.location,
                            "iosRow" to entity.iosRow,
                            "suffix" to entity.suffix,
                            "prefix" to entity.prefix,
                            "email" to entity.email,
                            "notes" to entity.notes,
                            "website" to entity.website,
                            "birthday" to entity.birthday,
                            "labels" to entity.labels,
                            "nickname" to entity.nickname
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

        // Get SIM card country code using TelephonyManager
        Function("getDialCountryCode") {
            try {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null) {
                    // Get SIM country ISO code (e.g., "US", "IN", "GB")
                    val simCountryIso = telephonyManager.simCountryIso?.uppercase()

                    // Return the country code, default to "IN" if null or empty
                    return@Function if (!simCountryIso.isNullOrEmpty()) simCountryIso else "IN"
                } else {
                    Log.w("CallerIdModule", "TelephonyManager not available")
                    return@Function "IN"
                }
            } catch (e: Exception) {
                Log.e("CallerIdModule", "Error getting SIM country code: ${e.message}", e)
                "IN"
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
