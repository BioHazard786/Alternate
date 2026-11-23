package com.lulu786.alternate.util

import com.lulu786.alternate.data.ContactEntity
import com.lulu786.alternate.data.ExportContact
import com.lulu786.alternate.data.PhoneEntity
import java.time.Instant

/** One spreadsheet row. [fields] holds only the columns the file has, so a missing column leaves the contact alone. */
class CsvRow(
    val id: Long?,
    val fields: Map<String, String>,
    /** (number as typed, label) in column order; null when the file has no phone columns. */
    val phones: List<Pair<String, String>>?,
    val groups: List<String>?,
)

/**
 * RFC 4180 CSV for editing contacts in a spreadsheet. Export writes a UTF-8 BOM so Excel shows non-ASCII names;
 * import reads columns by header name, so they can be reordered or deleted.
 * Photos are not included (VCF carries them).
 */
object Csv {
    private val editable = listOf("name", "prefix", "suffix", "nickname", "appointment", "location", "email", "website", "birthday", "notes", "starred")
    private val trueValues = setOf("yes", "true", "1", "y", "x")

    private fun cell(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    private fun date(ms: Long) = if (ms > 0) Instant.ofEpochMilli(ms).toString() else ""

    fun export(items: List<ExportContact>): String {
        val maxPhones = items.maxOfOrNull { it.phones.size } ?: 0
        val header = listOf("id") + editable + listOf("groups", "created", "updated") + (1..maxPhones).flatMap { listOf("phone $it", "phone $it label") }
        val out = StringBuilder("﻿").append(header.joinToString(",")).append("\r\n")
        for (item in items) {
            val c = item.contact
            val values = listOf(
                c.id.toString(), c.name, c.prefix, c.suffix, c.nickname, c.appointment, c.location, c.email, c.website, c.birthday, c.notes,
                if (c.starred) "yes" else "", item.groups.joinToString("; "), date(c.createdAt), date(c.updatedAt),
            ) + item.phones.sortedBy { it.position }.flatMap { listOf(intl(it.fullPhoneNumber, it.countryCode), it.label) }
            out.append(values.joinToString(",", transform = ::cell)).append("\r\n")
        }
        return out.toString()
    }

    /** Quoted values may hold commas, quotes ("") and line breaks. Rows with no non-blank cell are dropped. */
    internal fun records(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val sb = StringBuilder()
        var quoted = false; var i = 0
        fun endCell() { row.add(sb.toString()); sb.clear() }
        fun endRow() { endCell(); if (row.any { it.isNotBlank() }) rows += row; row = ArrayList() }
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' -> if (text.getOrNull(i + 1) == '"') { sb.append('"'); i++ } else quoted = false
                quoted -> sb.append(ch)
                ch == '"' && sb.isEmpty() -> quoted = true
                ch == ',' -> endCell()
                ch == '\n' -> endRow()
                ch == '\r' -> if (text.getOrNull(i + 1) != '\n') endRow()
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    fun parse(text: String): List<CsvRow> {
        val recs = records(text.removePrefix("﻿"))
        if (recs.isEmpty()) return emptyList()
        val head = recs[0].map { it.trim().lowercase() }
        val col = head.withIndex().associate { (i, h) -> h to i }
        val phoneCols = head.mapNotNull { Regex("""phone (\d+)""").matchEntire(it)?.groupValues?.get(1)?.toInt() }.sorted()
        val hasGroups = "groups" in col
        return recs.drop(1).map { r ->
            fun at(name: String) = col[name]?.let { r.getOrNull(it) }?.trim().orEmpty()
            CsvRow(
                id = at("id").toLongOrNull(),
                fields = editable.filter { it in col }.associateWith(::at),
                phones = if (phoneCols.isEmpty()) null else phoneCols.mapNotNull { n ->
                    // A leading ' is the spreadsheet trick for keeping "+91…" as text.
                    at("phone $n").removePrefix("'").trim().takeIf(String::isNotEmpty)?.let { it to at("phone $n label").ifEmpty { "Mobile" } }
                },
                groups = if (hasGroups) at("groups").split(';').map(String::trim).filter(String::isNotEmpty) else null,
            )
        }
    }

    /** shortcut: a number a spreadsheet turned into a plain integer (lost its +) is read as a SIM-country number; upgrade by asking the user. */
    fun phone(raw: String, label: String, simIso: String?): PhoneEntity? =
        Vcf.parsePhone(raw, simIso)?.let { (country, national, full) ->
            PhoneEntity(contactId = 0, fullPhoneNumber = full, phoneNumber = national, countryCode = country, label = label)
        }

    /** [c] with every column the file had applied; columns it lacked keep their value. */
    fun apply(c: ContactEntity, f: Map<String, String>) = c.copy(
        name = f["name"] ?: c.name, prefix = f["prefix"] ?: c.prefix, suffix = f["suffix"] ?: c.suffix, nickname = f["nickname"] ?: c.nickname,
        appointment = f["appointment"] ?: c.appointment, location = f["location"] ?: c.location, email = f["email"] ?: c.email,
        website = f["website"] ?: c.website, notes = f["notes"] ?: c.notes,
        birthday = f["birthday"]?.takeIf { it.isEmpty() || Vcf.validBirthday(it) } ?: c.birthday,
        starred = f["starred"]?.let { it.lowercase() in trueValues } ?: c.starred,
    )
}
