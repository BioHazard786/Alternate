package com.lulu786.alternate.util

import com.lulu786.alternate.callerid.PhoneNormalizer
import com.lulu786.alternate.data.ContactEntity
import com.lulu786.alternate.data.ExportContact
import com.lulu786.alternate.data.ImportedContact
import com.lulu786.alternate.data.PhoneEntity

/** vCard 3.0 export, tolerant 2.1/3.0/4.0 import. Ported from lib/vcf-utils.ts (as of the #38 import fixes). */
object Vcf {
    private val standardTypes = mapOf("Mobile" to "CELL", "Work" to "WORK", "Home" to "HOME")

    private fun esc(v: String) = v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n").replace("\r", "")
    private fun unesc(v: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (val n = v[i + 1]) { 'n', 'N' -> sb.append('\n'); else -> sb.append(n) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    /** [photoBase64] looks up a contact's photo by file name; export passes PhotoStore::readBase64. */
    fun export(items: List<ExportContact>, photoBase64: (String) -> String? = { null }): String =
        items.joinToString("\r\n\r\n") { card(it, photoBase64) }

    private fun card(item: ExportContact, photoBase64: (String) -> String?): String {
        val c = item.contact
        val lines = ArrayList<String>()
        lines += "BEGIN:VCARD"; lines += "VERSION:3.0"
        lines += "N:;${esc(c.name)};;${esc(c.prefix)};${esc(c.suffix)}"
        lines += "FN:${esc(displayName(c.prefix, c.name, c.suffix))}"
        if (c.nickname.isNotEmpty()) lines += "NICKNAME:${esc(c.nickname)}"
        var custom = 0
        for (p in item.phones.sortedBy { it.position }) {
            val type = standardTypes[p.label]
            if (type != null) lines += "TEL;TYPE=$type:${intl(p.fullPhoneNumber, p.countryCode)}"
            else { custom++; lines += "item$custom.TEL:${intl(p.fullPhoneNumber, p.countryCode)}"; lines += "item$custom.X-ABLabel:${esc(p.label)}" }
        }
        if (c.email.isNotEmpty()) lines += "EMAIL;TYPE=INTERNET:${esc(c.email)}"
        if (c.location.isNotEmpty()) lines += "ADR;TYPE=HOME:;;;;${esc(c.location)};;"
        if (c.appointment.isNotEmpty()) lines += "TITLE:${esc(c.appointment)}"
        if (c.website.isNotEmpty()) lines += "URL:${esc(c.website)}"
        if (item.groups.isNotEmpty()) lines += "CATEGORIES:${item.groups.joinToString(",") { esc(it) }}"
        if (c.notes.isNotEmpty()) lines += "NOTE:${esc(c.notes)}"
        if (c.birthday.isNotEmpty()) lines += "BDAY:${esc(c.birthday)}"
        if (c.photoPath.isNotEmpty()) photoBase64(c.photoPath)?.let { lines += "PHOTO;ENCODING=b;TYPE=JPEG:$it" }
        lines += "END:VCARD"
        return lines.joinToString("\r\n")
    }

    private val bdayFull = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val bdayPartial = Regex("""^--\d{2}-\d{2}$""")
    internal fun validBirthday(v: String) = bdayFull.matches(v) || bdayPartial.matches(v)

    /** Splits "a,b\,c" on unescaped commas. */
    private fun splitList(v: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var i = 0
        while (i < v.length) {
            if (v[i] == '\\' && i + 1 < v.length) { sb.append(v[i]).append(v[i + 1]); i += 2 }
            else if (v[i] == ',') { out += sb.toString(); sb.clear(); i++ } else { sb.append(v[i]); i++ }
        }
        out += sb.toString()
        return out.map { unesc(it).trim() }.filter { it.isNotEmpty() }
    }

    private fun splitFields(v: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var i = 0
        while (i < v.length) {
            if (v[i] == '\\' && i + 1 < v.length) { sb.append(v[i]).append(v[i + 1]); i += 2 }
            else if (v[i] == ';') { out += sb.toString(); sb.clear(); i++ } else { sb.append(v[i]); i++ }
        }
        out += sb.toString()
        return out.map(::unesc)
    }

    fun parse(text: String, simIso: String?): List<ImportedContact> {
        val unfolded = joinQpSoftBreaks(text.replace(Regex("\r\n?"), "\n").replace(Regex("\n[ \t]"), ""))
        return unfolded.split("BEGIN:VCARD", ignoreCase = true).mapNotNull { parseCard(it, simIso) }
    }

    /** Quoted-printable lines end in "=" when they continue on the next line. Only touches QP lines so base64 padding is safe. */
    private fun joinQpSoftBreaks(text: String): String {
        val out = StringBuilder(); val lines = text.split("\n"); var i = 0
        while (i < lines.size) {
            var line = lines[i]
            if (line.substringBefore(':').uppercase().contains("QUOTED-PRINTABLE"))
                while (line.endsWith("=") && i + 1 < lines.size) { line = line.dropLast(1) + lines[++i] }
            out.append(line); if (i < lines.size - 1) out.append('\n'); i++
        }
        return out.toString()
    }

    /** Decodes =XX bytes (text between them is already ASCII) using the card's CHARSET, default UTF-8. */
    private fun decodeQp(v: String, charset: String?): String {
        val bytes = java.io.ByteArrayOutputStream(); var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '=' && i + 2 < v.length && v.substring(i + 1, i + 3).all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
                bytes.write(v.substring(i + 1, i + 3).toInt(16)); i += 3
            } else { bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
        }
        val cs = runCatching { charset?.let { java.nio.charset.Charset.forName(it) } }.getOrNull() ?: Charsets.UTF_8
        return String(bytes.toByteArray(), cs)
    }

    private class RawPhone(val group: String?, val value: String, val types: Set<String>)

    private fun parseCard(card: String, simIso: String?): ImportedContact? {
        if (card.isBlank()) return null
        var name = ""; var prefix = ""; var suffix = ""; var nickname = ""; var email = ""; var location = ""
        var title = ""; var org = ""; var website = ""; var notes = ""; var birthday = ""; var photo: String? = null
        val groups = LinkedHashSet<String>()
        val rawPhones = ArrayList<RawPhone>()
        val labels = HashMap<String, String>()

        for (line in card.split("\n")) {
            val colon = line.indexOf(':'); if (colon < 0) continue
            val head = line.substring(0, colon)
            val parts = head.split(';')
            val rawValue = line.substring(colon + 1).let { v ->
                if (parts.any { it.uppercase().contains("QUOTED-PRINTABLE") }) decodeQp(v, parts.firstOrNull { it.uppercase().startsWith("CHARSET=") }?.substringAfter('=')) else v
            }
            val propFull = parts[0]
            val group = propFull.substringBefore('.', "").ifEmpty { null }
            val prop = propFull.substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            val value = unesc(rawValue)

            when {
                prop == "N" -> {
                    val f = splitFields(rawValue)
                    prefix = f.getOrElse(3) { "" }; suffix = f.getOrElse(4) { "" }
                    val joined = listOf(f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(0) { "" }).filter { it.isNotEmpty() }.joinToString(" ")
                    if (joined.isNotEmpty()) name = joined
                }
                prop == "FN" -> if (name.isEmpty()) name = value
                prop == "TEL" -> rawPhones += RawPhone(group, value, params.flatMap { it.removePrefix("TYPE=").split(',') }.toSet())
                prop == "X-ABLABEL" && group != null -> labels[group] = cleanLabel(value)
                prop == "EMAIL" -> if (email.isEmpty() || params.any { "PREF" in it }) email = value
                prop == "ADR" -> splitFields(rawValue).let { location = it.getOrElse(4) { "" }.ifEmpty { it.getOrElse(3) { "" } }.ifEmpty { location } }
                prop == "TITLE" -> title = value
                prop == "URL" -> website = value
                prop == "CATEGORIES" -> groups += splitList(rawValue)
                prop == "ORG" -> org = splitFields(rawValue).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                prop == "NOTE" -> notes = value
                prop == "NICKNAME" -> nickname = value
                prop == "BDAY" -> if (validBirthday(value)) birthday = value
                prop == "X-ANDROID-CUSTOM" && value.startsWith("vnd.android.cursor.item/nickname;") -> nickname = value.split(";").getOrElse(1) { "" }
                prop == "PHOTO" && params.any { it.contains("ENCODING=B") || it.contains("BASE64") || it == "B" } ->
                    photo = rawValue.filterNot(Char::isWhitespace)
            }
        }

        val phones = ArrayList<PhoneEntity>()
        for (r in rawPhones) {
            val label = r.group?.let { labels[it] } ?: typeLabel(r.types)
            parsePhone(r.value, simIso)?.let { (country, national, full) ->
                if (phones.none { it.fullPhoneNumber == full }) phones += PhoneEntity(contactId = 0, fullPhoneNumber = full, phoneNumber = national, countryCode = country, label = label, position = phones.size)
            }
        }
        if (name.isEmpty() || phones.isEmpty()) return null
        val contact = ContactEntity(
            name = name, prefix = prefix, suffix = suffix, nickname = nickname, appointment = title.ifEmpty { org }, location = location,
            email = email, website = website, birthday = birthday, notes = notes, sortKey = sortKeyOf(name),
        )
        return ImportedContact(contact, phones, groups.toList(), photo)
    }

    private fun typeLabel(types: Set<String>) = when {
        "CELL" in types -> "Mobile"; "WORK" in types -> "Work"; "HOME" in types -> "Home"; "MAIN" in types -> "Main"; else -> "Other"
    }

    /** iOS wraps built-in labels like `_$!<Mobile>!$_`. */
    private fun cleanLabel(v: String) = v.removePrefix("_\$!<").removeSuffix(">!\$_")

    /** Returns (countryIso, nationalDigits, fullDigits). Numbers without '+' are taken to be in the SIM country. */
    internal fun parsePhone(raw: String, simIso: String?): Triple<String, String, String>? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val international = raw.trim().startsWith("+") || digits.startsWith("00")
        if (international) {
            val d = PhoneNormalizer.normalize(raw, simIso)
            Countries.split(d, simIso)?.let { (c, national) -> return Triple(c.code, national, d) }
            return Triple(simIso ?: "IN", d, d)
        }
        val country = Countries.byCode(simIso ?: "IN") ?: Countries.byCode("IN")!!
        val national = PhoneNormalizer.normalize(raw, country.code)
        return Triple(country.code, national, country.dialCode + national)
    }
}
