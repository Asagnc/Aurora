package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class RootLinuxFileClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "LinuxFiles")
    suspend fun invoke(owner: String, operation: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject = withContext(Dispatchers.IO) {
        require(UUID.fromString(owner).toString() == owner)
        val encoded = arguments.toString().also { require(it.length <= 262144) }
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root file service exited")) }
        val lease = awake.acquire(40_000)
        try {
            binder.linkToDeath(death, 0)
            remote.linuxFiles(id, owner, Bundle().apply { putString("operation", operation); putString("arguments", encoded) },
                object : IRootResult.Stub() { override fun complete(value: String) { result.complete(value) } })
            val response = withTimeout(30_000) { result.await() }.also { require(it.length <= 262144) }
            WireJson.parseToJsonElement(response).jsonObject.also { require(it["error"] == null) { "Root file operation was not confirmed" } }
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { remote.cancelAndWait(id) }
            } finally { runCatching { binder.unlinkToDeath(death, 0) }; lease.close() }
        }
    }
}
