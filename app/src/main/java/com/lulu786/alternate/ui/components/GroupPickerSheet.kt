package com.lulu786.alternate.ui.components

import com.lulu786.alternate.ui.Sym
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lulu786.alternate.ui.AppViewModel

/** Checkbox sheet of all groups with a "New group" row. [onDone] gets the final set of checked group ids. */
@Composable
fun GroupPickerSheet(vm: AppViewModel, current: Set<Long>, onDismiss: () -> Unit, onDone: (Set<Long>) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var checked by remember { mutableStateOf(current) }
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss) {
        Text("Add to group", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn {
            items(groups.orEmpty(), key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth().clickable { checked = if (g.id in checked) checked - g.id else checked + g.id }.padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(g.id in checked, null)
                    Spacer(Modifier.width(16.dp))
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym.Add, null); Spacer(Modifier.width(16.dp)); Text("New group")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    Button({ onDone(checked); onDismiss() }) { Text("Done") }
                }
            }
        }
    }
    if (creating) NameDialog("New group", "", "Create", { creating = false }) { name ->
        creating = false
        vm.createGroup(name) { id -> checked = checked + id }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Group name") }) },
        confirmButton = { TextButton({ if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
