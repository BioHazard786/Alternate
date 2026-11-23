package com.lulu786.alternate.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lulu786.alternate.AppContainer
import com.lulu786.alternate.data.AppSettings
import com.lulu786.alternate.data.AutoLock
import com.lulu786.alternate.data.BackupException
import com.lulu786.alternate.data.BackupFrequency
import com.lulu786.alternate.data.BackupScheduler
import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.data.CsvResult
import com.lulu786.alternate.data.LockState
import com.lulu786.alternate.ui.components.STAR
import com.lulu786.alternate.util.BackupCrypto
import com.lulu786.alternate.util.Csv
import com.lulu786.alternate.util.SearchIndex
import com.lulu786.alternate.util.SearchResult
import com.lulu786.alternate.util.Vcf
import com.lulu786.alternate.util.sectionOf
import com.lulu786.alternate.util.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One immutable snapshot of the Contacts tab, built off the main thread. */
data class ContactListModel(
    val favorites: List<ContactRow>,
    val sections: List<Pair<Char, List<ContactRow>>>,
    /** Contacts shown (after the filter). */
    val total: Int,
    /** Contacts saved, filter or not. */
    val all: Int,
    /** Letter -> index of its sticky header in the LazyColumn, for the fast scroller. */
    val letterStarts: List<Pair<Char, Int>>,
    val filter: ContactFilter,
)

/** Section key of the single header-less list that non-name sorts use. */
internal const val FLAT = '\u0000'

private fun comparatorOf(sort: ContactSort): Comparator<ContactRow> {
    val byName = compareBy<ContactRow>({ it.sortKey }, { it.id })
    // Unknown (0) dates and missing numbers always sink to the bottom.
    fun newestFirst(key: (ContactRow) -> Long) = compareByDescending<ContactRow> { key(it) > 0 }.thenByDescending(key).then(byName)
    return when (sort) {
        ContactSort.NameAsc, ContactSort.NameDesc -> byName
        ContactSort.Newest -> newestFirst { it.createdAt }
        ContactSort.Oldest -> compareByDescending<ContactRow> { it.createdAt > 0 }.thenBy { it.createdAt }.then(byName)
        ContactSort.Modified -> newestFirst { it.updatedAt }
        ContactSort.Phone -> compareBy<ContactRow, String?>(nullsLast()) { it.fullPhoneNumber }.then(byName)
        ContactSort.Country -> compareBy<ContactRow, String?>(nullsLast()) { it.countryCode }.then(byName)
    }
}

