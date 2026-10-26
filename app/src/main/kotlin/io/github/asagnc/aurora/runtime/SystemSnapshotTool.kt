package io.github.asagnc.aurora.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.StatFs
import android.system.Os
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** On-demand ordinary Android APIs. No shell, permission request, Root connection or polling. */
internal class SystemSnapshotTool(private val context: Context, private val rootState: () -> String) : ToolExecutor {
    private val sections = listOf("hardware", "memory", "storage", "battery", "root_connection")
    override val definitions = listOf(ToolDefinition("system_snapshot",
        "Read an on-demand structured Android snapshot: hardware/OS, approximate system RAM, app-data filesystem space, " +
        "battery properties and cached Root connection state. sections optionally selects unique names. " +
        "No unique device identifiers, installed-app inventory, shell, new permission or Root connection. " +
        "Unavailable sections/properties remain explicit; values are sampled over an interval, not atomically or as a performance benchmark. " +
        "Filesystem available space is not an allocation/quota guarantee; cached Root connection state is not runtime qualification.",
        objectOf("type" to string("object"), "properties" to objectOf("sections" to objectOf("type" to string("array"),
            "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(sections.size),
            "items" to objectOf("type" to string("string"), "enum" to JsonArray(sections.map(::string))))),
            "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        require(call.name == "system_snapshot" && call.arguments.keys.all { it == "sections" })
        val selected = call.arguments["sections"]?.let { value ->
            require(value is JsonArray)
            value.map { require(it is JsonPrimitive && it.isString); it.content }
        } ?: sections
        require(selected.isNotEmpty() && selected.size <= sections.size && selected.distinct().size == selected.size && selected.all { it in sections })
        val started = SystemClock.elapsedRealtime()
        val values = selected.associateWith { section ->
            currentCoroutineContext().ensureActive()
            try { objectOf("available" to JsonPrimitive(true), "value" to sample(section)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { objectOf("available" to JsonPrimitive(false), "reason" to string("android_api_unavailable_or_denied")) }
        }
        objectOf("protocol" to JsonPrimitive(1), "atomic" to JsonPrimitive(false), "clock" to string("elapsedRealtimeMs"),
            "started" to JsonPrimitive(started), "finished" to JsonPrimitive(SystemClock.elapsedRealtime()), "sections" to JsonObject(values)).toString()
    }

    private fun sample(section: String): JsonObject = when (section) {
        "hardware" -> {
            val kernel = Os.uname()
            objectOf("manufacturer" to string(Build.MANUFACTURER.take(128)), "model" to string(Build.MODEL.take(128)),
                "sdk" to JsonPrimitive(Build.VERSION.SDK_INT), "release" to string(Build.VERSION.RELEASE.take(128)),
                "securityPatch" to string(Build.VERSION.SECURITY_PATCH.take(32)),
                "abis" to JsonArray(Build.SUPPORTED_ABIS.take(8).map { string(it.take(64)) }),
                "kernel" to string(kernel.release.take(256)), "architecture" to string(kernel.machine.take(64)))
        }
        "memory" -> {
            val memory = ActivityManager.MemoryInfo(); requireNotNull(context.getSystemService(ActivityManager::class.java)).getMemoryInfo(memory)
            objectOf("totalBytes" to JsonPrimitive(memory.totalMem), "availableBytes" to JsonPrimitive(memory.availMem),
                "lowMemoryThresholdBytes" to JsonPrimitive(memory.threshold), "lowMemory" to JsonPrimitive(memory.lowMemory),
                "approximate" to JsonPrimitive(true))
        }
        "storage" -> {
            val filesystem = StatFs(context.filesDir.absolutePath)
            objectOf("scope" to string("app_data_filesystem"), "totalBytes" to JsonPrimitive(filesystem.totalBytes),
                "availableBytes" to JsonPrimitive(filesystem.availableBytes), "allocationGuaranteed" to JsonPrimitive(false))
        }
        "battery" -> battery()
        "root_connection" -> objectOf("cachedState" to string(rootState().take(32)), "runtimeQualified" to JsonNull,
            "connectionRequested" to JsonPrimitive(false))
        else -> error("Unknown snapshot section")
    }

    private fun battery(): JsonObject {
        val battery = requireNotNull(context.getSystemService(BatteryManager::class.java))
        fun property(id: Int): Int? = battery.getIntProperty(id).takeUnless { it == Int.MIN_VALUE }
        val capacity = property(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        return objectOf("capacityPercent" to (capacity?.let(::JsonPrimitive) ?: JsonNull),
            "charging" to JsonPrimitive(battery.isCharging),
            "currentNowMicroamps" to (property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let(::JsonPrimitive) ?: JsonNull),
            "currentAverageMicroamps" to (property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.let(::JsonPrimitive) ?: JsonNull),
            "currentSign" to string("raw_platform_value"))
    }
}
