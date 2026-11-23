package com.lulu786.alternate

import com.lulu786.alternate.data.BackupContact
import com.lulu786.alternate.data.BackupData
import com.lulu786.alternate.data.BackupPhone
import com.lulu786.alternate.util.BackupCrypto
import java.security.GeneralSecurityException
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTests {
    // Few iterations keep the tests fast; the real counts come from BackupCrypto.newParams.
    private fun params(mode: Int = BackupCrypto.MODE_PASSWORD, kdf: Int = 1) = BackupCrypto.Params(mode, kdf, 1_000, ByteArray(16) { it.toByte() })

    @Test fun roundTripWithEitherHash() {
        for (kdf in 1..2) {
            val p = params(kdf = kdf)
            val key = BackupCrypto.deriveKey("correct horse", p)
            val file = BackupCrypto.encrypt("hello contacts".toByteArray(), key, p)
            assertEquals("hello contacts", String(BackupCrypto.decrypt(file, BackupCrypto.deriveKey("correct horse", BackupCrypto.peek(file)!!))))
        }
    }

    @Test fun wrongSecretOrDamagedFileFails() {
        val p = params()
        val file = BackupCrypto.encrypt("secret".toByteArray(), BackupCrypto.deriveKey("right", p), p)
        assertFailsWith<GeneralSecurityException> { BackupCrypto.decrypt(file, BackupCrypto.deriveKey("wrong", p)) }
        val right = BackupCrypto.deriveKey("right", p)
        assertFailsWith<GeneralSecurityException> { BackupCrypto.decrypt(file.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }, right) } // ciphertext
        assertFailsWith<GeneralSecurityException> { BackupCrypto.decrypt(file.copyOf().also { it[8] = (it[8] + 1).toByte() }, right) } // header (iteration count)
    }

    @Test fun headerIsReadBackAndGarbageIsRefused() {
        val p = params(BackupCrypto.MODE_RECOVERY, 2)
        val file = BackupCrypto.encrypt(ByteArray(10), BackupCrypto.deriveKey("x", p), p)
        val back = BackupCrypto.peek(file)!!
        assertEquals(BackupCrypto.MODE_RECOVERY, back.mode); assertEquals(2, back.kdf); assertEquals(1_000, back.iterations)
        assertContentEquals(p.salt, back.salt)
        assertNull(BackupCrypto.peek("BEGIN:VCARD\nFN:Not a backup\nEND:VCARD".toByteArray()))
        assertNull(BackupCrypto.peek(ByteArray(3)))
    }

    @Test fun everyFileGetsItsOwnIv() {
        val p = params(); val key = BackupCrypto.deriveKey("x", p)
        assertNotEquals(BackupCrypto.encrypt(ByteArray(4), key, p).toList(), BackupCrypto.encrypt(ByteArray(4), key, p).toList())
    }

    @Test fun recoveryKeysAreEightGroupsAndForgiveTyping() {
        val k = BackupCrypto.newRecoveryKey()
        assertTrue(Regex("([A-Z2-7]{4}-){7}[A-Z2-7]{4}").matches(k), k)
        assertNotEquals(k, BackupCrypto.newRecoveryKey())
        val p = params(BackupCrypto.MODE_RECOVERY)
        assertContentEquals(BackupCrypto.deriveKey(k, p), BackupCrypto.deriveKey(" " + k.lowercase().replace("-", " ") + " ", p))
    }

    @Test fun passwordsAreNotNormalised() {
        val p = params()
        assertNotEquals(BackupCrypto.deriveKey("Secret", p).toList(), BackupCrypto.deriveKey("secret", p).toList())
    }

    @Test fun backupDataSurvivesJsonAndIgnoresUnknownKeys() {
        val data = BackupData(exportedAt = 5, groups = listOf("Family"), contacts = listOf(
            BackupContact("Asha", notes = "line1\nline2, \"quoted\"", starred = true, avatarSeed = 42, createdAt = 1, updatedAt = 2,
                phones = listOf(BackupPhone("919876543210", "9876543210", "IN", "Mobile")), groups = listOf("Family"), photo = "AAAA"),
        ))
        assertEquals(data, BackupData.json.decodeFromString<BackupData>(BackupData.json.encodeToString(data)))
        val newer = BackupData.json.encodeToString(data).replaceFirst("{", "{\"addedLater\":1,")
        assertEquals(data, BackupData.json.decodeFromString<BackupData>(newer))
    }
}
