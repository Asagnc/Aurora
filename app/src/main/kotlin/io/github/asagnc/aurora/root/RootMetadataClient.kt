package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class RootMetadataClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "LinuxMetadata")
    suspend fun invoke(args: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val encoded = args.toString().also { require(it.length <= 8192) }
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root service exited")) }
        val lease = awake.acquire(40_000)
        try {
            binder.linkToDeath(death, 0)
            remote.maintainLinuxMetadata(id, Bundle().apply { putString("arguments", encoded) },
                object : IRootResult.Stub() { override fun complete(value: String) { result.complete(value) } })
            val response = withTimeout(30_000) { result.await() }.also { require(it.length <= 131072) }
            WireJson.parseToJsonElement(response).jsonObject.also { require(it["error"] == null) { "Metadata maintenance was not confirmed" } }
        } finally {
            try { withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(id) } } }
            finally { runCatching { binder.unlinkToDeath(death, 0) }; lease.close() }
        }
    }
}
