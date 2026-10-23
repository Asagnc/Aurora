package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BrowserTransferTest {
    @Test fun productionScriptsStreamWithBackpressureAndPopulateSelectedFiles() {
        val cases = mutableListOf<JsonObject>()
        fun add(name: String, request: JsonObject, mode: String = "download", extras: Map<String, JsonElement> = emptyMap()) {
            val id = UUID.randomUUID().toString()
            cases += JsonObject(mapOf("name" to string(name), "id" to string(id), "mode" to string(mode),
                "code" to string(BrowserTransferJavascript.setup(id, request, 2000)),
                "cleanup" to string(BrowserTransferJavascript.cleanup(id))) + extras)
        }
        fun download(name: String, script: String, size: Int = 0, max: Int = 200000, mode: String = "download", error: Boolean = false) =
            add(name, objectOf("mode" to string("download"), "script" to string(script), "maxBytes" to JsonPrimitive(max)), mode,
                mapOf("size" to JsonPrimitive(size), "error" to JsonPrimitive(error)))
        download("Response chunking", "new Response(Uint8Array.from({length:150000},(_,i)=>i%251))", 150000)
        download("Blob chunking", "new Blob([Uint8Array.from({length:70000},(_,i)=>i%251)])", 70000)
        download("response metadata", "new Response(null,{headers:{'Content-Type':'application/pdf','Content-Disposition':'attachment; filename=report.pdf'}})")
        download("oversize metadata discarded", "new Response(null,{headers:{'Content-Type':'a'.repeat(129),'Content-Disposition':'b'.repeat(513)}})")
        download("empty Blob", "new Blob([])")
        download("empty response", "new Response(null,{status:204})")
        download("oversize response", "new Response(new Uint8Array(100))", max = 64, error = true)
        download("HTTP failure", "new Response('denied',{status:403})", error = true)
        download("script rejection", "Promise.reject(Error('secret request URL'))", error = true)
        download("wrong result type", "42", error = true)
        download("reader rejection", "new Response(new ReadableStream({start(c){c.error(Error('fixture'))}}))", error = true)
        download("cancel while awaiting disk", "new Blob([new Uint8Array(100)])", mode = "cancel")
        download("unexpected disk acknowledgement", "new Blob([new Uint8Array(100)])", mode = "badAck")
        for ((name, sizes, accept) in listOf(Triple("single upload", listOf(150000), ""),
            Triple("universal MIME wildcard", listOf(4), "*/*"),
            Triple("MIME family wildcard", listOf(4), "application/*"),
            Triple("multiple including empty", listOf(0,70000), ".bin"), Triple("accept mismatch", listOf(1), "image/png"))) {
            add(name, objectOf("mode" to string("upload"), "selector" to string("#file"), "files" to JsonArray(sizes.map {
                objectOf("name" to string("fixture.bin"), "mime" to string("application/octet-stream"), "size" to JsonPrimitive(it))
            })), "upload", mapOf("sizes" to JsonArray(sizes.map { JsonPrimitive(it) }), "accept" to string(accept),
                "error" to JsonPrimitive(accept == "image/png")))
        }
        val log = Files.createTempFile("aurora-transfer-", ".log")
        val process = ProcessBuilder("node", requireNotNull(System.getProperty("aurora.browser.transferTest")))
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(JsonArray(cases).toString()) }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Transfer JS did not finish")
            val output = Files.readString(log)
            assertEquals(0, process.exitValue(), output)
            assertTrue(output.contains("${cases.size} cases passed"), output)
            println(output)
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            process.inputStream.close(); process.errorStream.close(); process.outputStream.close(); Files.deleteIfExists(log)
        }
    }

    @Test fun controlEnvelopesRejectDeepUnknownAndWrongTypedMessagesWithoutLeakingErrors() {
        assertEquals(0L, decodeBrowserTransferControl("""{"kind":"complete","bytes":0}""", "complete").number("bytes"))
        assertEquals("ack", decodeBrowserTransferControl("""{"kind":"ack"}""", "ack").text("kind"))
        for (raw in listOf("null", "[]", "x".repeat(2049), "[".repeat(65)+"0"+"]".repeat(65),
            """{"kind":"complete","bytes":"2"}""", """{"kind":"complete","bytes":-1}""",
            """{"kind":"ready","extra":42}""", """{"kind":"complete"}""", """{"kind":"complete","bytes":1,"files":1}""")) {
            assertFails { decodeBrowserTransferControl(raw, "complete") }
        }
        val failure = assertFails { decodeBrowserTransferControl("""{"kind":"error","error":"secret-request-url"}""", "ready") }
        assertFalse(failure.message.orEmpty().contains("secret-request-url"))
    }

    @Test fun onlyKnownExactPageErrorCodesReachDiagnostics() {
        val known = assertFailsWith<BrowserTransferFailure> {
            decodeBrowserTransferControl("""{"kind":"error","code":"download_budget_exceeded"}""", "complete")
        }
        assertEquals("download_budget_exceeded", known.code)
        for (raw in listOf(
            """{"kind":"error","code":"https://secret.example/token"}""",
            """{"kind":"error","code":"stream_read","extra":"secret"}""",
            """{"kind":"error","error":"secret"}""")) {
            val failure = assertFailsWith<BrowserTransferFailure> { decodeBrowserTransferControl(raw, "ready") }
            assertEquals("page_transfer_failed", failure.code)
            assertFalse(failure.message.orEmpty().contains("secret"))
        }
    }

    @Test fun incompleteAndOverBudgetDownloadsNeverBecomeCompleteArtifacts() {
        val root = Files.createTempDirectory("aurora-transfer-files-")
        try {
            val file = BrowserDownloadFile(root, 10)
            val relative = file.relative
            file.use {
                it.append(byteArrayOf(1,2,3))
                assertFails { it.append(ByteArray(8)) }
                assertEquals(3, it.bytes)
                assertFails { it.publish(4) }
            }
            assertFalse(Files.exists(root.resolve(relative)))
            assertFalse(Files.exists(root.resolve(relative + ".partial")))
            BrowserDownloadFile(root, 10).use {
                it.append(byteArrayOf(1,2,3));val path=it.publish(3)
                assertContentEquals(byteArrayOf(1,2,3), Files.readAllBytes(root.resolve(path)))
                assertFails { it.append(byteArrayOf(4)) };assertFails { it.publish(3) }
            }
            BrowserDownloadFile(root, 1).use { assertEquals(0, Files.size(root.resolve(it.publish(0)))) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun uploadEmptyFileIsExplicitAndReadBudgetStillApplies() = runTest {
        val root = Files.createTempDirectory("aurora-upload-")
        try {
            Files.write(root.resolve("empty.bin"), byteArrayOf())
            Files.write(root.resolve("large.bin"), ByteArray(2))
            val files = OwnedFiles(root)
            assertFails { files.readBinary("empty.bin", 1) }
            assertContentEquals(byteArrayOf(), files.readBinary("empty.bin", 1, allowEmpty = true))
            assertFails { files.readBinary("large.bin", 1, allowEmpty = true) }
            assertFails { files.readBinary("../outside", 1, allowEmpty = true) }
        } finally { root.toFile().deleteRecursively() }
    }
}
