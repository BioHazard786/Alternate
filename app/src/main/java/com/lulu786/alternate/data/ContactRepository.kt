package com.lulu786.alternate.data

import com.lulu786.alternate.util.sortKeyOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.random.Random

class ContactRepository(private val db: AppDatabase, val photos: PhotoStore, private val prefs: Prefs) {
    private val dao get() = db.dao()

    val rows: Flow<List<ContactRow>> get() = dao.observeRows()
    val groups: Flow<List<GroupWithCount>> get() = dao.observeGroups()
    fun groupRows(groupId: Long) = dao.observeGroupRows(groupId)
    fun searchDocs() = dao.observeSearchDocs()

    fun observeDetail(id: Long): Flow<ContactDetail?> =
        combine(dao.observeContact(id), dao.observePhones(id), dao.observeGroupsOf(id)) { c, p, g ->
            c?.let { ContactDetail(it, p, g) }
        }.distinctUntilChanged()

    suspend fun detail(id: Long): ContactDetail? =
        dao.contact(id)?.let { ContactDetail(it, dao.phones(id), dao.groupsOf(id)) }

    suspend fun lookup(normalized: String): CallerHit? = dao.lookup(normalized)

    /** Numbers in [phones] that already belong to another contact: full number -> owner's name. */
    suspend fun conflicts(phones: List<PhoneEntity>, selfId: Long): Map<String, Pair<Long, String>> {
        val out = HashMap<String, Pair<Long, String>>()
        for (p in phones) {
            val owner = dao.ownerOf(p.fullPhoneNumber) ?: continue
            if (owner != selfId) out[p.fullPhoneNumber] = owner to (dao.nameOf(owner) ?: "")
        }
        return out
    }

    /**
     * Creates or updates a contact with all of its numbers and groups in one transaction.
     * New contacts need an [avatarSeed] picked by the form so the preview matches what is saved.
     */
    suspend fun save(contact: ContactEntity, phones: List<PhoneEntity>, groupIds: List<Long>): Long =
        dao.saveContact(contact.copy(sortKey = sortKeyOf(contact.name)), phones, groupIds)

    suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val files = ids.chunked(500).flatMap { dao.photoPaths(it) }
        ids.chunked(500).forEach { dao.deleteContacts(it) }
        files.forEach(photos::delete)
    }

    suspend fun setStarred(ids: List<Long>, starred: Boolean) = ids.chunked(500).forEach { dao.setStarred(it, starred) }

    // --- Groups -----------------------------------------------------------------------------------------------
    suspend fun createGroup(name: String): Long =
        dao.groupByName(name.trim())?.id ?: dao.insertGroup(GroupEntity(name = name.trim(), position = dao.nextGroupPosition()))

    suspend fun renameGroup(id: Long, name: String) = dao.renameGroup(id, name.trim())
    suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)
    suspend fun groupList() = dao.groups()
    suspend fun memberIds(groupId: Long) = dao.memberIds(groupId)

    suspend fun addToGroup(groupId: Long, ids: List<Long>) = dao.insertMembers(ids.map { GroupMember(it, groupId) })
    suspend fun removeFromGroup(groupId: Long, ids: List<Long>) = dao.removeMembers(groupId, ids)

    /** Group selection mode: members become exactly [ids]. */
    suspend fun setMembers(groupId: Long, ids: Set<Long>) {
        val current = dao.memberIds(groupId).toSet()
        removeFromGroup(groupId, (current - ids).toList())
        addToGroup(groupId, (ids - current).toList())
    }

    // --- Import / export --------------------------------------------------------------------------------------
    suspend fun exportData(): ExportData {
        val phones = dao.allPhones().groupBy { it.contactId }
        val groups = dao.allMemberships().groupBy({ it.contactId }, { it.groupName })
        return ExportData(dao.allContacts().map { ExportContact(it, phones[it.id].orEmpty(), groups[it.id].orEmpty()) })
    }

    /** Inserts imported contacts, skipping any whose numbers are already stored. Returns how many were added. */
    suspend fun importContacts(items: List<ImportedContact>): Int {
        val known = dao.allFullNumbers().toHashSet()
        var added = 0
        for (item in items) {
            val fresh = item.phones.filter { known.add(it.fullPhoneNumber) }
            if (fresh.isEmpty()) continue // no numbers, or every number already stored
            val photo = item.photoBase64?.let(photos::saveFromBase64) ?: ""
            val groupIds = item.groups.map { createGroup(it) }
            save(item.contact.copy(photoPath = photo, avatarSeed = Random.nextInt(Int.MAX_VALUE)), fresh, groupIds)
            added++
        }
        return added
    }

    // --- 2.x -> 3.0 one-time work after MIGRATION_4_5 -----------------------------------------------------------
    suspend fun finishLegacyMigration() {
        if (prefs.legacyPostDone()) return
        photos.backfill(dao)
        dao.idsAndNames().forEach { dao.setSortKey(it.id, sortKeyOf(it.name)) }
        for (row in dao.legacyLabels()) {
            val ids = row.labels.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { createGroup(it) }
            dao.insertMembers(ids.distinct().map { GroupMember(row.id, it) })
        }
        dao.clearLegacyLabels()
        prefs.markLegacyPostDone()
    }
}

data class ExportContact(val contact: ContactEntity, val phones: List<PhoneEntity>, val groups: List<String>)
data class ExportData(val contacts: List<ExportContact>)
data class ImportedContact(
    val contact: ContactEntity,
    val phones: List<PhoneEntity>,
    val groups: List<String> = emptyList(),
    val photoBase64: String? = null,
)

/** Builds a phone row from user input; shared by the editor, VCF import and tests. */
fun phoneOf(dialCode: String, national: String, countryCode: String, label: String, contactId: Long = 0): PhoneEntity {
    val digits = national.filter(Char::isDigit).trimStart('0')
    return PhoneEntity(contactId = contactId, fullPhoneNumber = dialCode + digits, phoneNumber = digits, countryCode = countryCode, label = label)
}
