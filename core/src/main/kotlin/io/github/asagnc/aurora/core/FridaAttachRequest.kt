package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

data class FridaAttachRequest(val target: RootTargetIdentity?, val source: String, val path: String,
    val scriptSha256: String, val java: Boolean, val observeMs: Long, val spawnPackage: String? = null) {
    companion object {
        fun parse(args: JsonObject): FridaAttachRequest {
            require(args.keys.all { it in setOf("action", "pid", "startTicks", "package", "source", "path", "scriptSha256", "java", "observeMs") })
            val spawn = args.text("action") == "spawn"
            require(spawn || args.text("action") == "attach")
            fun text(key: String): String {
                val value = args[key]
                require(value is JsonPrimitive && value.isString)
                return value.content
            }
            fun number(key: String, fallback: Long? = null): Long {
                val value = args[key] ?: return requireNotNull(fallback)
                require(value is JsonPrimitive && !value.isString)
                return requireNotNull(value.longOrNull)
            }
            require(if (spawn) "pid" !in args && "startTicks" !in args else "package" !in args)
            val target = if (spawn) null else RootTargetIdentity(
                number("pid").also { require(it in 1..Int.MAX_VALUE.toLong()) }.toInt(), number("startTicks").also { require(it > 0) })
            val program = if (spawn) text("package").also {
                require(it.length <= 255 && it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
            } else null
            val source = text("source").also { require(it in setOf("conversation", "resources", "workspace")) }
            val path = text("path").also { require(it.length <= 4096); skillRelativePath(it) }
            val sha = text("scriptSha256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            val java = args["java"]?.let { require(it is JsonPrimitive && !it.isString); requireNotNull(it.booleanOrNull) } ?: false
            val duration = number("observeMs", 60_000).also { require(it in 1..250_000) }
            return FridaAttachRequest(target, source, path, sha, java, duration, program)
        }
    }
}
