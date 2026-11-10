package io.github.asagnc.aurora.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.data.SessionHeader
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.delay

@Composable internal fun ConversationDrawer(sessions: List<SessionHeader>, model: ChatViewModel,
    onOpen: (String) -> Unit, onProviders: () -> Unit, onSettings: () -> Unit, onSpace: (String) -> Unit) {
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
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight().width(300.dp).imePadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("会话", Modifier.semantics { heading() }.focusable(), style = MaterialTheme.typography.headlineSmall)
                IconButton({ selecting = !selecting; selected = emptySet() }) { LocalIcon(R.drawable.ic_checklist, if (selecting) "结束多选" else "多选会话") }
            }
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
                            val anchor = rememberCouiMenuAnchor()
                            Surface(shape = RoundedCornerShape(16.dp), color = when {
                                selecting && session.id in selected -> MaterialTheme.colorScheme.primaryContainer
                                session.id == state.current?.id -> AuroraMist
                                else -> Color.Transparent
                            }) {
                                Row(Modifier.fillMaxWidth().couiMenuAnchor(anchor).combinedClickable(onClick = { if (selecting) toggle(session.id) else onOpen(session.id) },
                                    onLongClick = if (selecting) null else ({ anchor.open { menu = session.id } }))
                                    .semantics { if (selecting) { role = Role.Checkbox; toggleableState = ToggleableState(session.id in selected) } }
                                    .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (selecting) {
                                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(20.dp).background(if (session.id in selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                                                if (session.id in selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    else { LocalIcon(R.drawable.ic_chat_bubble, "会话"); Spacer(Modifier.width(10.dp)) }
                                    Column(Modifier.weight(1f).padding(vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium)
                                        MessageTime(session.updatedAt)
                                    }
                                    Box {
                                        IconButton({ anchor.open { menu = session.id } }) { LocalIcon(if (session.pinned) R.drawable.ic_push_pin else R.drawable.ic_more_vert, "会话操作") }
                                        Menu(expanded = menu == session.id, onDismissRequest = { menu = null }, anchor = anchor,
                                            items = listOf(CouiMenuItem("rename", "重命名"),
                                                CouiMenuItem("pin", if (session.pinned) "取消置顶" else "置顶"), CouiMenuItem("delete", "删除")),
                                            onItemClick = { action ->
                                                menu = null
                                                when (action.value) {
                                                    "rename" -> { renaming = session; renameText = session.title }
                                                    "pin" -> model.pin(listOf(session.id), !session.pinned)
                                                    "delete" -> deleting = listOf(session.id)
                                                }
                                            })
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
    renaming?.let { session -> AuroraPrompt("重命名会话", "保存",
        { model.rename(session.id, renameText); renaming = null }, { renaming = null },
        cancelLabel = "取消", confirmEnabled = renameText.isNotBlank()) {
        AuroraInput(renameText, { renameText = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = "会话名称")
    } }
    deleting?.let { ids -> AuroraPrompt("删除 ${ids.size} 个会话？", "删除",
        { model.delete(ids); selected = emptySet(); deleting = null }, { deleting = null },
        message = "会话及其空间内的文件会一起删除。共享资源、保留工作区和原始文件会保留。", cancelLabel = "取消") }

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
