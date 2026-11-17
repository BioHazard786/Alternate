package com.lulu786.alternate

import com.lulu786.alternate.callerid.PhoneNormalizer
import com.lulu786.alternate.data.MmkvParser
import com.lulu786.alternate.data.Prefs
import com.lulu786.alternate.ui.components.segmentedShape
import com.lulu786.alternate.util.Countries
import com.lulu786.alternate.util.sectionOf
import com.lulu786.alternate.util.sortKeyOf
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DataTests {
    private fun varint(out: ByteArrayOutputStream, v: Int) {
        var n = v
        while (n >= 0x80) { out.write((n and 0x7f) or 0x80); n = n ushr 7 }
        out.write(n)
    }

    /** Builds a file the way MMKV lays out one string entry: key, then a length-prefixed string value. */
    private fun mmkv(vararg entries: Pair<String, String?>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((k, v) in entries) {
            val kb = k.toByteArray(); varint(body, kb.size); body.write(kb)
            if (v == null) { varint(body, 0); continue }
            val vb = v.toByteArray(); val inner = ByteArrayOutputStream().also { varint(it, vb.size); it.write(vb) }.toByteArray()
            varint(body, inner.size); body.write(inner)
        }
        val b = body.toByteArray()
        val size = b.size
        return byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) + b
    }

    @Test fun mmkvParsesEntriesAndLatestWins() {
        val json = """{"state":{"themeMode":"dark"},"version":0}"""
        val file = mmkv("theme-storage" to """{"state":{"themeMode":"light"}}""", "theme-storage" to json, "gone" to "x", "gone" to null)
        val parsed = MmkvParser.parse(file)
        assertEquals(json, parsed["theme-storage"])
        assertNull(parsed["gone"])
    }

    @Test fun mmkvHandlesLongValues() {
        val big = "x".repeat(5000)
        assertEquals(big, MmkvParser.parse(mmkv("k" to big))["k"])
    }

    @Test fun pinHashMatchesReactNativeStore() {
        // expo-crypto SHA256 of pin + salt, lowercase hex.
        // Value computed independently with Python hashlib for pin "1234" + the salt below.
        assertEquals("5541ae2419bdd263b492cad79d7bc907637911ba6098c5025c461ac709e06a3f", Prefs.hashPin("1234", "3f2b8c1e-aaaa-bbbb-cccc-0123456789ab"))
        assertEquals(64, Prefs.hashPin("1234", "s").length)
        assertNotEquals(Prefs.hashPin("1234", "s"), Prefs.hashPin("1235", "s"))
    }

    @Test fun normalizer() {
        assertEquals("919876543210", PhoneNormalizer.normalize("+91 98765-43210", "IN"))
        assertEquals("9876543210", PhoneNormalizer.normalize("09876543210", "IN"))
        assertEquals("0212345678", PhoneNormalizer.normalize("0212345678", "IT")) // Italian numbers keep their zero
        assertEquals("919876543210", PhoneNormalizer.normalize("00919876543210", "IN"))
    }

    @Test fun sortAndSections() {
        assertEquals("emile", sortKeyOf("  Émile"))
        assertEquals('E', sectionOf(sortKeyOf("Émile")))
        assertEquals('#', sectionOf(sortKeyOf("123")))
    }

    @Test fun countrySplitPrefersLongestThenSim() {
        assertEquals("BS", Countries.split("12425551234", null)!!.first.code)
        assertEquals("IN", Countries.split("919876543210", null)!!.first.code)
        assertEquals("9876543210", Countries.split("919876543210", "IN")!!.second)
        assertEquals("CA", Countries.split("14165551234", "CA")!!.first.code)
    }

    @Test fun segmentedShapes() {
        val one = segmentedShape(0, 1); val first = segmentedShape(0, 3); val mid = segmentedShape(1, 3); val last = segmentedShape(2, 3)
        assertEquals(one, segmentedShape(0, 1))
        assertEquals(segmentedShape(0, 2).bottomEnd, first.bottomEnd)
        assertEquals(segmentedShape(1, 2).topStart, last.topStart)
        assertNotEquals(first, mid); assertNotEquals(mid, last); assertNotEquals(one, first)
    }
}
