package com.lulu786.alternate.data

import androidx.compose.runtime.Immutable
import com.lulu786.alternate.util.displayName

/** What lists need, and nothing more: no notes, no photo data. */
@Immutable
data class ContactRow(
    val id: Long,
    val name: String,
    val prefix: String,
    val suffix: String,
    val appointment: String,
    val location: String,
    val photoPath: String,
    val starred: Boolean,
    val avatarSeed: Int,
    val sortKey: String,
    val fullPhoneNumber: String?,
    val countryCode: String?,
    val phoneLabel: String?,
) {
    /** What lists show: "Mr Zaid, Khan". The avatar initial and sorting still use [name] alone. */
    val displayName: String
        get() = displayName(prefix, name, suffix)

    val subtitle: String
        get() = listOf(appointment, location).filter { it.isNotBlank() }.joinToString(" · ")
}

@Immutable
data class GroupWithCount(val id: Long, val name: String, val count: Int)

/** Haystack parts for search; loaded only while the search screen is open. */
data class SearchDoc(
    val id: Long,
    val name: String,
    val prefix: String,
    val suffix: String,
    val nickname: String,
    val appointment: String,
    val location: String,
    val email: String,
    val website: String,
    val notes: String,
    /** Space separated: full number, national number and label of every phone. */
    val phones: String,
    val groupNames: String,
)

/** Minimal hit used by the call overlay and the dialer directory. */
data class CallerHit(
    val contactId: Long,
    val name: String,
    val prefix: String,
    val suffix: String,
    val appointment: String,
    val location: String,
    val photoPath: String,
    val avatarSeed: Int,
    val fullPhoneNumber: String,
    val phoneNumber: String,
    val label: String,
    val countryCode: String,
)

data class ContactDetail(
    val contact: ContactEntity,
    val phones: List<PhoneEntity>,
    val groups: List<GroupEntity>,
)
