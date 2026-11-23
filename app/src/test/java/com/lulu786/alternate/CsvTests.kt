package com.lulu786.alternate

import com.lulu786.alternate.data.ContactEntity
import com.lulu786.alternate.data.ExportContact
import com.lulu786.alternate.data.PhoneEntity
import com.lulu786.alternate.util.Csv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvTests {
    private fun phone(full: String, national: String, label: String, pos: Int) =
        PhoneEntity(contactId = 7, fullPhoneNumber = full, phoneNumber = national, countryCode = "IN", label = label, position = pos)

    @Test fun roundTripKeepsFieldsPhonesAndGroupsThroughQuotesCommasAndNewlines() {
        val c = ContactEntity(id = 7, name = "Jimmy \"JC\" Carter", notes = "line1\nline2, with; punctuation", birthday = "--03-12", starred = true, createdAt = 1_700_000_000_000)
        val text = Csv.export(listOf(ExportContact(c, listOf(phone("919876543210", "9876543210", "Mobile", 0), phone("919812345678", "9812345678", "Mob II", 1)), listOf("Work", "Family, close"))))
        assertTrue(text.startsWith("﻿id,name,"))
        assertTrue("2023-11-14T22:13:20Z" in text)

        val row = Csv.parse(text).single()
        assertEquals(7L, row.id)
        assertEquals("Jimmy \"JC\" Carter", row.fields["name"])
        assertEquals("line1\nline2, with; punctuation", row.fields["notes"])
        assertEquals(listOf("+919876543210" to "Mobile", "+919812345678" to "Mob II"), row.phones)
        assertEquals(listOf("Work", "Family, close"), row.groups) // groups are "; "-separated, so a comma inside a name survives
        assertEquals(c, Csv.apply(ContactEntity(id = 7, name = "x", createdAt = c.createdAt), row.fields).copy(avatarSeed = 0)) // every editable field survives
    }

    @Test fun readsColumnsByNameSoMissingOnesKeepTheirValue() {
        val rows = Csv.parse("﻿phone 1,Name\r\n'+919876543210,Asha\r\n\r\n,\r\n")
        val r = rows.single()
        assertNull(r.id); assertNull(r.groups)
        assertEquals(mapOf("name" to "Asha"), r.fields) // no email column, so email is left alone
        assertEquals(listOf("+919876543210" to "Mobile"), r.phones)
        val existing = ContactEntity(id = 1, name = "Old", email = "keep@x.com")
        assertEquals(ContactEntity(id = 1, name = "Asha", email = "keep@x.com"), Csv.apply(existing, r.fields))
    }

    @Test fun emptyCellClearsAndBadBirthdayIsIgnored() {
        val r = Csv.parse("name,email,birthday,starred\nAsha,,31/12/1990,no").single()
        val out = Csv.apply(ContactEntity(name = "Old", email = "a@x.com", birthday = "1990-12-31", starred = true), r.fields)
        assertEquals("", out.email) // present but empty clears
        assertEquals("1990-12-31", out.birthday) // not a vCard date, so the old value stays
        assertEquals(false, out.starred)
    }
}
