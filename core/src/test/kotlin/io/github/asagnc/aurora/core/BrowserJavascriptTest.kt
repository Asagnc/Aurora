package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import kotlin.test.*

class BrowserJavascriptTest {
    @Test fun emittedPageScriptsHandlePromisesCancellationAndBoundedSerialization() {
        val cases = mutableListOf<JsonObject>()
        fun sync(name: String, script: String, expected: JsonElement = JsonNull, error: Boolean = false, clipped: Boolean = false) {
            cases += objectOf("mode" to string("sync"), "name" to string(name),
                "code" to string(BrowserJavascript.synchronous(script)), "expected" to expected,
                "error" to JsonPrimitive(error), "truncated" to JsonPrimitive(clipped))
        }
        fun asynchronous(name: String, script: String, expected: JsonElement = JsonNull, mode: String = "async", error: Boolean = false) {
            val id = UUID.randomUUID().toString()
            cases += objectOf("mode" to string(mode), "name" to string(name), "id" to string(id),
                "code" to string(BrowserJavascript.setup(id, script, if (mode == "pending") 1 else 1000)),
                "cleanup" to string(BrowserJavascript.cleanup(id)), "expected" to expected, "error" to JsonPrimitive(error))
        }
        sync("sync object", "({n:42})", objectOf("n" to JsonPrimitive(42)))
        sync("undefined", "undefined")
        sync("quoted large output", "'\\\"'.repeat(100000)", clipped = true)
        sync("unicode boundary", "'😀'.repeat(40000)", clipped = true)
        sync("cyclic value", "(()=>{const x={};x.self=x;return x})()", error = true)
        sync("bigint", "1n", error = true)
        sync("function", "(()=>42)", error = true)
        sync("Promise requires opt in", "Promise.reject(Error('pending'))", error = true)
        asynchronous("fulfilled Promise", "Promise.resolve({n:42})", objectOf("n" to JsonPrimitive(42)))
        asynchronous("async plain value", "42", JsonPrimitive(42))
        asynchronous("rejected Promise", "Promise.reject(Error('fixture'))", error = true)
        asynchronous("async cyclic value", "Promise.resolve().then(()=>{const x={};x.self=x;return x})", error = true)
        asynchronous("late completion after cancel", "new Promise(resolve=>{window.__auroraResolveLate=resolve})", mode = "cancel")
        asynchronous("pending receiver cleanup deadline", "new Promise(()=>{})", mode = "pending")
        runNode(JsonArray(cases).toString(), cases.size)
    }

    @Test fun untrustedRendererEnvelopesCannotHideMissingFieldsOrOversizedDepth() {
        val valid = """{"ok":true,"value":42,"truncated":false}"""
        assertEquals(42L, decodeBrowserScriptResult(valid).number("value"))
        for (invalid in listOf("null", "[]", """{"ok":true}""",
            """{"ok":true,"value":42,"truncated":"false"}""",
            """{"ok":true,"value":{},"truncated":true}""",
            """{"ok":true,"value":42,"truncated":false,"extra":1}""",
            """{"ok":false,"error":"fixture"}""", "[".repeat(65) + "0" + "]".repeat(65), "x".repeat(40001))) {
            assertFails { decodeBrowserScriptResult(invalid) }
        }
    }

    @Test fun webViewCallbackMustContainTheSerializedResultInsteadOfNullOrRawObject() {
        val valid = """{"ok":true,"value":"quoted value","truncated":false}"""
        assertEquals("quoted value", decodeBrowserJavascriptCallback(string(valid).toString())["value"]?.jsonPrimitive?.content)
        for (invalid in listOf("null", valid, "[]", "x".repeat(80003))) {
            assertFails { decodeBrowserJavascriptCallback(invalid) }
        }
    }

    private fun runNode(input: String, expected: Int) {
        val output = Files.createTempFile("aurora-browser-js-", ".log")
        val script = requireNotNull(System.getProperty("aurora.browser.scriptTest"))
        val process = ProcessBuilder("node", script).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(input) }
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Browser JS host test did not finish")
            val text = Files.readString(output)
            assertEquals(0, process.exitValue(), text)
            assertTrue(text.contains("$expected cases passed"), text)
            println(text)
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            process.inputStream.close(); process.errorStream.close(); process.outputStream.close()
            Files.deleteIfExists(output)
        }
    }
}
