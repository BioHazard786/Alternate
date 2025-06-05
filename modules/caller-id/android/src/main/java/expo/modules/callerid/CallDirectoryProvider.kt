package expo.modules.callerid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import androidx.core.net.toUri
import expo.modules.callerid.database.CallerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CallDirectoryProvider : ContentProvider() {

    private var callerRepository: CallerRepository? = null
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authorityUri: Uri

    companion object {
        private const val DIRECTORIES = 1
        private const val PHONE_LOOKUP = 2
        private const val PRIMARY_PHOTO = 3
        private const val PRIMARY_PHOTO_URI = "photo/primary_photo"
    }

    override fun onCreate(): Boolean {
        context?.let { ctx ->
            callerRepository = CallerRepository(ctx)
            val authority = ctx.getString(R.string.callerid_authority)
            authorityUri = "content://$authority".toUri()

            uriMatcher.apply {
                addURI(authority, "directories", DIRECTORIES)
                addURI(authority, "phone_lookup/*", PHONE_LOOKUP)
                addURI(authority, PRIMARY_PHOTO_URI, PRIMARY_PHOTO)
            }
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val callingPackage = callingPackage
        Log.d("CallerIDProvider", "Query from: $callingPackage")
        Log.d("CallerIDProvider", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d("CallerIDProvider", "Android: ${Build.VERSION.RELEASE}")

        val matchResult = uriMatcher.match(uri)

        when (matchResult) {
            DIRECTORIES -> {
                val label = context?.getString(R.string.app_name) ?: return null

                val cursor = MatrixCursor(projection)
                projection?.map { column ->
                    when (column) {
                        Directory.ACCOUNT_NAME,
                        Directory.ACCOUNT_TYPE,
                        Directory.DISPLAY_NAME -> label

                        Directory.TYPE_RESOURCE_ID -> R.string.app_name
                        Directory.EXPORT_SUPPORT -> Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY
                        Directory.SHORTCUT_SUPPORT -> Directory.SHORTCUT_SUPPORT_NONE
                        else -> null
                    }
                }?.let {
                    cursor.addRow(it)
                }
                return cursor
            }

            PHONE_LOOKUP -> {
                callerRepository?.let { repo ->
                    val phoneNumber = uri.pathSegments[1]

                    val correctedPhoneNumber = if (phoneNumber.startsWith("+")) {
                        phoneNumber.substring(1)
                    } else {
                        phoneNumber
                    }

                    val cursor = MatrixCursor(projection)

                    val callerEntity = runBlocking(Dispatchers.IO) {
                        var result = repo.getCallerInfo(correctedPhoneNumber)
                        if (result == null) {
                            result = repo.getCallerInfo(phoneNumber)
                        }
                        result
                    }

                    callerEntity?.let { entity ->
                        projection?.map { column ->
                            when (column) {
                                PhoneLookup._ID -> -1
                                PhoneLookup.DISPLAY_NAME -> entity.name
                                PhoneLookup.LABEL -> when {
                                    entity.appointment.isNotEmpty() && entity.city.isNotEmpty() -> "${entity.appointment}, ${entity.city}"
                                    entity.appointment.isNotEmpty() -> entity.appointment
                                    entity.city.isNotEmpty() -> entity.city
                                    else -> "Mobile"
                                }

                                PhoneLookup.NUMBER -> entity.phoneNumber
                                PhoneLookup.NORMALIZED_NUMBER -> entity.phoneNumber
                                PhoneLookup.PHOTO_THUMBNAIL_URI,
                                PhoneLookup.PHOTO_URI -> {
                                    Uri.withAppendedPath(authorityUri, PRIMARY_PHOTO_URI)
                                }

                                else -> null
                            }
                        }?.let {
                            cursor.addRow(it)
                        }
                    }
                    return cursor
                } ?: return null
            }
        }
        return null
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        return when (uriMatcher.match(uri)) {
            PRIMARY_PHOTO -> null
            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException()
    }
}
