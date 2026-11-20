package com.lulu786.alternate

import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.ui.ContactFilter
import com.lulu786.alternate.ui.buildModel
import com.lulu786.alternate.ui.facet
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterTests {
    private fun row(id: Long, name: String, cc: String? = "IN", loc: String = "", job: String = "", photo: String = "", star: Boolean = false) =
        ContactRow(id, name, "", "", job, loc, photo, star, 0, name.lowercase(), "91$id", cc, "Mobile")

    private val rows = listOf(
        row(1, "Asha", loc = "Delhi", star = true),
        row(2, "Bilal", cc = "AE", loc = "Dubai", job = "Engineer", photo = "p.jpg"),
        row(3, "Chetan", loc = "Delhi ", job = "Engineer"),
        row(4, "Dev", cc = null),
    )
    private val groupsOf = mapOf(1L to setOf(10L), 3L to setOf(10L, 11L))
    private fun ids(f: ContactFilter) = rows.filter { f.matches(it, groupsOf) }.map { it.id }

    @Test fun orWithinAFacetAndAcrossFacets() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(ContactFilter()))
        assertEquals(listOf(1L, 2L, 3L), ids(ContactFilter(countries = setOf("IN", "AE")))) // OR within; null code never matches
        assertEquals(listOf(1L, 3L), ids(ContactFilter(locations = setOf("Delhi")))) // trimmed
        assertEquals(listOf(3L), ids(ContactFilter(groups = setOf(10L), work = setOf("Engineer")))) // AND across
        assertEquals(listOf(2L), ids(ContactFilter(photo = true)))
        assertEquals(listOf(1L), ids(ContactFilter(favorites = true)))
        assertEquals(listOf(3L), ids(ContactFilter(groups = setOf(11L))))
    }

    @Test fun facetCountsMostCommonFirst() {
        assertEquals(listOf("Delhi" to 2, "Dubai" to 1), facet(rows) { it.location })
        assertEquals(listOf("IN" to 2, "AE" to 1), facet(rows) { it.countryCode })
    }

    @Test fun fastScrollerIndexesFollowTheLeadingItems() {
        // No count line any more: favorites header (0) + grid (1), first letter header at 2.
        val plain = buildModel(rows, ContactFilter(), groupsOf)
        assertEquals(listOf('★' to 0, 'A' to 2, 'B' to 4, 'C' to 6, 'D' to 8), plain.letterStarts.map { (c, i) -> (if (c.isLetter()) c else '★') to i })
        // Filtered: chip row (0), no favorites grid, first letter at 1.
        val filtered = buildModel(rows, ContactFilter(locations = setOf("Delhi")), groupsOf)
        assertEquals(listOf('A' to 1, 'C' to 3), filtered.letterStarts)
        assertEquals(2, filtered.total); assertEquals(4, filtered.all)
    }
}
