package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*
import kotlin.test.*

class FridaAttachRequestTest {
    private val args = objectOf("action" to string("attach"), "pid" to JsonPrimitive(123), "startTicks" to JsonPrimitive(456),
        "source" to string("conversation"), "path" to string("imports/hook.js"), "scriptSha256" to string("a".repeat(64)))

    @Test fun targetIdentityAndSelectedScriptRemainExplicit() {
        val request = FridaAttachRequest.parse(args)
        assertEquals(RootTargetIdentity(123, 456), request.target)
        assertEquals("imports/hook.js", request.path)
        assertFalse(request.java); assertEquals(60_000L, request.observeMs)
        assertTrue(FridaAttachRequest.parse(JsonObject(args + ("java" to JsonPrimitive(true)))).java)
    }

    @Test fun ambiguousParametersAndUnboundedTargetsAreRejectedBeforeDispatch() {
        val changes = listOf("pid" to string("123"), "pid" to JsonPrimitive(0), "pid" to JsonPrimitive(2147483648L),
            "startTicks" to JsonPrimitive(0), "java" to string("true"), "source" to string("external"),
            "path" to string("../foreign.js"), "path" to string("/system/hook.js"), "path" to JsonPrimitive(123),
            "scriptSha256" to string("not-a-version"), "observeMs" to JsonPrimitive(250001),
            "observeMs" to JsonPrimitive(1.5), "argv" to string("--unmanaged"))
        changes.forEach { (key, value) -> assertFails { FridaAttachRequest.parse(JsonObject(args + (key to value))) } }
        assertFails { FridaAttachRequest.parse(JsonObject(args - "startTicks")) }
    }
}
