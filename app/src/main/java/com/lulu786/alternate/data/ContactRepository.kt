package com.lulu786.alternate.data

import com.lulu786.alternate.util.Csv
import com.lulu786.alternate.util.CsvRow
import com.lulu786.alternate.util.intl
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
    val members get() = dao.observeMembers()
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
    suspend fun save(contact: ContactEntity, phones: List<PhoneEntity>, groupIds: List<Long>, stamp: Boolean = true): Long {
        val now = System.currentTimeMillis()
        // A restore keeps the dates it was given; every other save is an add or an edit happening now.
        val stamped = if (!stamp) contact else contact.copy(createdAt = if (contact.id == 0L) now else contact.createdAt, updatedAt = now)
        return dao.saveContact(stamped.copy(sortKey = sortKeyOf(contact.name)), phones, groupIds)
    }

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

    /**
     * Spreadsheet round trip. A row whose id matches a contact edits it (only saved when something changed, so an
     * untouched re-import leaves "last modified" alone); any other row is added like a VCF import. A number that
     * belongs to a different contact is dropped from the row, because numbers are unique.
     */
    suspend fun importCsv(rows: List<CsvRow>, simIso: String?): CsvResult {
        var updated = 0; var skipped = 0
        val fresh = ArrayList<ImportedContact>()
        for (row in rows) {
            val existing = row.id?.let { dao.contact(it) }
            if (existing == null) {
                val phones = row.phones.orEmpty().mapNotNull { (raw, label) -> Csv.phone(raw, label, simIso) }
                val contact = Csv.apply(ContactEntity(name = ""), row.fields)
                if (contact.name.isEmpty() || phones.isEmpty()) skipped++ else fresh += ImportedContact(contact, phones, row.groups.orEmpty())
                continue
            }
            val current = dao.phones(existing.id)
            // Numbers saved "as typed" print without a '+'; matching the printed text keeps them from being re-read as SIM-country numbers.
            val printed = current.associateBy { intl(it.fullPhoneNumber, it.countryCode) }
            val wanted = row.phones?.mapNotNull { (raw, label) -> printed[raw]?.copy(label = label) ?: Csv.phone(raw, label, simIso) } ?: current
            val phones = wanted.distinctBy { it.fullPhoneNumber }.filter { p -> dao.ownerOf(p.fullPhoneNumber).let { it == null || it == existing.id } }
            val merged = Csv.apply(existing, row.fields)
            if (merged.name.isEmpty() || phones.isEmpty()) { skipped++; continue }
            val currentGroups = dao.groupsOf(existing.id).map { it.id }
            val groupIds = row.groups?.map { createGroup(it) } ?: currentGroups
            fun key(p: List<PhoneEntity>) = p.map { Triple(it.fullPhoneNumber, it.label, it.countryCode) }
            if (merged == existing && key(phones) == key(current) && groupIds.toSet() == currentGroups.toSet()) continue
            save(merged, phones, groupIds); updated++
        }
        val added = if (fresh.isEmpty()) 0 else importContacts(fresh)
        return CsvResult(added, updated, skipped + fresh.size - added)
    }

    // --- Backup ------------------------------------------------------------------------------------------------
    suspend fun backupData(): BackupData = BackupData(
        exportedAt = System.currentTimeMillis(),
        groups = dao.groups().map { it.name },
        contacts = exportData().contacts.map { (c, phones, groups) ->
            BackupContact(
                c.name, c.prefix, c.suffix, c.nickname, c.appointment, c.location, c.email, c.website, c.birthday, c.notes,
                c.starred, c.avatarSeed, c.createdAt, c.updatedAt,
                phones.map { BackupPhone(it.fullPhoneNumber, it.phoneNumber, it.countryCode, it.label) }, groups,
                c.photoPath.takeIf { it.isNotEmpty() }?.let(photos::readBase64),
            )
        },
    )

    /** Adds what is missing, like an import, but keeps avatar, star and dates. Contacts whose numbers all exist are left alone. */
    suspend fun restore(data: BackupData): Int {
        data.groups.forEach { createGroup(it) } // first, so the group order survives
        val known = dao.allFullNumbers().toHashSet()
        var added = 0
        for (b in data.contacts) {
            val fresh = b.phones.filter { known.add(it.full) }
            if (fresh.isEmpty()) continue
            val contact = ContactEntity(
                name = b.name, prefix = b.prefix, suffix = b.suffix, nickname = b.nickname, appointment = b.appointment, location = b.location,
                email = b.email, website = b.website, birthday = b.birthday, notes = b.notes, starred = b.starred, avatarSeed = b.avatarSeed,
                photoPath = b.photo?.let(photos::saveFromBase64) ?: "", createdAt = b.createdAt, updatedAt = b.updatedAt,
            )
            save(contact, fresh.map { PhoneEntity(contactId = 0, fullPhoneNumber = it.full, phoneNumber = it.national, countryCode = it.country, label = it.label) }, b.groups.map { createGroup(it) }, stamp = false)
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

data class CsvResult(val added: Int, val updated: Int, val skipped: Int)
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
