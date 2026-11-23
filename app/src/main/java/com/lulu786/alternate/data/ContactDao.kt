package com.lulu786.alternate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val ROW_SELECT = """
    SELECT c.id, c.name, c.prefix, c.suffix, c.appointment, c.location, c.photoPath, c.starred, c.avatarSeed, c.sortKey,
           p.fullPhoneNumber AS fullPhoneNumber, p.countryCode AS countryCode, p.label AS phoneLabel,
           c.createdAt AS createdAt, c.updatedAt AS updatedAt
    FROM contact c
    LEFT JOIN phone p ON p.contactId = c.id
      AND p.position = (SELECT MIN(position) FROM phone WHERE contactId = c.id)
"""

@Dao
interface ContactDao {
    @Query("$ROW_SELECT ORDER BY c.sortKey, c.id")
    fun observeRows(): Flow<List<ContactRow>>

    @Query("$ROW_SELECT WHERE c.id IN (SELECT contactId FROM group_member WHERE groupId = :groupId) ORDER BY c.sortKey, c.id")
    fun observeGroupRows(groupId: Long): Flow<List<ContactRow>>

    @Query(
        """SELECT c.id, c.name, c.prefix, c.suffix, c.nickname, c.appointment, c.location, c.email, c.website, c.notes,
           COALESCE((SELECT group_concat(fullPhoneNumber || ' ' || phoneNumber || ' ' || label, ' ') FROM phone WHERE contactId = c.id), '') AS phones,
           COALESCE((SELECT group_concat(g.name, ' ') FROM contact_group g JOIN group_member m ON m.groupId = g.id WHERE m.contactId = c.id), '') AS groupNames
           FROM contact c"""
    )
    fun observeSearchDocs(): Flow<List<SearchDoc>>

    @Query("SELECT COUNT(*) FROM contact")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM contact WHERE id = :id")
    suspend fun contact(id: Long): ContactEntity?

    @Query("SELECT * FROM contact WHERE id = :id")
    fun observeContact(id: Long): Flow<ContactEntity?>

    @Query("SELECT * FROM phone WHERE contactId = :id ORDER BY position")
    fun observePhones(id: Long): Flow<List<PhoneEntity>>

    @Query("SELECT * FROM phone WHERE contactId = :id ORDER BY position")
    suspend fun phones(id: Long): List<PhoneEntity>

    @Query("SELECT g.* FROM contact_group g JOIN group_member m ON m.groupId = g.id WHERE m.contactId = :id ORDER BY g.position, g.name")
    fun observeGroupsOf(id: Long): Flow<List<GroupEntity>>

    @Query("SELECT g.* FROM contact_group g JOIN group_member m ON m.groupId = g.id WHERE m.contactId = :id ORDER BY g.position, g.name")
    suspend fun groupsOf(id: Long): List<GroupEntity>

    /**
     * Caller-ID lookup; hits the phoneNumber and fullPhoneNumber indexes, never loads blobs. Numbers saved "as typed"
     * (empty countryCode) match by their digits without the trunk zero, e.g. 08012345678 also answers +91 80 1234 5678.
     */
    @Query(
        """SELECT c.id AS contactId, c.name, c.prefix, c.suffix, c.appointment, c.location, c.photoPath, c.avatarSeed,
           p.fullPhoneNumber, p.phoneNumber, p.label, p.countryCode
           FROM phone p JOIN contact c ON c.id = p.contactId
           WHERE p.fullPhoneNumber = :n OR p.phoneNumber = :n
              OR (p.countryCode = '' AND LENGTH(ltrim(p.phoneNumber, '0')) >= 7 AND :n LIKE '%' || ltrim(p.phoneNumber, '0'))
           LIMIT 1"""
    )
    suspend fun lookup(n: String): CallerHit?

    @Query("SELECT c.id FROM phone p JOIN contact c ON c.id = p.contactId WHERE p.fullPhoneNumber = :full")
    suspend fun ownerOf(full: String): Long?

    @Query("SELECT fullPhoneNumber FROM phone")
    suspend fun allFullNumbers(): List<String>

    @Query("SELECT c.id FROM contact c WHERE c.id IN (:ids)")
    suspend fun existingIds(ids: List<Long>): List<Long>

