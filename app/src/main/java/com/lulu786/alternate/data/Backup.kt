package com.lulu786.alternate.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything a restore needs to bring a contact back exactly as it was, avatar and dates included. */
@Serializable
data class BackupPhone(val full: String, val national: String, val country: String, val label: String)

@Serializable
data class BackupContact(
    val name: String,
    val prefix: String = "", val suffix: String = "", val nickname: String = "", val appointment: String = "", val location: String = "",
    val email: String = "", val website: String = "", val birthday: String = "", val notes: String = "",
    val starred: Boolean = false, val avatarSeed: Int = 0, val createdAt: Long = 0, val updatedAt: Long = 0,
    val phones: List<BackupPhone> = emptyList(), val groups: List<String> = emptyList(),
    /** base64 JPEG */
    val photo: String? = null,
)

@Serializable
data class BackupData(val version: Int = 1, val exportedAt: Long, val groups: List<String>, val contacts: List<BackupContact>) {
    companion object {
        const val CURRENT = 1
        // Unknown keys are ignored so a newer backup of the same major version still opens.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

enum class BackupFrequency(val label: String, val days: Long) { Off("Off", 0), Daily("Daily", 1), Weekly("Weekly", 7) }

/** The derived key, wrapped by the Android Keystore, plus the parameters that go in every backup's header. */
@Serializable
data class BackupKey(val mode: Int, val kdf: Int, val iterations: Int, val salt: String, val wrapped: String)

data class BackupSettings(
    /** Persisted SAF tree URI, empty when none is chosen. */
    val folder: String = "",
    val frequency: BackupFrequency = BackupFrequency.Off,
    val key: BackupKey? = null,
    val lastOk: Long = 0,
    val lastError: String = "",
)
