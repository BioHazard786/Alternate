package com.lulu786.alternate

import com.lulu786.alternate.callerid.PhoneNormalizer
import com.lulu786.alternate.data.MmkvParser
import com.lulu786.alternate.data.Prefs
import com.lulu786.alternate.ui.components.AvatarSpec
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
        // +1: area code decides, whatever the SIM says
        assertEquals("US", Countries.split("12025550100", "CA")!!.first.code)
        assertEquals("US", Countries.split("12025550100", null)!!.first.code)
        assertEquals("CA", Countries.split("14165550100", "US")!!.first.code)
        assertEquals("US", Countries.split("1202", null)!!.first.code)
        // territories with several area codes: one entry each, national part excludes the area code
        assertEquals("PR", Countries.split("17875551234", null)!!.first.code)
        assertEquals("1787", Countries.split("17875551234", null)!!.first.dialCode)
        assertEquals("1939", Countries.split("19395551234", null)!!.first.dialCode)
        assertEquals("DO", Countries.split("18295551234", null)!!.first.code)
        assertEquals("5551234", Countries.split("18295551234", null)!!.second)
        assertEquals("JM", Countries.split("16585551234", null)!!.first.code)
        // a stored number finds its own entry again (the edit form used to take the last one: 787 turned into 939)
        assertEquals("1787", Countries.forNumber("PR", "17875551234")!!.dialCode)
        assertEquals("1849", Countries.forNumber("DO", "18495551234")!!.dialCode)
    }

    @Test fun sharedDialCodesAreToldApartByNationalNumber() {
        fun c(d: String, sim: String? = null) = Countries.split(d, sim)!!.first.code
        assertEquals("KZ", c("77012345678")); assertEquals("RU", c("74951234567")); assertEquals("RU", c("74951234567", "KZ"))
        assertEquals("VA", c("390669812345")); assertEquals("IT", c("390612345678"))
        assertEquals("GB", c("442071234567")); assertEquals("GG", c("441481123456")); assertEquals("JE", c("441534123456")); assertEquals("IM", c("441624123456"))
        assertEquals("GB", c("447700900123")); assertEquals("JE", c("447700900123", "JE")) // SIM settles what no rule covers
        assertEquals("AX", c("35818123456")); assertEquals("FI", c("358401234567"))
        assertEquals("SJ", c("4779123456")); assertEquals("NO", c("4722123456"))
        assertEquals("CC", c("61891621234")); assertEquals("CX", c("61891641234")); assertEquals("AU", c("61412345678"))
        assertEquals("EH", c("212528891234")); assertEquals("MA", c("212612345678"))
        assertEquals("YT", c("262269601234")); assertEquals("RE", c("262692123456"))
        assertEquals("TA", c("29084123")); assertEquals("SH", c("29022123"))
        assertEquals("BQ", c("59931812345")); assertEquals("CW", c("59990123456"))
    }

    @Test fun countryListHasNoDuplicatesAndIsSorted() {
        val all = Countries.all
        assertEquals(all.size, all.map { it.code to it.dialCode }.toSet().size)
        assertEquals(all.map { it.name }, all.map { it.name }.sortedBy { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase() })
        for (code in listOf("HK", "MO", "CD", "PS", "XK", "GI", "AW", "PR")) kotlin.test.assertNotNull(Countries.byCode(code), code)
        assertEquals("852", Countries.split("85212345678", null)!!.first.dialCode)
        assertEquals("CD", Countries.split("243812345678", null)!!.first.code)
    }

    @Test fun segmentedShapes() {
        val one = segmentedShape(0, 1); val first = segmentedShape(0, 3); val mid = segmentedShape(1, 3); val last = segmentedShape(2, 3)
        assertEquals(one, segmentedShape(0, 1))
        assertEquals(segmentedShape(0, 2).bottomEnd, first.bottomEnd)
        assertEquals(segmentedShape(1, 2).topStart, last.topStart)
        assertNotEquals(first, mid); assertNotEquals(mid, last); assertNotEquals(one, first)
    }
}

