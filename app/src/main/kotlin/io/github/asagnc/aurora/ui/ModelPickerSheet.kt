package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.menu.rememberCouiWindowBackdrop
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
    ModalBottomSheet(dismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)), containerColor = MaterialTheme.colorScheme.surface) {
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
            val candidates = remember(connections) { connections.filter { it.enabled }.flatMap { connection ->
                connection.models.ifEmpty { listOf(io.github.asagnc.aurora.core.AvailableModel(connection.model, connection.model)) }
                    .map { model -> connection.copy(model = model.id) to model.name }
            } }
            if (candidates.size > 1) {
            SearchField(query, { query = it }, "搜索供应商或模型")
            AuroraTabs(listOf("全部", "官方", "中转").map { it to it }, category) { category = it }
            }
            val matches = candidates.filter { (connection, label) ->
                (category == "全部" || connection.official == (category == "官方")) &&
                    (connection.name.contains(query, true) || connection.model.contains(query, true) || label.contains(query, true)) }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (matches.isEmpty()) item { Text(if (connections.isEmpty()) "先添加自己的 API 连接" else "没有匹配的模型",
                    Modifier.padding(vertical = 24.dp), color = AuroraMuted, style = MaterialTheme.typography.bodyMedium) }
                matches.groupBy { it.first.id }.forEach { (providerId, models) ->
                  item("provider:$providerId") { Text(models.first().first.name, color = AuroraMuted,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                  items(models, key = { it.first.id + ":" + it.first.model }) { (connection, label) ->
                    Surface(onClick = { choose(connection); dismiss() }, shape = RoundedCornerShape(18.dp),
                        color = if (connection.id == selected?.id && connection.model == selected.model) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModelBrand(connection.model, 22.dp)
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(connection.model, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                                Text(when (connection.protocol) {
                                    io.github.asagnc.aurora.core.Protocol.CHAT -> "Chat Completions"
                                    io.github.asagnc.aurora.core.Protocol.RESPONSES -> "Responses"
                                    io.github.asagnc.aurora.core.Protocol.ANTHROPIC -> "Messages"
                                }, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                            }
                            if (connection.id == selected?.id && connection.model == selected.model) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                  }
                }
            }
            TextButton({ dismiss(); manage() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = AuroraInk)) {
                LocalIcon(R.drawable.ic_settings, "管理供应商"); Spacer(Modifier.width(8.dp)); Text("管理供应商和模型")
            }
            Text("仅显示已配置并启用的连接", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
        }
    }
}

@Composable internal fun SearchField(value: String, change: (String) -> Unit, hint: String) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val backdrop = requireNotNull(rememberCouiWindowBackdrop()) { "Search requires the current page backdrop" }
    SearchBar(modifier = Modifier.fillMaxWidth(), state = rememberCouiSearchBarState(expanded), query = value,
        onQueryChange = change, onExpandedChange = { expanded = it; if (!it) change("") }, placeholder = hint,
        cancelText = "取消", clearDescription = "清除搜索", backdrop = backdrop, onSearch = { keyboard?.hide() })
}
