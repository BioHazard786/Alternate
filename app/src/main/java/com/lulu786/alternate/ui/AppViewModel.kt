package com.lulu786.alternate.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lulu786.alternate.AppContainer
import com.lulu786.alternate.data.AppSettings
import com.lulu786.alternate.data.AutoLock
import com.lulu786.alternate.data.ContactRow
import com.lulu786.alternate.data.LockState
import com.lulu786.alternate.ui.components.STAR
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

internal fun buildModel(all: List<ContactRow>, filter: ContactFilter, groupsOf: Map<Long, Set<Long>>): ContactListModel {
    val rows = if (filter.active) all.filter { filter.matches(it, groupsOf) } else all
    // A filtered list is just results: no Favorites grid on top.
    val favorites = if (filter.active) emptyList() else rows.filter { it.starred }
    val sections = rows.groupBy { sectionOf(it.sortKey) }.toSortedMap(compareBy { if (it == '#') '￿' else it }).map { it.key to it.value }
    // LazyColumn items before the first letter: the active-filter chip row, or the Favorites header + grid.
    val lead = if (filter.active) 1 else if (favorites.isEmpty()) 0 else 2
    var index = lead
    val starts = sections.map { (letter, list) -> (letter to index).also { index += 1 + list.size } }
    // The star jumps to the Favorites header, the first item.
    val withStar = if (favorites.isEmpty()) starts else listOf(STAR to 0) + starts
    return ContactListModel(favorites, sections, rows.size, all.size, withStar, filter)
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(private val c: AppContainer) : ViewModel() {
    val repo = c.repo
    val prefs = c.prefs

    val settings: StateFlow<AppSettings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val filter = MutableStateFlow(ContactFilter())
    /** contactId -> its group ids, for the group filter. */
    private val groupsOf = repo.members.map { m -> m.groupBy({ it.contactId }, { it.groupId }).mapValues { it.value.toSet() } }
    val list: StateFlow<ContactListModel?> = combine(repo.rows, filter, groupsOf, ::buildModel).flowOn(Dispatchers.Default)
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

    fun exportTo(context: Context, uri: Uri) = viewModelScope.launch {
        busy = "Exporting…"
        val ok = runCatching {
            val text = withContext(Dispatchers.Default) { Vcf.export(repo.exportData().contacts, repo.photos::readBase64) }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
        }.isSuccess
        busy = null
        say(if (ok) "Contacts exported" else "Export failed")
    }

    fun importFrom(context: Context, uri: Uri, simIso: String?) = viewModelScope.launch {
        busy = "Importing…"
        val added = runCatching {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
            val parsed = withContext(Dispatchers.Default) { Vcf.parse(text, simIso) }
            if (parsed.isEmpty()) -1 else repo.importContacts(parsed)
        }.getOrDefault(-2)
        busy = null
        say(when { added == -1 -> "No contacts found in that file"; added < 0 -> "Import failed"; added == 0 -> "Nothing new to import"; else -> "$added contacts imported" })
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
