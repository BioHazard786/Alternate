package expo.modules.callerid.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CallerEntity::class],
    version = 4,
    exportSchema = false
)
abstract class CallerDatabase : RoomDatabase() {
    abstract fun callerDao(): CallerDao

    companion object {
        @Volatile
        private var INSTANCE: CallerDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with updated structure
                db.execSQL(
                    """
                    CREATE TABLE caller_info_new (
                        fullPhoneNumber TEXT PRIMARY KEY NOT NULL,
                        phoneNumber TEXT NOT NULL DEFAULT '',
                        countryCode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        appointment TEXT NOT NULL,
                        location TEXT NOT NULL,
                        iosRow TEXT NOT NULL,
                        suffix TEXT NOT NULL DEFAULT '',
                        prefix TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        website TEXT NOT NULL DEFAULT '',
                        birthday TEXT NOT NULL DEFAULT '',
                        labels TEXT NOT NULL DEFAULT '',
                        nickname TEXT NOT NULL DEFAULT ''
                    )
                """
                )

                // Copy data from old table to new table
                // Extract phone number without country code based on country dial codes
                db.execSQL(
                    """
                    INSERT INTO caller_info_new (
                        fullPhoneNumber, phoneNumber, countryCode, name, appointment, location, iosRow,
                        suffix, prefix, email, notes, website, birthday, labels, nickname
                    )
                    SELECT 
                        phoneNumber as fullPhoneNumber,
                        CASE 
                            WHEN countryCode = 'AR' AND phoneNumber LIKE '54%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'AU' AND phoneNumber LIKE '61%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'AT' AND phoneNumber LIKE '43%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'BH' AND phoneNumber LIKE '973%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'BD' AND phoneNumber LIKE '880%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'BE' AND phoneNumber LIKE '32%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'BR' AND phoneNumber LIKE '55%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'CA' AND phoneNumber LIKE '1%' THEN SUBSTR(phoneNumber, 2)
                            WHEN countryCode = 'CL' AND phoneNumber LIKE '56%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'CN' AND phoneNumber LIKE '86%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'CO' AND phoneNumber LIKE '57%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'CZ' AND phoneNumber LIKE '420%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'DK' AND phoneNumber LIKE '45%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'EG' AND phoneNumber LIKE '20%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'FI' AND phoneNumber LIKE '358%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'FR' AND phoneNumber LIKE '33%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'DE' AND phoneNumber LIKE '49%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'GR' AND phoneNumber LIKE '30%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'HU' AND phoneNumber LIKE '36%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'IN' AND phoneNumber LIKE '91%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'ID' AND phoneNumber LIKE '62%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'IR' AND phoneNumber LIKE '98%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'IQ' AND phoneNumber LIKE '964%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'IL' AND phoneNumber LIKE '972%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'IT' AND phoneNumber LIKE '39%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'JP' AND phoneNumber LIKE '81%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'JO' AND phoneNumber LIKE '962%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'KE' AND phoneNumber LIKE '254%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'KW' AND phoneNumber LIKE '965%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'LB' AND phoneNumber LIKE '961%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'MY' AND phoneNumber LIKE '60%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'MX' AND phoneNumber LIKE '52%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'MA' AND phoneNumber LIKE '212%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'NL' AND phoneNumber LIKE '31%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'NG' AND phoneNumber LIKE '234%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'NO' AND phoneNumber LIKE '47%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'OM' AND phoneNumber LIKE '968%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'PK' AND phoneNumber LIKE '92%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'PE' AND phoneNumber LIKE '51%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'PH' AND phoneNumber LIKE '63%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'PL' AND phoneNumber LIKE '48%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'PT' AND phoneNumber LIKE '351%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'QA' AND phoneNumber LIKE '974%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'RU' AND phoneNumber LIKE '7%' THEN SUBSTR(phoneNumber, 2)
                            WHEN countryCode = 'SA' AND phoneNumber LIKE '966%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'SG' AND phoneNumber LIKE '65%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'ZA' AND phoneNumber LIKE '27%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'KR' AND phoneNumber LIKE '82%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'ES' AND phoneNumber LIKE '34%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'LK' AND phoneNumber LIKE '94%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'SE' AND phoneNumber LIKE '46%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'CH' AND phoneNumber LIKE '41%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'TH' AND phoneNumber LIKE '66%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'TR' AND phoneNumber LIKE '90%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'AE' AND phoneNumber LIKE '971%' THEN SUBSTR(phoneNumber, 4)
                            WHEN countryCode = 'GB' AND phoneNumber LIKE '44%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'US' AND phoneNumber LIKE '1%' THEN SUBSTR(phoneNumber, 2)
                            WHEN countryCode = 'VE' AND phoneNumber LIKE '58%' THEN SUBSTR(phoneNumber, 3)
                            WHEN countryCode = 'VN' AND phoneNumber LIKE '84%' THEN SUBSTR(phoneNumber, 3)
                            ELSE phoneNumber
                        END as phoneNumber,
                        countryCode,
                        name,
                        appointment,
                        city as location,
                        iosRow,
                        '' as suffix,
                        '' as prefix,
                        '' as email,
                        '' as notes,
                        '' as website,
                        '' as birthday,
                        '' as labels,
                        '' as nickname
                    FROM caller_info
                """
                )

                // Drop old table
                db.execSQL("DROP TABLE caller_info")

                // Rename new table to original name
                db.execSQL("ALTER TABLE caller_info_new RENAME TO caller_info")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add photo column to existing table
                db.execSQL("ALTER TABLE caller_info ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): CallerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallerDatabase::class.java,
                    "caller_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