class SearchTests {
    private fun doc(id: Long, name: String, notes: String = "", phones: String = "", groups: String = "") =
        com.lulu786.alternate.data.SearchDoc(id, name, "", "", "", "", "", "", "", notes, phones, groups)

    private val index = com.lulu786.alternate.util.SearchIndex(listOf(
        doc(1, "Ravi Kumar", notes = "Installs CCTV cameras for shops", phones = "919876543210 9876543210 Mob II"),
        doc(2, "Aarav Shah", groups = "Family"),
    ))

    @Test fun matchesNotesWithHighlight() {
        val r = index.search("cctv").single()
        assertEquals("Notes", r.matchedField)
        assertEquals("cctv", r.snippet.substring(r.snippetRanges[0].first, r.snippetRanges[0].last + 1).lowercase())
    }

    @Test fun multiWordAndAcrossFields() {
        assertEquals(listOf(1L), index.search("ravi cctv").map { it.id })
        assertEquals(emptyList(), index.search("ravi family"))
    }

    @Test fun matchesNumbersLabelsAndGroups() {
        assertEquals(listOf(1L), index.search("98765").map { it.id })
        assertEquals(listOf(1L), index.search("mob ii").map { it.id })
        assertEquals(listOf(2L), index.search("family").map { it.id })
        assertEquals("Group", index.search("family").single().matchedField)
    }

    @Test fun nameMatchReportsNameRanges() {
        val r = index.search("aar").single()
        assertNull(r.matchedField); assertEquals(listOf(0..2), r.nameRanges)
    }
}

class VcfTests {
    @Test fun quotedPrintableEmojiNamesDecode() {
        val vcf = "BEGIN:VCARD\nVERSION:2.1\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:;=5A=65=62=61=E2=99=A5=EF=B8=8F;;;\n" +
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=5A=65=62=61=E2=99=A5=EF=B8=8F\nTEL;CELL:7820054917\nEND:VCARD\n" +
            "BEGIN:VCARD\nVERSION:2.1\nN;ENCODING=QUOTED-PRINTABLE:;Zaid =F0=9F=92=AB=\n=F0=9F=92=AB;;;\nTEL;CELL:6396634236\nEND:VCARD\n"
        val names = com.lulu786.alternate.util.Vcf.parse(vcf, "IN").map { it.contact.name }
        assertEquals(listOf("Zeba\u2665\uFE0F", "Zaid \uD83D\uDCAB\uD83D\uDCAB"), names)
    }

    private fun contact(name: String) = com.lulu786.alternate.data.ContactEntity(
        name = name, prefix = "Dr", suffix = "Jr", nickname = "Jim", appointment = "Engineer", location = "Dehradun",
        email = "j@x.com", website = "x.com", birthday = "--03-12", notes = "line1\nline2, with; punctuation", photoPath = "",
    )

    @Test fun roundTripKeepsNumbersLabelsGroupsAndFields() {
        val phones = listOf(
            com.lulu786.alternate.data.PhoneEntity(contactId = 1, fullPhoneNumber = "919876543210", phoneNumber = "9876543210", countryCode = "IN", label = "Mobile", position = 0),
            com.lulu786.alternate.data.PhoneEntity(contactId = 1, fullPhoneNumber = "919812345678", phoneNumber = "9812345678", countryCode = "IN", label = "Mob II", position = 1),
        )
        val text = com.lulu786.alternate.util.Vcf.export(listOf(com.lulu786.alternate.data.ExportContact(contact("Jimmy Carter"), phones, listOf("Work", "Family, close"))))
        assertEquals(true, text.contains("item1.X-ABLabel:Mob II"))
        val back = com.lulu786.alternate.util.Vcf.parse(text, "IN").single()
        assertEquals("Jimmy Carter", back.contact.name)
        assertEquals("Dr", back.contact.prefix); assertEquals("Jr", back.contact.suffix); assertEquals("Jim", back.contact.nickname)
        assertEquals("--03-12", back.contact.birthday)
        assertEquals("line1\nline2, with; punctuation", back.contact.notes)
        assertEquals(listOf("Mobile", "Mob II"), back.phones.map { it.label })
        assertEquals(listOf("919876543210", "919812345678"), back.phones.map { it.fullPhoneNumber })
        assertEquals(listOf("Work", "Family, close"), back.groups)
    }

