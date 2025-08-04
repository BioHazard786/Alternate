package expo.modules.callerid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import expo.modules.callerid.database.CallerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CallDirectoryProvider : ContentProvider() {

    private var callerRepository: CallerRepository? = null
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authorityUri: Uri

    companion object {
        private const val DIRECTORIES = 1
        private const val PHONE_LOOKUP = 2
        private const val PRIMARY_PHOTO = 3
        private const val PRIMARY_PHOTO_URI = "photo/primary_photo"

        // Store photo data temporarily for the current lookup
        @Volatile
        private var currentPhotoData: String = ""

        // Cache for temporary photo files to avoid recreating the same image
        private val photoFileCache = mutableMapOf<String, File>()
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

    private fun getStoredPhotoForCurrentLookup(): String {
        return currentPhotoData
    }

    private fun cleanupOldTempFiles() {
        try {
            context?.cacheDir?.listFiles { file ->
                file.name.startsWith("temp_photo_") &&
                        (System.currentTimeMillis() - file.lastModified()) > 30000 // 30 seconds old
            }?.forEach {
                it.delete()
                // Remove from cache if exists
                photoFileCache.values.removeAll { cachedFile -> cachedFile.absolutePath == it.absolutePath }
            }
        } catch (e: Exception) {
            Log.e("CallDirectoryProvider", "Error cleaning temp files", e)
        }
    }

    private fun getPhotoHash(photoData: String): String {
        return photoData.hashCode().toString()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // val callingPackage = callingPackage
        // Log.d("CallerIDProvider", "Query from: $callingPackage")
        // Log.d("CallerIDProvider", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        // Log.d("CallerIDProvider", "Android: ${Build.VERSION.RELEASE}")

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
                        val result = repo.getCallerInfo(correctedPhoneNumber)
                        result
                    }

                    callerEntity?.let { entity ->
                        // Store photo data for later use in openAssetFile
                        currentPhotoData = entity.photo

                        projection?.map { column ->
                            when (column) {
                                PhoneLookup._ID -> -1
                                PhoneLookup.DISPLAY_NAME -> buildString {
                                    entity.prefix.takeIf { it.isNotBlank() }
                                        ?.let { append(it).append(" ") }
                                    append(entity.name)
                                    entity.suffix.takeIf { it.isNotBlank() }
                                        ?.let { append(", ").append(it) }
                                }

                                PhoneLookup.LABEL -> when {
                                    entity.appointment.isNotEmpty() && entity.location.isNotEmpty() -> "${entity.appointment}, ${entity.location}"
                                    entity.appointment.isNotEmpty() -> entity.appointment
                                    entity.location.isNotEmpty() -> entity.location
                                    else -> "Mobile"
                                }

                                PhoneLookup.NUMBER -> entity.fullPhoneNumber
                                PhoneLookup.NORMALIZED_NUMBER -> entity.phoneNumber
                                PhoneLookup.PHOTO_THUMBNAIL_URI,
                                PhoneLookup.PHOTO_URI -> {
                                    if (entity.photo.isNotEmpty()) {
                                        Uri.withAppendedPath(authorityUri, PRIMARY_PHOTO_URI)
                                    } else {
                                        null
                                    }
                                }

                                else -> null
                            }
                        }?.let {
                            cursor.addRow(it)
                        }
                    } ?: run {
                        // Clear photo data if no caller found
                        currentPhotoData = ""
                    }
                    return cursor
                } ?: return null
            }
        }
        return null
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {

        return when (uriMatcher.match(uri)) {
            PRIMARY_PHOTO -> {
                try {
                    val photoData = getStoredPhotoForCurrentLookup()

                    if (photoData.isNotEmpty()) {
                        // Create a hash for caching
                        val photoHash = getPhotoHash(photoData)

                        // Check if we already have this photo cached
                        val cachedFile = photoFileCache[photoHash]
                        if (cachedFile != null && cachedFile.exists()) {
                            val pfd = ParcelFileDescriptor.open(
                                cachedFile,
                                ParcelFileDescriptor.MODE_READ_ONLY
                            )
                            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                        }

                        val (mimeType, base64String) = if (photoData.startsWith("data:")) {
                            val commaIndex = photoData.indexOf(",")
                            val headerPart =
                                photoData.substring(5, commaIndex) // Remove "data:" prefix
                            val mimeType =
                                headerPart.split(";")[0] // Get MIME type before semicolon
                            val base64Part = photoData.substring(commaIndex + 1)
                            Pair(mimeType, base64Part)
                        } else {
                            Pair("image/jpeg", photoData) // Default to JPEG if no data URI
                        }

                        // Determine file extension from MIME type
                        val fileExtension = when (mimeType.lowercase()) {
                            "image/png" -> "png"
                            "image/gif" -> "gif"
                            "image/webp" -> "webp"
                            else -> "jpg" // Default to JPG
                        }

                        val imageBytes = try {
                            Base64.decode(base64String, Base64.DEFAULT)
                        } catch (e: IllegalArgumentException) {
                            Log.e("CallDirectoryProvider", "Invalid base64 data", e)
                            return null
                        }

                        // Create temporary file with hash-based name for better caching
                        val tempFile =
                            File(
                                context?.cacheDir,
                                "temp_photo_${photoHash}.$fileExtension"
                            )

                        // Clean up old temp files
                        cleanupOldTempFiles()

                        try {
                            FileOutputStream(tempFile).use { fos ->
                                fos.write(imageBytes)
                            }

                            // Cache the file
                            photoFileCache[photoHash] = tempFile

                            // Return AssetFileDescriptor for the temporary file
                            val pfd = ParcelFileDescriptor.open(
                                tempFile,
                                ParcelFileDescriptor.MODE_READ_ONLY
                            )
                            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)

                        } catch (e: IOException) {
                            Log.e("CallDirectoryProvider", "Error creating temp file", e)
                            tempFile.delete()
                            photoFileCache.remove(photoHash)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CallDirectoryProvider", "Error serving photo", e)
                }
                null
            }

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
