package com.lulu786.alternate.callerid

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
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import com.lulu786.alternate.AlternateApp
import com.lulu786.alternate.R
import com.lulu786.alternate.util.displayName
import kotlinx.coroutines.runBlocking

/**
 * Lets the dialer show names and photos for saved numbers. The authority is the 2.x one on purpose,
 * so dialers that remembered the directory keep working after the update.
 */
class CallDirectoryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        authority = context!!.getString(R.string.callerid_authority)
        matcher.addURI(authority, "directories", DIRECTORIES)
        matcher.addURI(authority, "phone_lookup/*", PHONE_LOOKUP)
        matcher.addURI(authority, "photo/#", PHOTO)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val ctx = context ?: return null
        return when (matcher.match(uri)) {
            DIRECTORIES -> MatrixCursor(projection).also { c ->
                val label = ctx.getString(R.string.app_name)
                projection?.map {
                    when (it) {
                        Directory.ACCOUNT_NAME, Directory.ACCOUNT_TYPE, Directory.DISPLAY_NAME -> label
                        Directory.TYPE_RESOURCE_ID -> R.string.app_name
                        Directory.EXPORT_SUPPORT -> Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY
                        Directory.SHORTCUT_SUPPORT -> Directory.SHORTCUT_SUPPORT_NONE
                        else -> null
                    }
                }?.let(c::addRow)
            }

            PHONE_LOOKUP -> MatrixCursor(projection).also { c ->
                val iso = (ctx.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? TelephonyManager)
                    ?.let { it.networkCountryIso.ifEmpty { it.simCountryIso } }?.uppercase()
                val number = PhoneNormalizer.normalize(Uri.decode(uri.pathSegments[1]), iso)
                // Providers are queried on binder threads, so blocking here is fine.
                val hit = runBlocking { (ctx.applicationContext as AlternateApp).container.repo.lookup(number) } ?: return@also
                val photo = if (hit.photoPath.isNotEmpty()) "content://$authority/photo/${hit.contactId}".toUri() else null
                projection?.map {
                    when (it) {
                        PhoneLookup._ID -> hit.contactId
                        PhoneLookup.DISPLAY_NAME -> displayName(hit.prefix, hit.name, hit.suffix)
                        PhoneLookup.LABEL -> listOf(hit.appointment, hit.location).filter(String::isNotEmpty)
                            .joinToString(", ").ifEmpty { hit.label }
                        PhoneLookup.NUMBER -> hit.fullPhoneNumber
                        PhoneLookup.NORMALIZED_NUMBER -> hit.phoneNumber
                        PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.PHOTO_URI -> photo
                        else -> null
                    }
                }?.let(c::addRow)
            }

            else -> null
        }
    }

    /** Streams the contact's stored thumbnail directly; no temp copy, so concurrent lookups can't race. */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != PHOTO) return null
        val ctx = context ?: return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val app = ctx.applicationContext as AlternateApp
        val path = runBlocking { app.container.repo.detail(id)?.contact?.photoPath }.orEmpty()
        val file = app.container.photos.file(path).takeIf { path.isNotEmpty() && it.exists() } ?: return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }

    override fun getType(uri: Uri): String? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = throw UnsupportedOperationException()
    override fun insert(uri: Uri, values: ContentValues?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = throw UnsupportedOperationException()

    private companion object {
        const val DIRECTORIES = 1
        const val PHONE_LOOKUP = 2
        const val PHOTO = 3
    }
}
