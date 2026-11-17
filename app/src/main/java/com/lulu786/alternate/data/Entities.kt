package com.lulu786.alternate.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contact")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prefix: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val appointment: String = "",
    val location: String = "",
    val email: String = "",
    val website: String = "",
    val birthday: String = "",
    val notes: String = "",
    /** 2.x comma separated labels; seeds groups once, then is ignored. */
    val labels: String = "",
    /** File name inside filesDir/photos, empty when the contact has no photo. */
    val photoPath: String = "",
    val starred: Boolean = false,
    /** One random number that fixes the avatar's shape, colour and pattern forever. */
    val avatarSeed: Int = 0,
    val sortKey: String = "",
    /** 2.x base64 photo, cleared by the one-time backfill. Never selected in list queries. */
    val legacyPhoto: String = "",
)

@Entity(
    tableName = "phone",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contactId"), Index("phoneNumber"), Index("fullPhoneNumber", unique = true)],
)
data class PhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    /** Dial code + national digits, no '+'. */
    val fullPhoneNumber: String,
    val phoneNumber: String,
    /** ISO country code such as "IN". */
    val countryCode: String,
    val label: String = "Mobile",
    val position: Int = 0,
)

@Entity(tableName = "contact_group")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["contactId", "groupId"],
    foreignKeys = [
        ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(GroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId")],
)
data class GroupMember(val contactId: Long, val groupId: Long)
