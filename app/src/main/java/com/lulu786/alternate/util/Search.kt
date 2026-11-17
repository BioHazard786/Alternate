package com.lulu786.alternate.util

import com.lulu786.alternate.data.SearchDoc

data class SearchResult(
    val id: Long,
    /** Which non-name field matched ("Notes", "Phone", ...), or null when the name itself matched. */
    val matchedField: String?,
    /** Text to show under the name: the matching snippet. */
    val snippet: String,
    val snippetRanges: List<IntRange>,
    val nameRanges: List<IntRange>,
)

/**
 * In-memory search over a lowercase haystack per contact, built once per data change.
 * Matching: every whitespace-separated query word must appear (substring) somewhere in the contact.
 * shortcut: linear scan, fine well past 10k contacts; move to a Room FTS4 table if that stops being true.
 */
class SearchIndex(docs: List<SearchDoc>) {
    private class Entry(val doc: SearchDoc, val fields: List<Pair<String, String>>, val lower: List<String>, val all: String)

    private val entries = docs.map { d ->
        val fields = listOf(
            "Name" to displayName(d.prefix, d.name, d.suffix),
            "Nickname" to d.nickname,
            "Appointment" to d.appointment,
            "Location" to d.location,
            "Phone" to d.phones,
            "Email" to d.email,
            "Website" to d.website,
            "Group" to d.groupNames,
            "Notes" to d.notes,
        )
        val lower = fields.map { it.second.lowercase() }
        Entry(d, fields, lower, lower.joinToString("\n"))
    }

    fun search(query: String): List<SearchResult> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = ArrayList<SearchResult>()
        for (e in entries) {
            if (!tokens.all { it in e.all }) continue
            val nameRanges = ranges(e.lower[0], tokens)
            if (nameRanges.isNotEmpty()) {
                out += SearchResult(e.doc.id, null, "", emptyList(), nameRanges)
                continue
            }
            val i = (1 until e.fields.size).firstOrNull { idx -> tokens.any { it in e.lower[idx] } }
            if (i == null) { out += SearchResult(e.doc.id, null, "", emptyList(), emptyList()); continue }
            val (snippet, snippetRanges) = snippet(e.fields[i].second, e.lower[i], tokens)
            out += SearchResult(e.doc.id, e.fields[i].first, snippet, snippetRanges, emptyList())
        }
        return out
    }

    companion object {
        fun ranges(lower: String, tokens: List<String>): List<IntRange> {
            val out = ArrayList<IntRange>()
            for (t in tokens) {
                var from = 0
                while (true) {
                    val at = lower.indexOf(t, from)
                    if (at < 0) break
                    out += at until at + t.length
                    from = at + t.length
                }
            }
            return out.sortedBy { it.first }
        }

        /** A ~60 char window around the first hit, with ellipses; ranges are relative to the returned snippet. */
        fun snippet(original: String, lower: String, tokens: List<String>, window: Int = 24): Pair<String, List<IntRange>> {
            val first = tokens.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull() ?: 0
            val start = (first - window).coerceAtLeast(0)
            val end = (first + window * 2).coerceAtMost(original.length)
            val prefix = if (start > 0) "…" else ""
            val text = prefix + original.substring(start, end).replace('\n', ' ') + if (end < original.length) "…" else ""
            val shifted = ranges(lower.substring(start, end).replace('\n', ' '), tokens).map { (it.first + prefix.length)..(it.last + prefix.length) }
            return text to shifted
        }
    }
}
