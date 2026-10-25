package io.github.asagnc.aurora.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import io.github.asagnc.aurora.R

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Android 17 permission is requested only by the user's explicit settings action. */
@Composable internal fun LocalNetworkPermissionRow() {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    fun allowed() = context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    var expanded by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = allowed() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    ListItem(headlineContent = { Text("局域网访问") },
        supportingContent = { Text(if (granted) "已允许连接选定的本地服务" else "连接局域网设备或代理时需要授权") },
        leadingContent = { IconBadge(R.drawable.ic_hub, Color(0xFF347EDD)) },
        trailingContent = { Text("›", color = AuroraMuted) }, modifier = Modifier.clickable { expanded = true },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
    if (expanded) LocalNetworkPermissionDialog(granted, { expanded = false },
        { request.launch(LOCAL_NETWORK_PERMISSION) }, {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()))
        })
}

@Composable private fun LocalNetworkPermissionDialog(granted: Boolean, dismiss: () -> Unit,
    request: () -> Unit, settings: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("局域网访问") },
        text = { Text("Android 17 要求授权后才能连接局域网服务。Aurora 会按你选择的地址执行请求；授权本身不会修改系统代理、路由或证书。" +
            if (granted) "\n\n当前已授权，可在系统设置中撤销。" else "\n\n未授权时，相关连接可能被系统拒绝。") },
        confirmButton = { TextButton(if (granted) dismiss else request) { Text(if (granted) "完成" else "允许访问") } },
        dismissButton = { TextButton(settings) { Text("系统设置") } })
}
