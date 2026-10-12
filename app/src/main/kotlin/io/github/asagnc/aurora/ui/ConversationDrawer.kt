package io.github.asagnc.aurora.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.data.SessionHeader
import io.github.asagnc.aurora.runtime.ChatViewModel

@Composable internal fun ConversationDrawer(sessions: List<SessionHeader>, model: ChatViewModel,
    onOpen: (String) -> Unit, onProviders: () -> Unit, onSettings: () -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var menu by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SessionHeader?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<List<String>?>(null) }
    val state by model.state.collectAsStateWithLifecycle()
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight().width(320.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("会话", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { selecting = !selecting; selected = emptySet() }) { LocalIcon(R.drawable.ic_checklist, "多选会话") }
            }
            FilledTonalButton(onClick = { model.newConversation() }, modifier = Modifier.fillMaxWidth()) { Text("新对话") }
            if (selecting) Row {
                TextButton(onClick = { model.pin(selected.toList(), true); selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("置顶 ${selected.size}") }
                TextButton(onClick = { deleting = selected.toList() }, enabled = selected.isNotEmpty()) { Text("删除") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selecting) toggle(session.id) else onOpen(session.id) },
                            onLongClick = { menu = session.id }).padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (selecting) Checkbox(checked = session.id in selected, onCheckedChange = { toggle(session.id) })
                            Text(session.title, Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 2)
                            Box {
                                IconButton(onClick = { menu = session.id }) { LocalIcon(if (session.pinned) R.drawable.ic_push_pin else R.drawable.ic_more_vert, "会话操作") }
                                DropdownMenu(expanded = menu == session.id, onDismissRequest = { menu = null }) {
                                    DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { LocalIcon(R.drawable.ic_edit, "重命名") }, onClick = { renaming = session; renameText = session.title; menu = null })
                                    DropdownMenuItem(text = { Text(if (session.pinned) "取消置顶" else "置顶") }, onClick = { model.pin(listOf(session.id), !session.pinned); menu = null })
                                    DropdownMenuItem(text = { Text("多选") }, onClick = { selecting = true; toggle(session.id); menu = null })
                                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { LocalIcon(R.drawable.ic_delete, "删除") }, onClick = { deleting = listOf(session.id); menu = null })
                                }
                            }
                        }
                    }
                }
            }
            if (state.moreSessions) TextButton(onClick = { model.loadMoreSessions() }) { Text("更多会话") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSettings) { LocalIcon(R.drawable.ic_settings, "设置"); Text("设置") }
                TextButton(onClick = onProviders) { LocalIcon(R.drawable.ic_hub, "供应商"); Text("供应商") }
            }
        }
    }
    renaming?.let { session -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("重命名会话") },
        text = { TextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { model.rename(session.id, renameText); renaming = null }, enabled = renameText.isNotBlank()) { Text("保存") } }) }
    deleting?.let { ids -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${ids.size} 个会话？") },
        text = { Text("会话及其空间内的文件会一起删除。共享资源、保留工作区和原始文件会保留。") },
        confirmButton = { TextButton(onClick = { model.delete(ids); selected = emptySet(); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}
