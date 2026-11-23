package com.lulu786.alternate.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup file: header, then AES-256-GCM ciphertext. The header (everything before the IV) is authenticated, so
 * a tampered mode, salt or iteration count fails to decrypt instead of silently misbehaving.
 *
 *   "ALTB" | version 1 | mode 1 | kdf 1 | iterations 4 | salt 16 | iv 12 | ciphertext+tag
 *
 * The key comes from a secret (a chosen password, or a generated recovery key) through PBKDF2. Scheduled backups cannot
 * ask for the secret, so the app keeps the derived key wrapped by the Android Keystore ([KeyWrap]); only restoring needs the secret.
 */
object BackupCrypto {
    const val MODE_PASSWORD = 0
    const val MODE_RECOVERY = 1
    private const val KDF_SHA256 = 1
    private const val KDF_SHA1 = 2 // PBKDF2-HMAC-SHA256 only exists from API 26; minSdk is 24
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte())
    private const val VERSION: Byte = 1
    private const val AAD_LEN = 27
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = AAD_LEN + IV_LEN

    class Params(val mode: Int, val kdf: Int, val iterations: Int, val salt: ByteArray)

    fun newParams(mode: Int, sha256: Boolean = Build.VERSION.SDK_INT >= 26): Params {
        val kdf = if (sha256) KDF_SHA256 else KDF_SHA1
        // A recovery key already has 160 bits of randomness, so stretching it buys nothing.
        val iterations = if (mode == MODE_RECOVERY) 10_000 else if (sha256) 600_000 else 1_300_000
        return Params(mode, kdf, iterations, ByteArray(SALT_LEN).also(SecureRandom()::nextBytes))
    }

    /** Recovery keys are typed back by hand, so case, dashes and spaces do not matter. */
    fun normalize(mode: Int, secret: String) =
        if (mode == MODE_RECOVERY) secret.uppercase().filter { it.isLetterOrDigit() } else secret

    fun deriveKey(secret: String, p: Params): ByteArray {
        val algo = if (p.kdf == KDF_SHA256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val spec = PBEKeySpec(normalize(p.mode, secret).toCharArray(), p.salt, p.iterations, 256)
        return try { SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun encrypt(plain: ByteArray, key: ByteArray, p: Params): ByteArray {
        val out = ByteBuffer.allocate(HEADER_LEN)
        out.put(MAGIC).put(VERSION).put(p.mode.toByte()).put(p.kdf.toByte()).putInt(p.iterations).put(p.salt)
        val iv = ByteArray(IV_LEN).also(SecureRandom()::nextBytes)
        out.put(iv)
        val header = out.array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(header, 0, AAD_LEN)
        return header + c.doFinal(plain)
    }

    /** The header's parameters, or null when [file] is not one of our backups. */
    fun peek(file: ByteArray): Params? {
        if (file.size < HEADER_LEN + 16 || !file.copyOf(4).contentEquals(MAGIC) || file[4] != VERSION) return null
        val b = ByteBuffer.wrap(file, 5, AAD_LEN - 5)
        val mode = b.get().toInt(); val kdf = b.get().toInt(); val iterations = b.getInt()
        if (mode !in 0..1 || kdf !in 1..2 || iterations !in 1..10_000_000) return null
        return Params(mode, kdf, iterations, file.copyOfRange(11, 11 + SALT_LEN))
    }

    /** Throws [java.security.GeneralSecurityException] for a wrong secret or a damaged file. */
    fun decrypt(file: ByteArray, key: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, file.copyOfRange(AAD_LEN, HEADER_LEN)))
        c.updateAAD(file, 0, AAD_LEN)
        return c.doFinal(file, HEADER_LEN, file.size - HEADER_LEN)
    }

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** 160 random bits as eight dash-separated groups, e.g. "K3F2-9QXA-…". */
    fun newRecoveryKey(): String {
        val bytes = ByteArray(20).also(SecureRandom()::nextBytes)
        val sb = StringBuilder(); var buf = 0; var bits = 0
        for (x in bytes) {
            buf = (buf shl 8) or (x.toInt() and 0xFF); bits += 8
            while (bits >= 5) { sb.append(B32[(buf shr (bits - 5)) and 31]); bits -= 5 }
        }
        return sb.chunked(4).joinToString("-")
    }
}

/** Keeps the derived key unreadable outside this app: AES-GCM under a non-exportable Android Keystore key. */
object KeyWrap {
    private const val ALIAS = "alternate_backup_wrap"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    fun wrap(raw: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(raw)
    }

    /** Throws when the Keystore key is gone (new phone, cleared lock screen); the caller asks the user to set backups up again. */
    fun unwrap(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOf(12))) }
        return c.doFinal(blob, 12, blob.size - 12)
    }
}
