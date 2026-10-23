package io.github.asagnc.aurora.ui

import android.view.ViewGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.browser.BrowserController

@Composable internal fun BrowserPage(browser: BrowserController, owner: String, dismiss: () -> Unit) {
    var selected by rememberSaveable(owner) { mutableStateOf<String?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var proxySettings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val tabs = browser.tabs.filter { it.owner == owner }
    val active = tabs.firstOrNull { it.id == selected } ?: tabs.firstOrNull()
    fun navigate() {
        if (browser.configuring || address.isBlank()) return
        val tab = active ?: browser.open(owner)?.also { selected = it.id }
        tab?.let { browser.navigate(it, address) }
    }
    LaunchedEffect(active?.id, active?.address) { address = active?.address.orEmpty() }
    DisposableEffect(owner) { browser.show(owner); onDispose { browser.hide(owner) } }
    FullPage("浏览器", dismiss) {
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        fun openAddress() { navigate(); focus.clearFocus(); keyboard?.hide() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(address, { address = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("输入 HTTPS 地址", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(24.dp), textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = AuroraBorder,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openAddress() }))
                TextButton(::openAddress, enabled = !browser.configuring && browser.supported && address.isNotBlank()) { Text("打开") }
                Box {
                    IconButton({ menu = true }) { LocalIcon(R.drawable.ic_more_vert, "浏览器菜单") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("新页面") }, onClick = { menu = false; browser.open(owner)?.let { selected = it.id } }, enabled = !browser.configuring)
                        DropdownMenuItem(text = { Text("后退") }, enabled = active?.view?.canGoBack() == true && !browser.configuring,
                            onClick = { menu = false; active?.view?.goBack() })
                        DropdownMenuItem(text = { Text("前进") }, enabled = active?.view?.canGoForward() == true && !browser.configuring,
                            onClick = { menu = false; active?.view?.goForward() })
                        DropdownMenuItem(text = { Text("刷新") }, enabled = active != null && !browser.configuring,
                            onClick = { menu = false; active?.view?.reload() })
                        DropdownMenuItem(text = { Text("代理设置") }, onClick = { menu = false; proxySettings = true })
                        DropdownMenuItem(text = { Text("关闭本会话所有页面") }, onClick = { menu = false; tabs.toList().forEach(browser::close) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    InputChip(selected = active == tab, onClick = { selected = tab.id }, label = { Text("${index + 1} · ${tab.title.take(12)}") },
                        trailingIcon = { IconButton({ browser.close(tab) }, Modifier.size(24.dp)) { LocalIcon(R.drawable.ic_close, "关闭页面 ${index + 1}") } })
                }
            }
            Text(if (browser.configuring) "正在应用代理设置，暂不加载页面" else when (browser.proxy) {
                "" -> "代理：跟随系统"; "direct" -> "代理：直连"; else -> "代理：自定义"
            }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            browser.transfers.values.filter { it.owner == owner }.forEach { transfer ->
                Text("${transfer.direction} · ${android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, transfer.bytes)}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (active != null) {
                if (active.progress < 100) LinearProgressIndicator(progress = { active.progress / 100f }, modifier = Modifier.fillMaxWidth())
                key(active.id) {
                    AndroidView(factory = {
                        (active.view.parent as? ViewGroup)?.removeView(active.view)
                        browser.resume(active)
                        active.view
                    }, modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { (it.parent as? ViewGroup)?.removeView(it) })
                }
            } else Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                LocalIcon(R.drawable.ic_language, "浏览器")
                Spacer(Modifier.height(16.dp))
                Text(if (browser.supported) "输入网址开始浏览" else "请更新系统 WebView 以支持独立会话配置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text("本会话的网页与登录状态独立保存。最多同时打开 8 个页面。",
                    color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    browser.uploads.pending?.takeIf { it.tab.owner == owner }?.let { request ->
        key(request.id) { BrowserUploadSheet(browser.uploads,request) }
    }
    if (proxySettings) BrowserProxyDialog(browser) { proxySettings = false }
    browser.error?.let { message -> AlertDialog(onDismissRequest = { browser.error = null }, title = { Text("浏览器提示") },
        text = { Text(message) }, confirmButton = { TextButton({ browser.error = null }) { Text("知道了") } }) }
}

@Composable private fun BrowserProxyDialog(browser: BrowserController, dismiss: () -> Unit) {
    var value by remember { mutableStateOf(browser.proxy.takeUnless { it == "direct" }.orEmpty()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("浏览器代理") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("仅影响 Aurora 所有浏览器页面，不改变模型 API 或手机其他应用的网络。更改前需关闭所有页面。", style = MaterialTheme.typography.bodyMedium)
            if (!browser.proxySupported) Text("当前 WebView 不支持代理覆盖。", color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value, { value = it }, label = { Text("http(s)://主机:端口 或 socks://主机:端口") }, singleLine = true)
            Text("不支持代理认证。回环和本地地址遵循 WebView 默认绕过规则。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            if (browser.tabs.isNotEmpty()) TextButton({ browser.tabs.toList().forEach(browser::close) }) { Text("关闭所有会话的浏览器页面") }
            TextButton({ browser.applyProxy("") { dismiss() } }, enabled = browser.tabs.isEmpty() && !browser.configuring) { Text("跟随系统") }
            TextButton({ browser.applyProxy("direct") { dismiss() } }, enabled = browser.proxySupported && browser.tabs.isEmpty() && !browser.configuring) { Text("直连") }
        }
    }, confirmButton = { TextButton({ browser.applyProxy(value.trim()) { dismiss() } },
        enabled = browser.proxySupported && browser.tabs.isEmpty() && !browser.configuring && value.isNotBlank()) { Text("应用代理") } },
        dismissButton = { TextButton(dismiss) { Text("关闭") } })
}
