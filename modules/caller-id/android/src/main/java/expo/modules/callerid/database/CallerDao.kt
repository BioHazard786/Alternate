package expo.modules.callerid.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CallerDao {
    @Query("SELECT * FROM caller_info WHERE phoneNumber = :phoneNumber")
    suspend fun getCallerInfo(phoneNumber: String): CallerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallerInfo(callerInfo: CallerEntity)

    @Query("DELETE FROM caller_info WHERE phoneNumber = :phoneNumber")
    suspend fun deleteCallerInfo(phoneNumber: String)

    @Query("SELECT phoneNumber FROM caller_info")
    suspend fun getAllPhoneNumbers(): List<String>

    @Query("DELETE FROM caller_info")
    suspend fun clearAllCallerInfo()

    @Query("SELECT * FROM caller_info ORDER BY name ASC")
    suspend fun getAllCallerInfo(): List<CallerEntity>
}