    @Test fun importSplitsDialCodeAndDefaultsToSimCountry() {
        val v = "BEGIN:VCARD\nVERSION:3.0\nFN:A B\nTEL;TYPE=WORK:+44 7400 123456\nTEL:09876543210\nCATEGORIES:Family\nEND:VCARD"
        val c = com.lulu786.alternate.util.Vcf.parse(v, "IN").single()
        assertEquals("GB", c.phones[0].countryCode); assertEquals("447400123456", c.phones[0].fullPhoneNumber); assertEquals("Work", c.phones[0].label)
        assertEquals("919876543210", c.phones[1].fullPhoneNumber)
    }

    @Test fun skipsCardsWithoutNumbers() {
        assertEquals(0, com.lulu786.alternate.util.Vcf.parse("BEGIN:VCARD\nFN:Nobody\nEND:VCARD", "IN").size)
    }
}

class AvatarTests {
    @Test fun seedMapsToShapeColorPattern() {
        // Pinned values: changing the mapping would change every existing contact's avatar.
        fun t(seed: Int) = Triple(AvatarSpec.shape(seed), AvatarSpec.color(seed), AvatarSpec.pattern(seed))
        assertEquals(Triple(0, 0, 0), t(0))
        assertEquals(Triple(5, 0, 0), t(5))
        assertEquals(Triple(0, 1, 0), t(12))
        assertEquals(Triple(0, 0, 1), t(120))
        assertEquals(Triple(11, 9, 3), t(12 * 10 * 3 + 12 * 9 + 11))
        assertEquals(Triple(7, 0, 1), t(Int.MAX_VALUE))
    }

    @Test fun listsKeepTheirOrder() {
        assertEquals(listOf(0xFF0090FF, 0xFF00A2C7, 0xFF12A594, 0xFF30A46C, 0xFFBDEE63, 0xFFFFC53D, 0xFFF76B15, 0xFFE5484D, 0xFFD6409F, 0xFF8E4EC6).map { it.toInt() }, AvatarSpec.hues.toList())
        assertEquals(listOf("Cookie4Sided", "Cookie6Sided", "Cookie7Sided", "Cookie9Sided", "Cookie12Sided", "Clover4Leaf", "Clover8Leaf", "Sunny", "VerySunny", "Flower", "SoftBurst", "Pentagon"), com.lulu786.alternate.ui.components.AvatarShapes.names)
        assertEquals(AvatarSpec.SHAPES, 12); assertEquals(AvatarSpec.COLORS, 10); assertEquals(AvatarSpec.PATTERNS, 4)
    }
}

class AvatarContrastTests {
    private fun lum(c: Int): Double {
        fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c shr 16 and 0xff) + 0.7152 * ch(c shr 8 and 0xff) + 0.0722 * ch(c and 0xff)
    }
    private fun contrast(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    @Test fun letterIsReadableOnEveryHueInLightAndDark() {
        val primaries = listOf(0xFF6750A4.toInt(), 0xFF0B57D0.toInt(), 0xFF006C4C.toInt(), 0xFFB3261E.toInt())
        for (primary in primaries) for (light in listOf(true, false)) for (i in 0 until AvatarSpec.COLORS) {
            val (bg, fg) = AvatarSpec.roles(i, primary, light)
            assert(contrast(bg, fg) >= 4.5) { "hue $i light=$light primary=${primary.toString(16)} contrast=${contrast(bg, fg)}" }
        }
    }
}
