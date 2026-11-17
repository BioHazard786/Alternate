package com.lulu786.alternate.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/** Contact photos live as small square JPEGs in filesDir/photos; the DB only stores the file name. */
class PhotoStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    fun delete(name: String) {
        if (name.isNotEmpty()) file(name).delete()
    }

    /** Decodes [uri] (gallery pick), centre-crops to a square of at most [SIZE] px and returns the new file name. */
    fun saveFromUri(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { decodeSampled(it.readBytes()) }?.let(::write)
    }.getOrNull()

    fun saveBitmap(bitmap: Bitmap): String? = runCatching { write(squareCrop(bitmap)) }.getOrNull()

    /** 2.x stored photos as base64, optionally behind a `data:image/..;base64,` prefix. */
    fun saveFromBase64(data: String): String? = runCatching {
        val b64 = if (data.startsWith("data:")) data.substringAfter(',') else data
        decodeSampled(Base64.decode(b64, Base64.DEFAULT))?.let(::write)
    }.getOrNull()

    fun readBase64(name: String): String? =
        file(name).takeIf { it.exists() }?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let(::squareCrop)
    }

    private fun squareCrop(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return if (side > SIZE) Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true) else cropped
    }

    private fun write(bitmap: Bitmap): String {
        val name = "${System.nanoTime()}.jpg"
        FileOutputStream(file(name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    /** One-time: moves 2.x base64 photos out of the DB, and seeds groups from the legacy labels text. */
    suspend fun backfill(dao: ContactDao) {
        for (row in dao.legacyPhotos()) {
            val name = saveFromBase64(row.legacyPhoto)
            dao.finishLegacyPhoto(row.id, name ?: "") // undecodable photos are dropped rather than retried forever
        }
    }

    companion object { const val SIZE = 256 }
}
