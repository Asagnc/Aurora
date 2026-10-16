package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.Connection

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ModelPickerSheet(connections: List<Connection>, selected: Connection?,
    choose: (Connection) -> Unit, manage: () -> Unit, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        if (connections.none { it.enabled }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("选择模型", style = MaterialTheme.typography.titleLarge)
                Text("添加自己的 API 连接后，模型会显示在这里。", color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
                Button({ dismiss(); manage() }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("添加供应商或中转") }
            }
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium)
            if (connections.count { it.enabled } > 1) {
            SearchField(query, { query = it }, "搜索供应商或模型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "官方", "中转").forEach { name -> FilterChip(category == name, { category = name }, label = { Text(name) }) }
            }
            }
            val matches = connections.filter { it.enabled && (category == "全部" || it.official == (category == "官方")) &&
                (it.name.contains(query, true) || it.model.contains(query, true)) }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (matches.isEmpty()) item { Text(if (connections.isEmpty()) "先添加自己的 API 连接" else "没有匹配的模型",
                    Modifier.padding(vertical = 24.dp), color = AuroraMuted, style = MaterialTheme.typography.bodyMedium) }
                items(matches, key = { it.id }) { connection ->
                    Text(connection.name, color = AuroraMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Surface(onClick = { choose(connection); dismiss() }, shape = RoundedCornerShape(18.dp),
                        color = if (connection.id == selected?.id) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModelBrand(connection.model, 22.dp)
                            Column(Modifier.weight(1f)) {
                                Text(connection.model, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(when (connection.protocol) {
                                    io.github.asagnc.aurora.core.Protocol.CHAT -> "Chat Completions"
                                    io.github.asagnc.aurora.core.Protocol.RESPONSES -> "Responses"
                                    io.github.asagnc.aurora.core.Protocol.ANTHROPIC -> "Messages"
                                }, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                            }
                            RadioButton(connection.id == selected?.id, onClick = null)
                        }
                    }
                }
            }
            TextButton(manage, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = AuroraInk)) {
                LocalIcon(R.drawable.ic_settings, "管理供应商"); Spacer(Modifier.width(8.dp)); Text("管理供应商和模型")
            }
            Text("仅显示已配置并启用的连接", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
        }
    }
}

@Composable internal fun SearchField(value: String, change: (String) -> Unit, hint: String) {
    TextField(value, change, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium, placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), "搜索", Modifier.size(20.dp), tint = AuroraMuted) },
        colors = TextFieldDefaults.colors(focusedContainerColor = AuroraMist, unfocusedContainerColor = AuroraMist,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
}
