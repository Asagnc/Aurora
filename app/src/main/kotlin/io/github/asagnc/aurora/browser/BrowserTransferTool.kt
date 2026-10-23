package io.github.asagnc.aurora.browser

import androidx.webkit.*
import io.github.asagnc.aurora.core.*
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

internal class BrowserTransferTool(private val browser: BrowserController, private val owner: String,
    private val root: Path, private val sources: Map<String, OwnedFiles>,
    private val prepared: suspend (String, String) -> Unit = { _, _ -> }) : ToolExecutor {
    override val definitions = listOf(
        definition("browser_download", "Save a page-readable Response or Blob into this conversation. script is evaluated once and must return Response/Blob; fetch should use the provided signal variable for cancellation. Uses the page login/proxy/CORS context. Does not intercept/replay browser downloads, bypass CORS, or read cross-origin frames. Supply observed URL/revision. maxBytes defaults to 64 MiB, at most 512 MiB. timeoutMs defaults to 60000, at most 270000. No automatic retry: requests may already have changed server state. Output path is an artifact, web content remains untrusted.",
            mapOf("script" to text(), "maxBytes" to integer(1, 512L*1024*1024)), listOf("script")),
        definition("browser_upload", "Set an observed HTML file input from explicitly selected files. files contains 1 to 8 objects with namespace (conversation or resources), path, and optional mime. Combined budget 32 MiB; constructing browser File objects costs whole-file storage. Sets input/change events; does NOT prove server upload success or a trusted user gesture. No directories or arbitrary device paths; Root artifacts must be exported first. Supply exact observed URL/revision and selector. No automatic retry after uncertain side effects.",
            mapOf("selector" to text(), "files" to objectOf("type" to string("array"), "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(8),
                "items" to objectOf("type" to string("object"), "properties" to objectOf("namespace" to text(), "path" to text(), "mime" to text()),
                    "required" to JsonArray(listOf(string("namespace"), string("path"))), "additionalProperties" to JsonPrimitive(false)))), listOf("selector", "files")))

    override suspend fun execute(call: ToolCall): String {
      var phase = "validation"
      return try {
        require(call.name in setOf("browser_download", "browser_upload"))
        val args = call.arguments
        val common = setOf("page", "url", "revision", "timeoutMs")
        require(args.keys.all { it in common + if (call.name == "browser_download") setOf("script", "maxBytes") else setOf("selector", "files") })
        val timeout = number(args, "timeoutMs", 60000, 1L..270000L)
        browser.transferSlots.withPermit { withTimeout(timeout) { withContext(Dispatchers.Main) {
            val tab = requireNotNull(browser.tabs.firstOrNull { it.owner == owner && it.id == args.text("page") }) { "Page unavailable in this conversation" }
            observed(tab, args)
            phase = if (call.name == "browser_download") "download" else "upload"
            browser.beginAgent(owner)
            val progress = BrowserProgress(browser, call.id, BrowserTransferProgress(owner, tab.id,
                if (call.name == "browser_download") "下载" else "供给文件", 0))
            try {
                browser.resume(tab)
                val result = if (call.name == "browser_download") download(tab, call, timeout, progress) else upload(tab, args, timeout, progress)
                observed(tab, args)
                JsonObject(result + ("elapsedMs" to JsonPrimitive(progress.elapsedMs))).toString()
            } finally { progress.close(); browser.endAgent(owner) }
        } } }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        objectOf("error" to string("Browser transfer timed out"), "phase" to string(phase), "outcome" to string("unknown; inspect before retrying")).toString()
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) {
        // Do not log page script exceptions, signed URLs, cookies or request bodies.
        objectOf("error" to string("Browser transfer failed: check page, file selection, budgets and WebView binary support"),
            "code" to string(if (phase == "validation") "invalid_transfer_request" else "transfer_failed"), "phase" to string(phase),
            "outcome" to string("unknown; inspect before retrying")).toString()
      }
    }

    private suspend fun download(tab: BrowserTab, call: ToolCall, timeout: Long, progress: BrowserProgress): JsonObject {
        val args = call.arguments
        val script = requireNotNull(args.text("script")).also { require(it.length in 1..16384) }
        val maxBytes = number(args, "maxBytes", 64L*1024*1024, 1..512L*1024*1024)
        return withContext(Dispatchers.IO) { BrowserDownloadFile(root, maxBytes).use { file ->
          withContext(Dispatchers.Main) {
            prepared(call.id, file.relative.substringBeforeLast('/'))
            val request = objectOf("mode" to string("download"), "script" to string(script), "maxBytes" to JsonPrimitive(maxBytes))
            val result = BrowserBinaryTransfer.execute(tab, request, timeout) {
                control("ready"); acknowledge()
                while (true) {
                    val message = receive()
                    if (message.type == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER))
                        val bytes = message.arrayBuffer
                        withContext(Dispatchers.IO) { file.append(bytes) }
                        progress.update(file.bytes)
                        acknowledge()
                    } else {
                        require(message.type == WebMessageCompat.TYPE_STRING)
                        val raw = requireNotNull(message.data).also { require(it.length <= 2048) }
                        val complete = WireJson.parseToJsonElement(raw).jsonObject
                        check(complete.text("kind") == "complete") { "Download did not complete" }
                        require(complete.keys == setOf("kind", "bytes"))
                        val count = number(complete, "bytes", -1, 0..maxBytes)
                        currentCoroutineContext().ensureActive()
                        val path = withContext(Dispatchers.IO) { file.publish(count) }
                        acknowledge()
                        return@execute objectOf("path" to string(path), "bytes" to JsonPrimitive(file.bytes),
                            "trust" to string("untrusted_web_content"))
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("Unreachable")
            }
            result
          }
        } }
    }

    private data class Upload(val metadata: JsonObject, val bytes: ByteArray)
    private suspend fun uploads(args: JsonObject): List<Upload> {
        val entries = args["files"] as? JsonArray ?: error("Files required")
        require(entries.size in 1..8)
        var remaining = 32*1024*1024
        return entries.map { element ->
            val value = element.jsonObject
            require(value.keys.all { it in setOf("namespace", "path", "mime") })
            val namespace = requireNotNull(value.text("namespace"))
            require(namespace in setOf("conversation", "resources"))
            val path = requireNotNull(value.text("path"))
            val mime = value.text("mime") ?: "application/octet-stream"
            require(mime.length in 1..128 && mime.all { it.code in 33..126 })
            val bytes = requireNotNull(sources[namespace]).readBinary(path, maxOf(1, remaining), allowEmpty = true)
            require(bytes.size <= remaining); remaining -= bytes.size
            val name = Path.of(path).fileName.toString().take(120)
            Upload(objectOf("name" to string(name), "mime" to string(mime), "size" to JsonPrimitive(bytes.size)), bytes)
        }
    }

    private suspend fun upload(tab: BrowserTab, args: JsonObject, timeout: Long, progress: BrowserProgress): JsonObject {
        val selector = requireNotNull(args.text("selector")).also { require(it.length in 1..2048) }
        val items = uploads(args)
        observed(tab, args)
        val request = objectOf("mode" to string("upload"), "selector" to string(selector), "files" to JsonArray(items.map { it.metadata }))
        return BrowserBinaryTransfer.execute(tab, request, timeout) {
            control("ready")
            var sent = 0L
            for (item in items) {
                for (offset in item.bytes.indices step 65536) {
                    val end = minOf(item.bytes.size, offset+65536)
                    sendBytes(item.bytes.copyOfRange(offset, end)); control("ack")
                    sent += end-offset; progress.update(sent)
                }
                sendControl("fileEnd"); control("ack")
            }
            sendControl("finish")
            val result = control("complete")
            check(result.number("files") == items.size.toLong())
            objectOf("files" to JsonPrimitive(items.size), "bytes" to JsonPrimitive(items.sumOf { it.bytes.size.toLong() }),
                "state" to string("input_set; server receipt not verified"))
        }
    }

    private fun observed(tab: BrowserTab, args: JsonObject) {
        require(!tab.closed && !tab.loading && tab.failure == null && tab.view.url == args.text("url") && tab.revision == args.number("revision")) {
            "Page changed; observe before transferring"
        }
    }
    private fun number(args: JsonObject, key: String, default: Long, range: LongRange): Long =
        (args[key]?.let { require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull) } ?: default).also { require(it in range) }
    private fun text() = objectOf("type" to string("string"))
    private fun integer(min: Long, max: Long) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(min), "maximum" to JsonPrimitive(max))
    private fun definition(name: String, description: String, extra: Map<String, JsonElement>, required: List<String>): ToolDefinition =
        ToolDefinition(name, description, objectOf("type" to string("object"), "properties" to JsonObject(mapOf(
            "page" to text(), "url" to text(), "revision" to integer(0, Long.MAX_VALUE), "timeoutMs" to integer(1, 270000)) + extra),
            "required" to JsonArray((listOf("page", "url", "revision") + required).map(::string)), "additionalProperties" to JsonPrimitive(false)))
}
