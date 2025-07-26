package expo.modules.callerid.database

import android.content.Context
import kotlinx.coroutines.runBlocking

class CallerRepository(context: Context) {
    private val callerDao = CallerDatabase.getDatabase(context).callerDao()

    suspend fun getCallerInfo(phoneNumber: String): CallerEntity? {
        return callerDao.getCallerInfo(phoneNumber)
    }

    // Synchronous wrapper for CallReceiver usage
    fun getCallerInfoSync(phoneNumber: String): CallerEntity? {
        return try {
            runBlocking {
                callerDao.getCallerInfo(phoneNumber)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun storeCallerInfo(callerEntity: CallerEntity): Boolean {
        return try {
            callerDao.insertCallerInfo(callerEntity)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun storeMultipleCallerInfo(callerEntities: List<CallerEntity>): Boolean {
        return try {
            callerDao.insertMultipleCallerInfo(callerEntities)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeCallerInfo(fullPhoneNumber: String): Boolean {
        return try {
            callerDao.deleteCallerInfo(fullPhoneNumber)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAllStoredNumbers(): List<String> {
        return try {
            callerDao.getAllPhoneNumbers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllCallerInfo(): List<CallerEntity> {
        return try {
            callerDao.getAllCallerInfo()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearAllCallerInfo(): Boolean {
        return try {
            callerDao.clearAllCallerInfo()
            true
        } catch (e: Exception) {
            false
        }
    }
}
