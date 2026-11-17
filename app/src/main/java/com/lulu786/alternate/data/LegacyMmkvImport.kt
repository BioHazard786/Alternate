package com.lulu786.alternate.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/** Reads unencrypted MMKV files written by the 2.x React Native app (react-native-mmkv). */
object MmkvParser {
    /** File = uint32 LE data size, then repeated [varint keyLen][key][varint valueLen][value]. Later keys win; empty value = deleted. */
    fun parse(file: ByteArray): Map<String, String> {
        if (file.size < 4) return emptyMap()
        val size = (file[0].toInt() and 0xff) or ((file[1].toInt() and 0xff) shl 8) or
            ((file[2].toInt() and 0xff) shl 16) or ((file[3].toInt() and 0xff) shl 24)
        val end = minOf(4 + size, file.size)
        val out = LinkedHashMap<String, String>()
        var pos = 4
        fun varint(): Int {
            var shift = 0; var r = 0
            while (true) {
                require(pos < end) { "truncated varint" }
                val b = file[pos++].toInt()
                r = r or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0) return r
                shift += 7
                require(shift < 35) { "varint too long" }
            }
        }
        while (pos < end) {
            val kl = varint(); require(kl >= 0 && pos + kl <= end) { "bad key length" }
            val key = String(file, pos, kl, Charsets.UTF_8); pos += kl
            val vl = varint(); require(vl >= 0 && pos + vl <= end) { "bad value length" }
            val raw = file.copyOfRange(pos, pos + vl); pos += vl
            if (raw.isEmpty()) out.remove(key) else out[key] = decodeString(raw)
        }
        return out
    }

    /** MMKV encodes a string value as [varint length][utf8]; fall back to the raw bytes if the prefix doesn't fit. */
    private fun decodeString(raw: ByteArray): String {
        var i = 0; var len = 0; var shift = 0
        while (i < raw.size) {
            val b = raw[i++].toInt(); len = len or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return if (i + len == raw.size) String(raw, i, len, Charsets.UTF_8) else String(raw, Charsets.UTF_8)
    }
}

class LegacyMmkvImport(private val context: Context, private val prefs: Prefs) {
    private val dir get() = File(context.filesDir, "mmkv")

    /**
     * Runs once per install. [upgrading] is true when a 2.x database already existed before this launch;
     * those users skip Setup. A PIN that cannot be read never crashes the app: it stays unlocked and shows a notice.
     */
    suspend fun run(upgrading: Boolean) {
        if (prefs.alreadyImportedLegacy()) return
        val pass = File(dir, "passcode-storage")
        val theme = File(dir, "theme-storage")
        val state = runCatching { pass.takeIf { it.exists() }?.let { stateOf(it) } }
        val themeState = runCatching { theme.takeIf { it.exists() }?.let { stateOf(it) } }

        prefs.importLegacy { p ->
            if (upgrading || pass.exists() || theme.exists()) p.setupDone()
            themeState.getOrNull()?.get("themeMode")?.jsonPrimitive?.contentOrNull?.let {
                p.theme(when (it) { "light" -> ThemeMode.Light; "dark" -> ThemeMode.Dark; else -> ThemeMode.System })
            }
            state.getOrNull()?.let { s ->
                val hash = s["passcodeHash"]?.jsonPrimitive?.contentOrNull
                val salt = s["passcodeSalt"]?.jsonPrimitive?.contentOrNull
                if (s["passcodeEnabled"]?.jsonPrimitive?.booleanOrNull == true && hash != null && salt != null) {
                    p.hash(hash, salt)
                    p.biometric(s["biometricEnabled"]?.jsonPrimitive?.booleanOrNull ?: false)
                    p.autoLock(when (s["autoLockTimeout"]?.jsonPrimitive?.contentOrNull) {
                        "disabled" -> AutoLock.Disabled; "immediately" -> AutoLock.Immediately; "1m" -> AutoLock.M1
                        "1h" -> AutoLock.H1; "5h" -> AutoLock.H5; else -> AutoLock.M5
                    })
                    p.failed(s["failedAttempts"]?.jsonPrimitive?.intOrNull ?: 0)
                    p.lockout(s["lockoutUntil"]?.jsonPrimitive?.longOrNull ?: 0)
                    p.lastLock(System.currentTimeMillis()) // don't lock the user out right after the update
                }
            }
            if (state.isFailure) p.lockResetNotice()
        }
        // Delete only after the import is committed.
        dir.deleteRecursively()
    }

    private fun stateOf(file: File): JsonObject? {
        val entries = MmkvParser.parse(file.readBytes())
        val json = entries[file.name] ?: return null
        return Json.parseToJsonElement(json).jsonObject["state"]?.jsonObject
    }
}
