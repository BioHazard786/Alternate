package com.lulu786.alternate.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.store by preferencesDataStore("settings")

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark"), Amoled("AMOLED black") }

enum class AutoLock(val label: String, val millis: Long) {
    Disabled("Never", 0), Immediately("Immediately", 0), M1("After 1 minute", 60_000L),
    M5("After 5 minutes", 5 * 60_000L), H1("After 1 hour", 3_600_000L), H5("After 5 hours", 5 * 3_600_000L),
}

data class LockState(
    val enabled: Boolean = false,
    val biometric: Boolean = false,
    val autoLock: AutoLock = AutoLock.M5,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0,
    val lastLockTime: Long = 0,
    val forceLocked: Boolean = false,
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val setupDone: Boolean = false,
    val lockResetNotice: Boolean = false,
    val lock: LockState = LockState(),
)

/**
 * DataStore for app settings. The call-popup toggles stay in the 2.x SharedPreferences
 * ("<package>.settings") because CallReceiver reads them without starting the app.
 */
class Prefs(private val context: Context) {
    private val ds get() = context.store

    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val setupDone = booleanPreferencesKey("setup_done")
        val lockReset = booleanPreferencesKey("lock_reset_notice")
        val hash = stringPreferencesKey("passcode_hash")
        val salt = stringPreferencesKey("passcode_salt")
        val biometric = booleanPreferencesKey("biometric")
        val autoLock = stringPreferencesKey("auto_lock")
        val failed = intPreferencesKey("failed_attempts")
        val lockout = longPreferencesKey("lockout_until")
        val lastLock = longPreferencesKey("last_lock_time")
        val forceLocked = booleanPreferencesKey("force_locked")
        val legacyImported = booleanPreferencesKey("legacy_imported")
        val legacyPostDone = booleanPreferencesKey("legacy_post_done")
    }

    val settings: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            dynamicColor = p[K.dynamic] ?: true,
            setupDone = p[K.setupDone] ?: false,
            lockResetNotice = p[K.lockReset] ?: false,
            lock = LockState(
                enabled = p[K.hash] != null,
                biometric = p[K.biometric] ?: false,
                autoLock = p[K.autoLock]?.let { runCatching { AutoLock.valueOf(it) }.getOrNull() } ?: AutoLock.M5,
                failedAttempts = p[K.failed] ?: 0,
                lockoutUntil = p[K.lockout] ?: 0,
                lastLockTime = p[K.lastLock] ?: 0,
                forceLocked = p[K.forceLocked] ?: false,
            ),
        )
    }

    suspend fun current() = settings.first()

    suspend fun setTheme(t: ThemeMode) = ds.edit { it[K.theme] = t.name }
    suspend fun setDynamicColor(v: Boolean) = ds.edit { it[K.dynamic] = v }
    suspend fun setSetupDone() = ds.edit { it[K.setupDone] = true }
    suspend fun dismissLockResetNotice() = ds.edit { it[K.lockReset] = false }
    suspend fun setBiometric(v: Boolean) = ds.edit { it[K.biometric] = v }
    suspend fun setAutoLock(v: AutoLock) = ds.edit { it[K.autoLock] = v.name }
    suspend fun recordBackground(now: Long) = ds.edit { it[K.lastLock] = now }
    suspend fun setForceLocked(v: Boolean) = ds.edit { it[K.forceLocked] = v }

    suspend fun setPin(pin: String) = ds.edit {
        val salt = UUID.randomUUID().toString()
        it[K.salt] = salt
        it[K.hash] = hashPin(pin, salt)
        it[K.biometric] = false
        it[K.failed] = 0
        it.remove(K.lockout)
        it[K.lastLock] = System.currentTimeMillis()
    }

    suspend fun clearPin() = ds.edit {
        it.remove(K.hash); it.remove(K.salt)
        it[K.biometric] = false; it[K.failed] = 0; it.remove(K.lockout); it[K.forceLocked] = false
    }

    /** Result of one PIN try. Each wrong PIN is counted exactly once (2.x counted it twice). */
    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val until: Long) : PinResult
    }

    suspend fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        var result: PinResult = PinResult.Wrong(MAX_ATTEMPTS)
        ds.edit { p ->
            val until = p[K.lockout] ?: 0
            val hash = p[K.hash]; val salt = p[K.salt]
            result = when {
                until > now -> PinResult.LockedOut(until)
                hash == null || salt == null -> PinResult.Wrong(MAX_ATTEMPTS)
                hashPin(pin, salt) == hash -> {
                    p[K.failed] = 0; p.remove(K.lockout); p[K.forceLocked] = false
                    PinResult.Ok
                }
                else -> {
                    val failed = (p[K.failed] ?: 0) + 1
                    if (failed >= MAX_ATTEMPTS) {
                        p[K.failed] = 0
                        p[K.lockout] = now + LOCKOUT_MS
                        PinResult.LockedOut(now + LOCKOUT_MS)
                    } else {
                        p[K.failed] = failed
                        PinResult.Wrong(MAX_ATTEMPTS - failed)
                    }
                }
            }
        }
        return result
    }

    /** Biometric success unlocks without touching the failed-attempt counter or the lockout. */
    suspend fun markUnlocked() = ds.edit { it[K.forceLocked] = false; it[K.lastLock] = System.currentTimeMillis() }

    // Used by LegacyMmkvImport.
    suspend fun alreadyImportedLegacy() = ds.data.first()[K.legacyImported] == true
    suspend fun importLegacy(block: (MutablePrefs) -> Unit) = ds.edit {
        block(MutablePrefs(it))
        it[K.legacyImported] = true
    }
    suspend fun markLegacyImported() = ds.edit { it[K.legacyImported] = true }
    suspend fun legacyPostDone() = ds.data.first()[K.legacyPostDone] == true
    suspend fun markLegacyPostDone() = ds.edit { it[K.legacyPostDone] = true }

    inner class MutablePrefs(private val p: androidx.datastore.preferences.core.MutablePreferences) {
        fun theme(v: ThemeMode) { p[K.theme] = v.name }
        fun hash(hash: String, salt: String) { p[K.hash] = hash; p[K.salt] = salt }
        fun biometric(v: Boolean) { p[K.biometric] = v }
        fun autoLock(v: AutoLock) { p[K.autoLock] = v.name }
        fun failed(v: Int) { p[K.failed] = v }
        fun lockout(v: Long) { p[K.lockout] = v }
        fun lastLock(v: Long) { p[K.lastLock] = v }
        fun setupDone() { p[K.setupDone] = true }
        fun lockResetNotice() { p[K.lockReset] = true }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 60_000L

        /** Same as 2.x store/passcodeStore.ts: lowercase hex SHA-256 of pin + salt (UTF-8). */
        fun hashPin(pin: String, salt: String): String =
            MessageDigest.getInstance("SHA-256").digest((pin + salt).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