internal fun buildModel(
    all: List<ContactRow>, filter: ContactFilter, groupsOf: Map<Long, Set<Long>>, sort: ContactSort = ContactSort.NameAsc,
): ContactListModel {
    val rows = if (filter.active) all.filter { filter.matches(it, groupsOf) } else all
    // A filtered list is just results: no Favorites grid on top.
    val favorites = if (filter.active) emptyList() else rows.filter { it.starred }
    val sections = if (sort.lettered) {
        val lettered = rows.groupBy { sectionOf(it.sortKey) }.toSortedMap(compareBy { if (it == '#') '\uFFFF' else it }).map { it.key to it.value }
        if (sort == ContactSort.NameDesc) lettered.reversed().map { (k, v) -> k to v.reversed() } else lettered
    } else listOf(FLAT to rows.sortedWith(comparatorOf(sort)))
    // LazyColumn items before the first letter: the active-filter chip row, or the Favorites header + grid.
    val lead = if (filter.active) 1 else if (favorites.isEmpty()) 0 else 2
    var index = lead
    val starts = if (sort.lettered) sections.map { (letter, list) -> (letter to index).also { index += 1 + list.size } } else emptyList()
    // The star jumps to the Favorites header, the first item.
    val withStar = if (favorites.isEmpty() || !sort.lettered) starts else listOf(STAR to 0) + starts
    return ContactListModel(favorites, sections, rows.size, all.size, withStar, filter)
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(private val c: AppContainer) : ViewModel() {
    val repo = c.repo
    val prefs = c.prefs

    val settings: StateFlow<AppSettings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val filter = MutableStateFlow(ContactFilter())
    val sort = prefs.settings.map { it.sort }.distinctUntilChanged()
    fun setSort(v: ContactSort) { viewModelScope.launch { prefs.setSort(v) } }
    /** contactId -> its group ids, for the group filter. */
    private val groupsOf = repo.members.map { m -> m.groupBy({ it.contactId }, { it.groupId }).mapValues { it.value.toSet() } }
    val list: StateFlow<ContactListModel?> = combine(repo.rows, filter, groupsOf, sort, ::buildModel).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    // Eagerly: selectAll and copy read .value with no collector attached.
    val rows: StateFlow<List<ContactRow>?> = repo.rows.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val groups = repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    fun say(text: String) { messages.trySend(text) }

    // --- selection (a Set so rows only ever receive `selected: Boolean`) ---------------------------------------
    var selected by mutableStateOf<Set<Long>>(emptySet()); private set
    /** Set by the overflow menu's "Select", so selection mode can start with nothing picked yet. */
    private var selectMode by mutableStateOf(false)
    val selecting get() = selectMode || selected.isNotEmpty()
    fun startSelecting() { selectMode = true }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun clearSelection() { selected = emptySet(); selectMode = false }
    /** Everything currently shown, so "select all" respects the filter. */
    fun selectAll() { selected = list.value?.sections.orEmpty().flatMap { it.second }.map { it.id }.toSet() }

    // --- search --------------------------------------------------------------------------------------------------
    val query = MutableStateFlow("")
    /** Bumped when the Search tab is tapped again: refocus the field and bring the keyboard back. */
    var searchFocus by mutableIntStateOf(0)
    private val index = repo.searchDocs().map(::SearchIndex).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val results: StateFlow<List<SearchResult>> = combine(index, query.debounce(80)) { idx, q -> idx to q }
        .mapLatest { (idx, q) -> idx?.search(q).orEmpty() }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- lock ----------------------------------------------------------------------------------------------------
    /** null until settings are read; then true when the lock screen must cover the app. */
    var locked by mutableStateOf<Boolean?>(null); private set
    private var lockDecided = false

    fun onSettingsLoaded(s: AppSettings) {
        if (lockDecided) return
        lockDecided = true
        locked = shouldLock(s.lock, System.currentTimeMillis())
    }

    fun onBackground() = viewModelScope.launch {
        if (settings.value?.lock?.enabled == true && locked == false) prefs.recordBackground(System.currentTimeMillis())
    }

    fun onForeground() = viewModelScope.launch {
        val s = prefs.current()
        if (s.lock.enabled && locked == false && shouldLock(s.lock, System.currentTimeMillis())) locked = true
    }

    fun lockNow() = viewModelScope.launch { prefs.setForceLocked(true); locked = true }

    suspend fun tryPin(pin: String) = prefs.verifyPin(pin).also { if (it is com.lulu786.alternate.data.Prefs.PinResult.Ok) unlock() }
    fun unlockWithBiometric() { viewModelScope.launch { prefs.markUnlocked() }; locked = false }
    private fun unlock() { locked = false }

    private fun shouldLock(l: LockState, now: Long): Boolean = when {
        !l.enabled -> false
        l.forceLocked -> true
        l.autoLock == AutoLock.Disabled -> false
        l.autoLock == AutoLock.Immediately -> true
        else -> l.lastLockTime == 0L || now - l.lastLockTime > l.autoLock.millis
    }

    fun dismissLockNotice() { viewModelScope.launch { prefs.dismissLockResetNotice() } }
    fun finishSetup() { viewModelScope.launch { prefs.setSetupDone() } }

    // --- bulk actions ---------------------------------------------------------------------------------------------
    fun delete(ids: Collection<Long>) = viewModelScope.launch {
        repo.delete(ids.toList()); clearSelection(); say("${ids.size} deleted")
    }

    fun star(ids: Collection<Long>, starred: Boolean) = viewModelScope.launch {
        repo.setStarred(ids.toList(), starred); clearSelection()
    }

    fun addToGroup(groupId: Long, ids: Collection<Long>) = viewModelScope.launch {
        repo.addToGroup(groupId, ids.toList()); clearSelection(); say("Added to group")
    }

    fun createGroup(name: String, then: (Long) -> Unit = {}) = viewModelScope.launch { then(repo.createGroup(name)) }

    fun share(context: Context, ids: Collection<Long>?) = viewModelScope.launch {
        val all = repo.exportData().contacts
        val picked = if (ids == null) all else all.filter { it.contact.id in ids }
        val text = withContext(Dispatchers.Default) { Vcf.export(picked, repo.photos::readBase64) }
        context.shareText("contacts_${stamp()}.vcf", "text/vcard", text)
        clearSelection()
    }

    fun shareGroup(context: Context, groupId: Long) = viewModelScope.launch {
        share(context, repo.memberIds(groupId)).join()
    }

    /** Label of a long import/export in progress ("Importing…"), shown as a blocking loading indicator; null when idle. */
    var busy by mutableStateOf<String?>(null)
        private set

    fun exportTo(context: Context, uri: Uri, csv: Boolean = false) = viewModelScope.launch {
        busy = "Exporting…"
        val ok = runCatching {
            val text = withContext(Dispatchers.Default) {
                val data = repo.exportData().contacts
                if (csv) Csv.export(data) else Vcf.export(data, repo.photos::readBase64)
            }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
        }.isSuccess
        busy = null
        say(if (ok) "Contacts exported" else "Export failed")
    }

    /** One entry for both formats: a file mentioning BEGIN:VCARD near the top is a vCard, anything else is read as CSV. */
    fun importFrom(context: Context, uri: Uri, simIso: String?) = viewModelScope.launch {
        busy = "Importing…"
        val message = runCatching {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
            if (text.take(4096).contains("BEGIN:VCARD", ignoreCase = true)) {
                val parsed = withContext(Dispatchers.Default) { Vcf.parse(text, simIso) }
                if (parsed.isEmpty()) "No contacts found in that file" else repo.importContacts(parsed).let { if (it == 0) "Nothing new to import" else "$it contacts imported" }
            } else {
                val rows = withContext(Dispatchers.Default) { Csv.parse(text) }
                if (rows.isEmpty()) "No contacts found in that file" else repo.importCsv(rows, simIso).summary()
            }
        }.getOrDefault("Import failed")
        busy = null
        say(message)
    }

    private fun CsvResult.summary() = listOfNotNull(
        added.takeIf { it > 0 }?.let { "$it added" }, updated.takeIf { it > 0 }?.let { "$it updated" }, skipped.takeIf { it > 0 }?.let { "$it skipped" },
    ).joinToString(" · ").ifEmpty { "Nothing new to import" }

    // --- backup ----------------------------------------------------------------------------------------------------
    /** Header of the backup file being restored; non-null while the "enter your password" dialog is up. */
    var restoring by mutableStateOf<BackupCrypto.Params?>(null)
        private set
    private var restoreFile: ByteArray? = null

    fun setBackupFolder(context: Context, uri: Uri) = viewModelScope.launch {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val old = settings.value?.backup?.folder.orEmpty()
        context.contentResolver.takePersistableUriPermission(uri, rw)
        if (old.isNotEmpty() && old != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(old.toUri(), rw) }
        prefs.setBackupFolder(uri.toString())
    }

    fun setBackupKey(mode: Int, secret: String) = viewModelScope.launch {
        busy = "Securing backups…"
        val ok = runCatching { c.backups.setUpKey(mode, secret) }.isSuccess
        busy = null
        say(if (ok) "Backup encryption is on" else "Couldn't set up encryption")
    }

    fun setBackupFrequency(context: Context, f: BackupFrequency) {
        val b = settings.value?.backup
        if (f != BackupFrequency.Off && (b?.folder.isNullOrEmpty() || b?.key == null)) { say("Choose a folder and set up encryption first"); return }
        viewModelScope.launch { prefs.setBackupFrequency(f) }
        BackupScheduler.apply(context, f)
    }

    fun backupNow() = viewModelScope.launch {
        busy = "Backing up…"
        val r = c.backups.backupNow()
        busy = null
        say(r.exceptionOrNull()?.let { c.backups.explain(it) } ?: "Backup saved")
    }

    fun openBackup(uri: Uri) = viewModelScope.launch {
        busy = "Reading backup…"
        val opened = runCatching { c.backups.open(uri) }.getOrNull()
        busy = null
        if (opened == null) say("That isn’t an Alternate backup") else { restoreFile = opened.first; restoring = opened.second }
    }

    fun cancelRestore() { restoring = null; restoreFile = null }

    fun restore(secret: String) = viewModelScope.launch {
        val file = restoreFile ?: return@launch
        val params = restoring ?: return@launch
        busy = "Restoring…"
        val result = runCatching { c.backups.restore(file, params, secret) }
        busy = null
        val error = result.exceptionOrNull()
        say(when {
            error is BackupException -> error.message!!
            error != null -> "Restore failed"
            result.getOrThrow() == 0 -> "Nothing new to restore"
            else -> "${result.getOrThrow()} contacts restored"
        })
        if (error !is BackupException) cancelRestore() // a wrong secret keeps the dialog open to retype
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
