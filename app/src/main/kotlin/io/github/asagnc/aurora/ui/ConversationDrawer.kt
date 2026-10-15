package io.github.asagnc.aurora.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.data.SessionHeader
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.delay

@Composable internal fun ConversationDrawer(sessions: List<SessionHeader>, model: ChatViewModel,
    onOpen: (String) -> Unit, onProviders: () -> Unit, onSettings: () -> Unit, onSpace: (String) -> Unit, onNew: () -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var menu by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SessionHeader?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<List<String>?>(null) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<SessionHeader>()) }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(query, sessions) { if (query.isNotBlank()) { delay(180); matches = model.searchSessions(query.take(200)) } }
    val displayed = if (query.isBlank()) sessions else matches
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background, drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)) {
        Column(Modifier.fillMaxHeight().width(300.dp).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("会话", style = MaterialTheme.typography.headlineSmall)
                IconButton({ selecting = !selecting; selected = emptySet() }) { LocalIcon(R.drawable.ic_checklist, "多选会话") }
            }
            FilledTonalButton(onNew, Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AuroraMint, contentColor = AuroraInk)) {
                LocalIcon(R.drawable.ic_add, "新对话"); Spacer(Modifier.width(10.dp)); Text("新对话")
            }
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "搜索会话")
            if (selecting) Row {
                TextButton({ model.pin(selected.toList(), true); selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("置顶 ${selected.size}") }
                TextButton({ deleting = selected.toList() }, enabled = selected.isNotEmpty()) { Text("删除 ${selected.size}") }
            }
            LazyColumn(Modifier.weight(1f).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true to "置顶", false to "最近").forEach { (pinned, label) ->
                    val rows = displayed.filter { it.pinned == pinned }
                    if (rows.isNotEmpty()) {
                        item(label) { Text(label, Modifier.padding(vertical = 8.dp), color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
                        items(rows, key = { it.id }) { session ->
                            Surface(shape = RoundedCornerShape(16.dp), color = if (session.id == state.current?.id) AuroraMist else Color.Transparent) {
                                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selecting) toggle(session.id) else onOpen(session.id) },
                                    onLongClick = { menu = session.id }).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (selecting) Checkbox(session.id in selected, { toggle(session.id) })
                                    else { LocalIcon(R.drawable.ic_chat_bubble, "会话"); Spacer(Modifier.width(10.dp)) }
                                    Text(session.title, Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Box {
                                        IconButton({ menu = session.id }) { LocalIcon(if (session.pinned) R.drawable.ic_push_pin else R.drawable.ic_more_vert, "会话操作") }
                                        DropdownMenu(menu == session.id, { menu = null }) {
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
                }
                if (displayed.isEmpty()) item { Text(if (query.isBlank()) "还没有会话" else "没有匹配的会话", color = AuroraMuted,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 20.dp)) }
                if (state.moreSessions && query.isBlank()) item { TextButton({ model.loadMoreSessions() }) { Text("更多会话") } }
            }
            Surface(onClick = { onSpace("conversation") }, shape = RoundedCornerShape(16.dp), color = AuroraMist) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LocalIcon(R.drawable.ic_folder_open, "空间与工具"); Spacer(Modifier.width(12.dp)); Text("空间与工具", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                DrawerFooter("设置", R.drawable.ic_settings, Modifier.weight(1f), onSettings)
                DrawerFooter("供应商", R.drawable.ic_hub, Modifier.weight(1f), onProviders)
                DrawerFooter("Skills", R.drawable.ic_extension, Modifier.weight(1f)) { onSpace("resources") }
            }
        }
    }
    renaming?.let { session -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text("重命名会话") },
        text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
        confirmButton = { TextButton({ model.rename(session.id, renameText); renaming = null }, enabled = renameText.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton({ renaming = null }) { Text("取消") } }) }
    deleting?.let { ids -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${ids.size} 个会话？") },
        text = { Text("会话及其空间内的文件会一起删除。共享资源、保留工作区和原始文件会保留。") },
        confirmButton = { TextButton({ model.delete(ids); selected = emptySet(); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}

@Composable private fun DrawerFooter(label: String, icon: Int, modifier: Modifier, click: () -> Unit) {
    TextButton(click, modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val color = when (label) { "设置" -> Color(0xFF347EDD); "供应商" -> Color(0xFFE57D15); else -> Color(0xFF8762DD) }
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), label, Modifier.size(19.dp),
                tint = if (LocalAppearance.current.state.colorful) color else AuroraMuted)
            Text(label, style = MaterialTheme.typography.bodySmall, color = AuroraInk)
        }
    }
}
