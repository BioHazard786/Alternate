package com.lulu786.alternate.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lulu786.alternate.AlternateApp
import com.lulu786.alternate.util.BackupCrypto
import com.lulu786.alternate.util.KeyWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.FileNotFoundException
import java.security.GeneralSecurityException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A problem the user can act on; [message] is shown as-is. */
class BackupException(message: String) : Exception(message)

/** Encrypted backups into a folder the user picked (Storage Access Framework), and restoring from one. */
class BackupService(private val context: Context, private val repo: ContactRepository, private val prefs: Prefs) {
    private val resolver get() = context.contentResolver

    /** Derives the key from [secret] once and keeps it Keystore-wrapped, so scheduled backups never need the secret. */
    suspend fun setUpKey(mode: Int, secret: String) = withContext(Dispatchers.Default) {
        val p = BackupCrypto.newParams(mode)
        val key = BackupCrypto.deriveKey(secret, p)
        prefs.setBackupKey(BackupKey(p.mode, p.kdf, p.iterations, Base64.encodeToString(p.salt, Base64.NO_WRAP), Base64.encodeToString(KeyWrap.wrap(key), Base64.NO_WRAP)))
    }

    /** Writes one backup file, trims old ones and records the outcome for the Settings row. */
    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { write() }
            .onSuccess { prefs.markBackupOk(System.currentTimeMillis()) }
            .onFailure { prefs.markBackupFailed(explain(it)) }
    }

    fun explain(e: Throwable) = when (e) {
        is BackupException -> e.message!!
        is SecurityException, is FileNotFoundException, is IllegalArgumentException -> "Backup folder unavailable. Choose it again."
        else -> "Backup failed (${e.javaClass.simpleName})"
    }

    private suspend fun write() {
        val s = prefs.current().backup
        val tree = s.folder.takeIf { it.isNotEmpty() }?.toUri() ?: throw BackupException("Choose a backup folder first")
        val k = s.key ?: throw BackupException("Set up backup encryption first")
        val key = try { KeyWrap.unwrap(Base64.decode(k.wrapped, Base64.NO_WRAP)) } catch (e: GeneralSecurityException) {
            // The Keystore key does not travel with Android's own app backup, so a restored app lands here.
            throw BackupException("The backup key is gone from this phone. Set up encryption again.")
        }
        val params = BackupCrypto.Params(k.mode, k.kdf, k.iterations, Base64.decode(k.salt, Base64.NO_WRAP))
        val bytes = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(BackupData.json.encodeToString(repo.backupData()).toByteArray(), key, params)
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "alternate-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.abk"
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: throw BackupException("Couldn't create a file in the backup folder")
        (resolver.openOutputStream(doc) ?: throw BackupException("Couldn't write to the backup folder")).use { it.write(bytes) }
        runCatching { prune(tree) } // a failed clean-up must not fail the backup that just succeeded
    }

    /** Keeps the newest [KEEP] backups. Only files named like ours are ever touched. */
    private fun prune(tree: Uri) {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = ArrayList<Pair<String, String>>() // document id to display name
        resolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) files += c.getString(0) to c.getString(1)
        }
        files.filter { ours.matches(it.second) }.sortedByDescending { it.second }.drop(KEEP)
            .forEach { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
    }

    /** Reads [uri]; returns the bytes and their header, or null when it is not one of our backups. */
    suspend fun open(uri: Uri): Pair<ByteArray, BackupCrypto.Params>? = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        BackupCrypto.peek(bytes)?.let { bytes to it }
    }

    /** Returns how many contacts were added. Throws [BackupException] for a wrong secret or a damaged file. */
    suspend fun restore(file: ByteArray, params: BackupCrypto.Params, secret: String): Int = withContext(Dispatchers.Default) {
        val data = try {
            BackupData.json.decodeFromString<BackupData>(String(BackupCrypto.decrypt(file, BackupCrypto.deriveKey(secret, params))))
        } catch (e: GeneralSecurityException) {
            throw BackupException(if (params.mode == BackupCrypto.MODE_RECOVERY) "Wrong recovery key, or the file is damaged" else "Wrong password, or the file is damaged")
        }
        if (data.version > BackupData.CURRENT) throw BackupException("This backup was made by a newer version of Alternate")
        repo.restore(data)
    }

    companion object {
        const val KEEP = 5
        private val ours = Regex("""alternate-backup-\d{8}-\d{6}\.abk.*""")
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // The outcome is recorded in settings; a failed run just waits for the next period.
    override suspend fun doWork(): Result {
        (applicationContext as AlternateApp).container.backups.backupNow()
        return Result.success()
    }
}

object BackupScheduler {
    private const val NAME = "backup"

    /** WorkManager keeps the schedule across restarts and reboots. Turning it on also runs one backup soon. */
    fun apply(context: Context, f: BackupFrequency) {
        val wm = WorkManager.getInstance(context)
        if (f == BackupFrequency.Off) { wm.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(f.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
