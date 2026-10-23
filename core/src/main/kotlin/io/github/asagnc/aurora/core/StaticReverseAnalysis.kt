package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.json.*

/** Literal argv; output is unique and no shell, overwrite flag or runtime plugin installation is added. */
fun staticReverseArguments(args: JsonObject, id: String): List<String> {
    require(UUID.fromString(id).toString() == id)
    require(args.keys.all { it in setOf("tool", "mode", "path", "heapMiB", "threads", "timeoutMs") })
    val path = args["path"]
    require(path is JsonPrimitive && path.isString)
    val tool = args.text("tool"); val mode = args.text("mode")
    val input = "/mnt/" + skillRelativePath(requireNotNull(args.text("path"))).toString().replace('\\', '/')
    fun integer(key: String, default: Long, range: LongRange): Long {
        val value = args[key]
        val number = if (value == null) default else {
            require(value is JsonPrimitive && !value.isString); requireNotNull(value.longOrNull)
        }
        return number.also { require(it in range) }
    }
    val heap = integer("heapMiB", 768, 128L..2048L)
    val threads = integer("threads", 4, 1L..8L)
    val java = listOf("/opt/jre/jdk-27+35-jre/bin/java", "--enable-native-access=ALL-UNNAMED", "-Xms32m", "-Xmx${heap}m", "-XX:ActiveProcessorCount=$threads")
    val output = "/mnt/reverse/$id"
    return when (tool) {
        "jadx" -> {
            require(mode == "java")
            java + listOf("-Djdk.util.zip.disableZip64ExtraFieldValidation=true", "-cp", "/opt/jadx/lib/jadx-1.5.6-all.jar", "jadx.cli.JadxCLI", "-j", "$threads", "-d", output, input)
        }
        "apktool" -> {
            require(mode in setOf("manifest", "resources", "smali"))
            java + listOf("-jar", "/opt/apktool/apktool.jar", "d", "-j", "$threads", "-p",
                "/root/.cache/aurora/apktool/" + StaticReverseTools.bundleId, "-o", output) +
                when (mode) { "manifest" -> listOf("--only-manifest", "--no-src")
                    "resources" -> listOf("--no-src"); else -> listOf("--no-res") } + input
        }
        else -> error("Unknown reverse tool")
    }
}

class StaticReverseAnalysisTool(private val run: suspend (String, List<String>, Long) -> JsonObject,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    private val projection = LinuxCommandTool(run, prepared)
    override val definitions = listOf(ToolDefinition("reverse_analyze",
        "Run qualified shared JADX/Apktool in Root Linux. First install and probe via shared_toolchain. " +
        "path is a relative file already in this conversation Linux project (/mnt, .aurora-linux/project); " +
        "stage Android imports explicitly with the existing Root command/file tools. tool=jadx requires mode=java; " +
        "apktool accepts manifest/resources/smali. Unique output=.aurora-linux/project/reverse/<runId>, " +
        "logs=runs/<runId>. Partial output is retained and never reported as complete on failure; no automatic retry. " +
        "JVM heap defaults768MiB (not a total RSS limit), threads4; configurable128..2048 and1..8. " +
        "No APK rebuilding or signing is implied. Read outputs on demand with file tools.", objectOf(
            "type" to string("object"), "properties" to objectOf(
                "tool" to enumeration("jadx", "apktool"), "mode" to enumeration("java", "manifest", "resources", "smali"),
                "path" to objectOf("type" to string("string")), "heapMiB" to integer(128, 2048),
                "threads" to integer(1, 8), "timeoutMs" to integer(1, 270000)),
            "required" to JsonArray(listOf("tool", "mode", "path").map(::string)), "additionalProperties" to JsonPrimitive(false))))
    override suspend fun execute(call: ToolCall): String {
        require(call.name == "reverse_analyze")
        val id = UUID.randomUUID().toString()
        val argv = staticReverseArguments(call.arguments, id)
        val raw = call.arguments["timeoutMs"]
        require(raw == null || raw is JsonPrimitive && !raw.isString && raw.longOrNull != null)
        val timeout = (raw as? JsonPrimitive)?.longOrNull ?: 120000L
        require(timeout in 1..270000)
        prepared(call.id, "runs/$id")
        val proof = run(id, argv, timeout)
        return JsonObject(projection.project(id, proof) + mapOf("bundle" to string(StaticReverseTools.bundleId),
            "output" to string(".aurora-linux/project/reverse/$id"),
            "quality" to string("Inspect tool warnings and output; successful exit does not prove complete decompilation"))).toString()
    }
    private fun enumeration(vararg values: String) = objectOf("type" to string("string"), "enum" to JsonArray(values.map(::string)))
    private fun integer(min: Int, max: Int) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(min), "maximum" to JsonPrimitive(max))
}
