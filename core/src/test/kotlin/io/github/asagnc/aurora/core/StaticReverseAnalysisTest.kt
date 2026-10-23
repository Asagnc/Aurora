package io.github.asagnc.aurora.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class StaticReverseAnalysisTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private fun args(tool: String = "jadx", mode: String = "java", path: String = "input file.apk") =
        objectOf("tool" to string(tool), "mode" to string(mode), "path" to string(path))
    @Test fun plansLiteralArgumentsWithoutShellOrOverwriteAndIsolatesFrameworkCache() {
        val jadx = staticReverseArguments(args(path = "dollar$(literal);.apk"), id)
        assertEquals("/opt/jre/jdk-27+35-jre/bin/java", jadx.first())
        assertEquals("/mnt/dollar$(literal);.apk", jadx.last())
        assertTrue("/bin/sh" !in jadx && "--force" !in jadx)
        assertEquals("/mnt/reverse/$id", jadx[jadx.indexOf("-d") + 1])
        for (mode in listOf("manifest", "resources", "smali")) {
            val apktool = staticReverseArguments(args("apktool", mode), id)
            assertEquals("/root/.cache/aurora/apktool/" + StaticReverseTools.bundleId, apktool[apktool.indexOf("-p") + 1])
            assertTrue("-f" !in apktool)
        }
    }
    @Test fun rejectsEscapesInvalidModesUnknownParametersAndWrongTypedBudgets() {
        for (value in listOf(args(path = "../escape"), args(path = "/outside"), args(mode = "smali"),
            JsonObject(args() + ("heapMiB" to JsonPrimitive(99999))),
            JsonObject(args() + ("path" to JsonPrimitive(123))),
            JsonObject(args() + ("threads" to string("4"))), JsonObject(args() + ("extra" to string("ignored")))))
            assertFails { staticReverseArguments(value, id) }
    }
    @Test fun failedGuestExitIsPreservedAndOutputDoesNotImplySuccess() = runTest {
        var artifact: String? = null
        val tool = StaticReverseAnalysisTool({ run, _, _ -> objectOf("protocol" to JsonPrimitive(1), "runId" to string(run),
            "stopped" to JsonPrimitive(true), "completion" to objectOf("scopeRemoved" to JsonPrimitive(true),
                "exitCode" to JsonPrimitive(1), "interrupted" to JsonPrimitive(false), "timedOut" to JsonPrimitive(false))) },
            { _, path -> artifact = path })
        val result = WireJson.parseToJsonElement(tool.execute(ToolCall("call", "reverse_analyze", args()))).jsonObject
        assertEquals(false, result["success"]?.jsonPrimitive?.boolean)
        assertEquals(1L, result.number("exitCode"))
        assertTrue(requireNotNull(result.text("output")).startsWith(".aurora-linux/project/reverse/"))
        assertTrue(requireNotNull(artifact).startsWith("runs/"))
    }
}
