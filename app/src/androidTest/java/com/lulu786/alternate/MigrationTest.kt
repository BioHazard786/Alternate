package com.lulu786.alternate

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lulu786.alternate.data.AppDatabase
import com.lulu786.alternate.data.MIGRATION_4_5
import com.lulu786.alternate.data.MIGRATION_2_3
import com.lulu786.alternate.data.MIGRATION_3_4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds a real 2.4.7-shaped v4 file with raw SQL, then lets Room run MIGRATION_4_5 and validate the result. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name = "migration-test.db"

    private fun legacyRow(db: SQLiteDatabase, full: String, national: String, cc: String, name: String, photo: String = "", labels: String = "") {
        db.insert("caller_info", null, ContentValues().apply {
            put("fullPhoneNumber", full); put("phoneNumber", national); put("countryCode", cc); put("name", name)
            put("appointment", "Engineer"); put("location", "Dehradun"); put("iosRow", "x"); put("suffix", ""); put("prefix", "")
            put("email", ""); put("notes", "installs CCTV"); put("website", ""); put("birthday", ""); put("labels", labels)
            put("nickname", ""); put("photo", photo)
        })
    }

    @Test
    fun migratesCallerInfoToContactsAndPhones() = runBlocking {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                "CREATE TABLE caller_info (fullPhoneNumber TEXT PRIMARY KEY NOT NULL, phoneNumber TEXT NOT NULL DEFAULT '', countryCode TEXT NOT NULL, name TEXT NOT NULL, appointment TEXT NOT NULL, location TEXT NOT NULL, iosRow TEXT NOT NULL, suffix TEXT NOT NULL DEFAULT '', prefix TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', website TEXT NOT NULL DEFAULT '', birthday TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', nickname TEXT NOT NULL DEFAULT '', photo TEXT NOT NULL DEFAULT '')"
            )
            legacyRow(db, "919876543210", "9876543210", "IN", "Ravi Kumar", photo = "data:image/jpeg;base64,AAAA", labels = "Work,Family")
            legacyRow(db, "919812345678", "9812345678", "IN", "Aarav Shah")
            db.version = 4
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        val dao = room.dao()
        val contacts = dao.allContacts()
        assertEquals(2, contacts.size)
        assertTrue(contacts.all { it.avatarSeed >= 0 })
        assertTrue(contacts.map { it.avatarSeed }.toSet().size == 2, "avatar seeds should differ")
        val ravi = contacts.first { it.name == "Ravi Kumar" }
        assertEquals("Work,Family", ravi.labels)
        assertTrue(ravi.legacyPhoto.startsWith("data:image"))
        val phone = dao.lookup("9876543210")!!
        assertEquals("Mobile", phone.label); assertEquals("919876543210", phone.fullPhoneNumber)
        assertEquals(ravi.id, phone.contactId)
        room.close(); context.deleteDatabase(name)
    }
}
