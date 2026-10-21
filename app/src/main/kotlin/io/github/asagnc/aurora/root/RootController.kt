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
    @Volatile private var remote: IRootControl? = null
    internal val commandClient get() = remote?.let { RootCommandClient(it, context) }
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
                require(snapshot.getInt("commandProtocol") == 1)
                val detail = describe(snapshot)
                this@RootController.remote = remote
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
                    remote = null
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
        val endpoint = remote
        remote = null
        withContext(Dispatchers.IO) { runCatching { endpoint?.closeCommands() } }
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
            "Root 原生命令接口已连接；系统 API 与工具包仍在开发。"
    }
}
