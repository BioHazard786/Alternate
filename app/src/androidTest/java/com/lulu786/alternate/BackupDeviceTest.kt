package com.lulu786.alternate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lulu786.alternate.data.AppDatabase
import com.lulu786.alternate.data.BackupContact
import com.lulu786.alternate.data.BackupData
import com.lulu786.alternate.data.BackupPhone
import com.lulu786.alternate.data.ContactRepository
import com.lulu786.alternate.data.PhotoStore
import com.lulu786.alternate.data.Prefs
import com.lulu786.alternate.util.KeyWrap
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Runs on a real phone: the Keystore is the part that differs between manufacturers. */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun keystoreWrapsAndUnwrapsTheKey() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val wrapped = KeyWrap.wrap(key)
        assertContentEquals(key, KeyWrap.unwrap(wrapped))
        assertFailsWith<GeneralSecurityException> { KeyWrap.unwrap(wrapped.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }) }
    }

    @Test
    fun restoreKeepsAvatarStarAndDatesAndSkipsWhatExists() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = ContactRepository(db, PhotoStore(context), Prefs(context))
        val b = BackupContact(
            "Asha Verma", appointment = "Engineer", notes = "met at a meetup", starred = true, avatarSeed = 12345, createdAt = 1_000, updatedAt = 2_000,
            phones = listOf(BackupPhone("919876543210", "9876543210", "IN", "Mobile"), BackupPhone("971501234567", "501234567", "AE", "Work")), groups = listOf("Family"),
        )
        val data = BackupData(exportedAt = 9, groups = listOf("Family", "Work"), contacts = listOf(b))

        assertEquals(1, repo.restore(data))
        val c = db.dao().allContacts().single()
        assertEquals(12345, c.avatarSeed); assertEquals(true, c.starred); assertEquals(1_000, c.createdAt); assertEquals(2_000, c.updatedAt)
        assertEquals(listOf("Family", "Work"), db.dao().groups().map { it.name }) // group order survives
        assertEquals(0, repo.restore(data)) // every number is known now
        assertEquals(data.contacts, repo.backupData().contacts) // and what we back up is what we restored
        db.close()
    }
}
