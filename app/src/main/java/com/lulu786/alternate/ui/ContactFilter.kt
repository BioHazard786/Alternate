package com.lulu786.alternate.ui

import androidx.compose.runtime.Immutable
import com.lulu786.alternate.data.ContactRow

/**
 * Contacts-tab filter. Values inside one facet are OR-ed ("India or UAE"), facets are AND-ed
 * ("in Family" and "in India" and "has a photo"). Empty facet = no constraint.
 */
@Immutable
data class ContactFilter(
    val favorites: Boolean = false,
    /** true: only with a photo, false: only without, null: either. */
    val photo: Boolean? = null,
    val groups: Set<Long> = emptySet(),
    /** ISO codes of the contact's main number. */
    val countries: Set<String> = emptySet(),
    val locations: Set<String> = emptySet(),
    val work: Set<String> = emptySet(),
) {
    val active get() = this != ContactFilter()

    fun matches(r: ContactRow, groupsOf: Map<Long, Set<Long>>): Boolean =
        (!favorites || r.starred) &&
            (photo == null || r.photoPath.isNotEmpty() == photo) &&
            (groups.isEmpty() || groupsOf[r.id].orEmpty().any { it in groups }) &&
            (countries.isEmpty() || r.countryCode in countries) &&
            (locations.isEmpty() || r.location.trim() in locations) &&
            (work.isEmpty() || r.appointment.trim() in work)
}

/** Distinct non-blank values of [key] with how many contacts have each, most common first. */
fun facet(rows: List<ContactRow>, key: (ContactRow) -> String?): List<Pair<String, Int>> =
    rows.mapNotNull { key(it)?.trim()?.takeIf(String::isNotEmpty) }.groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

enum class ContactSort(val label: String) {
    NameAsc("Name A–Z"), NameDesc("Name Z–A"), Newest("Date added, newest"), Oldest("Date added, oldest"),
    Modified("Last modified"), Phone("Phone number"), Country("Country");

    /** Name sorts keep the lettered sections and fast scroller; the rest are one flat list. */
    val lettered get() = this == NameAsc || this == NameDesc
}
