package io.github.asagnc.aurora.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RootPhase { ORDINARY, CONNECTING, READY, UNAVAILABLE }
data class RootState(val phase: RootPhase = RootPhase.ORDINARY, val detail: String = "普通模式；Root 未连接")

class RootController(private val context: Context) {
    private val mutable = MutableStateFlow(RootState())
    val state = mutable.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private data class Endpoint(
        val remote: IRootControl,
        val identity: io.github.asagnc.aurora.core.LinuxRootIdentity?,
        val prepare: Int, val run: Int, val file: Int, val terminal: Int, val metadata: Int, val packet: Int, val managedRecovery: Int,
    )
    @Volatile private var endpoint: Endpoint? = null
    internal val managedClient get() = endpoint?.takeIf { it.managedRecovery == 1 }?.let { RootManagedClient(it.remote, context) }
    internal val packetClient get() = endpoint?.takeIf { it.packet == 1 }?.let { RootPacketClient(it.remote, context) }
    internal val metadataClient get() = endpoint?.takeIf { it.metadata == 1 }?.let { RootMetadataClient(it.remote, context) }
    internal val linuxTerminalClient get() = endpoint?.takeIf { it.terminal == 1 && it.run == 3 }?.let { RootTerminalClient(it.remote) }
    internal val linuxFileClient get() = endpoint?.takeIf { it.file == 1 }?.let { RootLinuxFileClient(it.remote, context) }
    internal val linuxRunClient get() = endpoint?.takeIf { it.run == 3 }?.let { RootLinuxRunClient(it.remote, context) }
    internal val commandClient get() = endpoint?.let { RootCommandClient(it.remote, context) }
    internal val terminalClient get() = endpoint?.let { RootTerminalClient(it.remote) }
    internal val linuxClient get() = endpoint?.let { current -> current.identity?.let {
        RootLinuxClient(current.remote, it, context, current.prepare)
    } }
    internal val linuxAvailable get() = endpoint?.identity != null
    internal val rootImagesAvailable get() = endpoint?.let { it.identity != null && it.prepare >= 2 } == true
    internal val linuxExecution get() = endpoint?.takeIf { it.run == 3 }?.let { current ->
        current.identity?.let { RootLinuxExecution(it, RootLinuxRunClient(current.remote, context),
            if (current.terminal == 1) RootTerminalClient(current.remote) else null) }
    }
    private val lifecycle = Mutex()

    suspend fun connect() = withContext(Dispatchers.Main.immediate) {
        lifecycle.withLock { connectLocked() }
    }

    private suspend fun connectLocked() {
        if (mutable.value.phase in setOf(RootPhase.CONNECTING, RootPhase.READY)) return
        mutable.value = RootState(RootPhase.CONNECTING, "等待 Root 授权和服务连接…")
        try {
            withTimeout(30_000) {
                val granted = withContext(Dispatchers.IO) { Shell.getShell().isRoot }
                if (!granted) throw SecurityException("Root unavailable")
                val remote = bind()
                val snapshot = withContext(Dispatchers.IO) { remote.snapshot() }
                check(connection != null && remote.asBinder().isBinderAlive)
                require(snapshot.getInt("commandProtocol") == 2)
                require(snapshot.getInt("terminalProtocol") == 1)
                val detail = describe(snapshot)
                val identity = if (snapshot.getInt("linuxPrepareProtocol") in 1..2) runCatching {
                    val identity = io.github.asagnc.aurora.core.WireJson.decodeFromString<io.github.asagnc.aurora.core.LinuxRootIdentity>(
                        requireNotNull(snapshot.getString("linuxIdentity")).also { require(it.length <= 1024) })
                    identity.validate(); require(identity.pid == snapshot.getInt("pid")); identity
                }.getOrNull() else null
                endpoint = Endpoint(remote, identity,
                    if (identity != null) snapshot.getInt("linuxPrepareProtocol") else 0,
                    snapshot.getInt("linuxRunProtocol"), snapshot.getInt("linuxFileProtocol"),
                    snapshot.getInt("linuxTerminalProtocol"), snapshot.getInt("linuxMetadataProtocol"), snapshot.getInt("packetCaptureProtocol"),
                    snapshot.getInt("managedRecoveryProtocol"))
                mutable.value = RootState(RootPhase.READY, detail)
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) { disconnectLocked() }
            if (cancel is TimeoutCancellationException) mutable.value = RootState(RootPhase.UNAVAILABLE, "Root 服务连接超时；普通功能仍可使用")
            else throw cancel
        } catch (failure: Exception) {
            disconnectLocked()
            mutable.value = RootState(RootPhase.UNAVAILABLE, "Root 未获授权或服务不可用（${failure.javaClass.simpleName}）；普通功能仍可使用")
        }
    }

    private suspend fun bind(): IRootControl = suspendCancellableCoroutine { continuation ->
        val pending = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (continuation.isActive) continuation.resume(IRootControl.Stub.asInterface(binder))
                else RootService.unbind(this)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (connection === this) {
                    endpoint = null
                    connection = null
                    mutable.value = RootState(RootPhase.UNAVAILABLE, "Root 服务已退出；普通功能仍可使用")
                }
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Root service disconnected"))
            }
        }
        connection = pending
        continuation.invokeOnCancellation { main.post {
            RootService.unbind(pending)
            if (connection === pending) connection = null
        } }
        RootService.bind(Intent(context, AuroraRootService::class.java), pending)
    }

    suspend fun disconnect() = withContext(Dispatchers.Main.immediate) {
        lifecycle.withLock { disconnectLocked() }
    }

    private suspend fun disconnectLocked() = withContext(NonCancellable) {
        val previous = endpoint
        endpoint = null
        withContext(Dispatchers.IO) { runCatching { previous?.remote?.closeCommands() } }
        connection?.let { RootService.unbind(it) }
        connection = null
        withContext(Dispatchers.IO) { runCatching { Shell.getCachedShell()?.close() } }
        mutable.value = RootState()
    }

    private fun describe(snapshot: Bundle): String {
        require(snapshot.getInt("protocol", -1) == 1 && snapshot.getInt("uid", -1) == 0 &&
            snapshot.getInt("pid", -1) > 0 && snapshot.getInt("sdk", -1) >= 36)
        return "Root 服务已验证：UID 0\n服务进程：${snapshot.getInt("pid")}\nAndroid API：${snapshot.getInt("sdk")}\n" +
            "架构：${snapshot.getString("architecture")}\n内核：${snapshot.getString("kernel")}\nSELinux：${snapshot.getString("selinux")}\n" +
            "Root 原生命令与交互终端接口已连接；系统 API 与工具包仍在开发。"
    }
}