    @Query("SELECT * FROM contact ORDER BY sortKey, id")
    suspend fun allContacts(): List<ContactEntity>

    @Query("SELECT * FROM phone ORDER BY contactId, position")
    suspend fun allPhones(): List<PhoneEntity>

    @Query("SELECT m.contactId AS contactId, g.name AS groupName FROM group_member m JOIN contact_group g ON g.id = m.groupId")
    suspend fun allMemberships(): List<Membership>

    @Insert suspend fun insertContact(c: ContactEntity): Long
    @Update suspend fun updateContact(c: ContactEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPhones(p: List<PhoneEntity>)
    @Query("DELETE FROM phone WHERE contactId = :id") suspend fun deletePhones(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMembers(m: List<GroupMember>)
    @Query("DELETE FROM group_member WHERE contactId = :id") suspend fun clearMemberships(id: Long)
    @Query("DELETE FROM contact WHERE id IN (:ids)") suspend fun deleteContacts(ids: List<Long>)
    @Query("UPDATE contact SET starred = :starred WHERE id IN (:ids)") suspend fun setStarred(ids: List<Long>, starred: Boolean)
    @Query("SELECT photoPath FROM contact WHERE id IN (:ids) AND photoPath != ''") suspend fun photoPaths(ids: List<Long>): List<String>

    // Groups
    @Query("SELECT g.id, g.name, (SELECT COUNT(*) FROM group_member WHERE groupId = g.id) AS count FROM contact_group g ORDER BY g.position, g.name")
    fun observeGroups(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM contact_group ORDER BY position, name")
    suspend fun groups(): List<GroupEntity>

    @Query("SELECT * FROM contact_group WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun groupByName(name: String): GroupEntity?

    @Insert suspend fun insertGroup(g: GroupEntity): Long
    @Query("UPDATE contact_group SET name = :name WHERE id = :id") suspend fun renameGroup(id: Long, name: String)
    @Query("DELETE FROM contact_group WHERE id = :id") suspend fun deleteGroup(id: Long)
    @Query("SELECT contactId FROM group_member WHERE groupId = :id") suspend fun memberIds(id: Long): List<Long>
    @Query("SELECT contactId, groupId FROM group_member") fun observeMembers(): Flow<List<GroupMember>>
    @Query("DELETE FROM group_member WHERE groupId = :groupId AND contactId IN (:ids)") suspend fun removeMembers(groupId: Long, ids: List<Long>)

    // 2.x photo backfill
    @Query("SELECT id, legacyPhoto FROM contact WHERE legacyPhoto != ''")
    suspend fun legacyPhotos(): List<LegacyPhoto>
    @Query("UPDATE contact SET photoPath = :path, legacyPhoto = '' WHERE id = :id")
    suspend fun finishLegacyPhoto(id: Long, path: String)
    @Query("SELECT id, labels FROM contact WHERE labels != ''")
    suspend fun legacyLabels(): List<LegacyLabels>
    @Query("UPDATE contact SET labels = '' WHERE labels != ''")
    suspend fun clearLegacyLabels()
    @Query("UPDATE contact SET sortKey = :key WHERE id = :id") suspend fun setSortKey(id: Long, key: String)
    @Query("SELECT id, name FROM contact") suspend fun idsAndNames(): List<IdName>
    @Query("SELECT c.name FROM contact c WHERE c.id = :id") suspend fun nameOf(id: Long): String?
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM contact_group") suspend fun nextGroupPosition(): Int

    @Transaction
    suspend fun saveContact(c: ContactEntity, phones: List<PhoneEntity>, groupIds: List<Long>): Long {
        val id = if (c.id == 0L) insertContact(c) else { updateContact(c); c.id }
        deletePhones(id)
        insertPhones(phones.mapIndexed { i, p -> p.copy(id = 0, contactId = id, position = i) })
        clearMemberships(id)
        insertMembers(groupIds.map { GroupMember(id, it) })
        return id
    }
}

data class Membership(val contactId: Long, val groupName: String)
data class LegacyPhoto(val id: Long, val legacyPhoto: String)
data class LegacyLabels(val id: Long, val labels: String)
data class IdName(val id: Long, val name: String)
