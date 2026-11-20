package com.lulu786.alternate.ui.groups

import com.lulu786.alternate.ui.components.RoundedTitle
import com.lulu786.alternate.ui.Sym
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.lulu786.alternate.ui.components.AppFab
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.data.GroupWithCount
import com.lulu786.alternate.ui.AppViewModel
import com.lulu786.alternate.ui.components.ConfirmDialog
import com.lulu786.alternate.ui.components.ContactListRow
import com.lulu786.alternate.ui.components.EmptyState
import com.lulu786.alternate.ui.components.NameDialog
import com.lulu786.alternate.ui.components.segment
import kotlinx.coroutines.launch

@Composable
fun GroupDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onOpen: (Long) -> Unit, onAddPeople: () -> Unit) {
    val rows by remember(id) { vm.repo.groupRows(id) }.collectAsStateWithLifecycle(null)
    val groups by vm.groups.collectAsStateWithLifecycle()
    val name = groups.orEmpty().firstOrNull { it.id == id }?.name.orEmpty()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.clearSelection() }

    Scaffold(
        Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (vm.selecting) TopAppBar(
                title = { Text("${vm.selected.size} selected") },
                navigationIcon = { IconButton(vm::clearSelection) { Icon(Sym.Close, "Cancel selection") } },
                actions = { TextButton({ val ids = vm.selected.toList(); scope.launch { vm.repo.removeFromGroup(id, ids); vm.clearSelection() } }) { Text("Remove from group") } },
            ) else LargeFlexibleTopAppBar(
                title = { RoundedTitle(name) }, navigationIcon = { IconButton(onBack) { Icon(Sym.ArrowBack, "Back") } }, scrollBehavior = scroll,
                actions = {
                    Box {
                        IconButton({ menu = true }) { Icon(Sym.MoreVert, "More options for $name") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Rename") }, { menu = false; renaming = true })
                            DropdownMenuItem({ Text("Share group as VCF") }, { menu = false; vm.shareGroup(context, id) })
                            DropdownMenuItem({ Text("Delete group") }, { menu = false; deleting = true })
                        }
                    }
                },
                subtitle = rows?.size?.let { n -> { Text(if (n == 1) "1 contact" else "$n contacts") } },
            )
        },
        floatingActionButton = { if (!vm.selecting) AppFab(Sym.PersonAdd, "Add people", onAddPeople) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        val list = rows
        if (list != null && list.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Sym.PersonAdd, "No one in this group yet") { Button(onAddPeople) { Text("Add people") } } }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list.orEmpty().size, key = { list!![it].id }) { i ->
                val r = list!![i]
                ContactListRow(r, r.id in vm.selected, { if (vm.selecting) vm.toggle(r.id) else onOpen(r.id) }, { vm.toggle(r.id) }, index = i, count = list.size)
            }
        }
    }
    GroupDialogs(vm, id, name, renaming, deleting, { renaming = false; deleting = false }, onBack)
}

@Composable
private fun GroupDialogs(vm: AppViewModel, id: Long, name: String, renaming: Boolean, deleting: Boolean, onDone: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (renaming) NameDialog("Rename group", name, "Rename", onDone) { n -> onDone(); scope.launch { vm.repo.renameGroup(id, n) } }
    if (deleting) ConfirmDialog("Delete “$name”?", "The group is removed. Its contacts stay in Alternate.", "Delete", { onDone(); scope.launch { vm.repo.deleteGroup(id) }; onDeleted() }, onDone)
}

/** "Add people": every contact with a checkbox, current members pre-checked. */
@Composable
fun MemberPickerScreen(vm: AppViewModel, groupId: Long, onDone: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var checked by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(groupId) { checked = vm.repo.memberIds(groupId).toSet() }
    val scope = rememberCoroutineScope()
    val current = checked
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add people") },
                navigationIcon = { IconButton(onDone) { Icon(Sym.Close, "Close") } },
                actions = { Button({ scope.launch { vm.repo.setMembers(groupId, current.orEmpty()); onDone() } }, Modifier.padding(end = 8.dp), enabled = current != null) { Text("Save") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        val list = rows.orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list.size, key = { list[it].id }) { i ->
                val r = list[i]; val on = current?.contains(r.id) == true
                ContactListRow(r, on, { checked = current.orEmpty().let { if (r.id in it) it - r.id else it + r.id } }, { checked = current.orEmpty().let { if (r.id in it) it - r.id else it + r.id } }, index = i, count = list.size)
            }
        }
    }
}
