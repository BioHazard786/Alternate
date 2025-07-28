package expo.modules.callerid.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CallerDao {
    @Query("SELECT * FROM caller_info WHERE fullPhoneNumber = :phoneNumber OR phoneNumber = :phoneNumber")
    suspend fun getCallerInfo(phoneNumber: String): CallerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallerInfo(callerInfo: CallerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultipleCallerInfo(entities: List<CallerEntity>)

    @Query("DELETE FROM caller_info WHERE fullPhoneNumber = :phoneNumber")
    suspend fun deleteCallerInfo(phoneNumber: String)

    @Query("DELETE FROM caller_info WHERE fullPhoneNumber IN (:phoneNumbers)")
    suspend fun deleteMultipleCallerInfo(phoneNumbers: List<String>)

    @Query("SELECT fullPhoneNumber FROM caller_info")
    suspend fun getAllPhoneNumbers(): List<String>

    @Query("DELETE FROM caller_info")
    suspend fun clearAllCallerInfo()

    @Query("SELECT * FROM caller_info ORDER BY name ASC")
    suspend fun getAllCallerInfo(): List<CallerEntity>
}
