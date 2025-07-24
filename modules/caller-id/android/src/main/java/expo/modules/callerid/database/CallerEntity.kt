package expo.modules.callerid.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caller_info")
data class CallerEntity(
    @PrimaryKey
    val fullPhoneNumber: String,
    val phoneNumber: String, // Phone number without country code
    val countryCode: String,
    val name: String,
    val appointment: String,
    val location: String, // Renamed from city
    val iosRow: String,
    val suffix: String = "",
    val prefix: String = "",
    val email: String = "",
    val notes: String = "",
    val website: String = "",
    val birthday: String = "",
    val labels: String = "",
    val nickname: String = ""
)
