package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*
import kotlin.test.*

class SocketOwnerTest {
    @Test fun initialNamespaceNeedsLiveKernelQualificationAndExplicitScope() {
        assertTrue(initialUserNamespaceConfiguration("# CONFIG_USER_NS is not set\n".toByteArray()))
        for (text in listOf("", "CONFIG_USER_NS=y\n", "# CONFIG_USER_NS is not set\nCONFIG_USER_NS=y\n",
            "# CONFIG_USER_NS is not set\n# CONFIG_USER_NS is not set\n")) {
            assertFalse(initialUserNamespaceConfiguration(text.toByteArray()))
        }
        val request = SocketOwnerRequest.parse(args())
        val initial = JsonObject(record() + mapOf("userNamespace" to JsonNull, "uidScope" to string("initial_user_namespace")))
        assertFails { socketOwnerCompletion(command(initial), request) }
        assertEquals("exact_socket_uid", socketOwnerCompletion(command(initial), request, true).text("status"))
        assertFails { socketOwnerCompletion(command(JsonObject(initial - "uidScope")), request, true) }
        assertEquals("--initial-user-namespace", request.argv("/worker", true).last())
    }
    private fun args() = objectOf("family" to string("ipv4"), "transport" to string("tcp"),
        "localAddress" to string("127.0.0.1"), "localPort" to JsonPrimitive(12345),
        "remoteAddress" to string("127.0.0.1"), "remotePort" to JsonPrimitive(54321))
    private fun match() = objectOf("uid" to JsonPrimitive(10001), "inode" to JsonPrimitive(20), "cookie" to string("0000000000000021"),
        "state" to JsonPrimitive(1), "interfaceIndex" to JsonPrimitive(0))
    private fun record() = JsonObject(args() + objectOf("protocol" to JsonPrimitive(1), "scope" to string("current_socket_snapshot"),
        "status" to string("exact_socket_uid"), "bootId" to string("00000000-0000-0000-0000-000000000001"),
        "startedBootMs" to JsonPrimitive(100), "finishedBootMs" to JsonPrimitive(110), "observedUnixMs" to JsonPrimitive(100000),
        "netNamespace" to string("4:123"), "userNamespace" to string("4:124"), "overflowUid" to JsonPrimitive(65534), "matches" to JsonArray(listOf(match()))))
    private fun command(value: JsonObject = record()) = objectOf("exitCode" to JsonPrimitive(0), "timedOut" to JsonPrimitive(false),
        "stopped" to JsonPrimitive(false), "captureComplete" to JsonPrimitive(true), "stdout" to objectOf("text" to string(value.toString()),
            "previewTruncated" to JsonPrimitive(false), "artifactTruncated" to JsonPrimitive(false)))

    @Test fun numericTupleRejectsDnsZonesMixedFamiliesAndCoercion() {
        for ((key, value) in listOf("localAddress" to string("localhost"), "localAddress" to string("127.1"),
            "localAddress" to string("0177.0.0.1"), "remoteAddress" to string("::1"), "localPort" to string("12345"),
            "localPort" to JsonPrimitive(0), "remotePort" to JsonPrimitive(65536), "transport" to string("icmp"))) {
            assertFails { SocketOwnerRequest.parse(JsonObject(args() + (key to value))) }
        }
        val ipv6 = JsonObject(args() + mapOf("family" to string("ipv6"), "localAddress" to string("::1"), "remoteAddress" to string("2001:db8::1")))
        assertEquals(7, SocketOwnerRequest.parse(ipv6).argv("/worker").size)
        val mapped = SocketOwnerRequest.parse(JsonObject(ipv6 + ("localAddress" to string("::ffff:127.0.0.1"))))
        assertEquals("0:0:0:0:0:ffff:7f00:1", mapped.localAddress)
        assertFails { SocketOwnerRequest.parse(JsonObject(ipv6 + ("localAddress" to string("fe80::1%lo")))) }
    }

    @Test fun exactUnknownAndAllCandidatesRemainDistinct() {
        val request = SocketOwnerRequest.parse(args())
        assertEquals("exact_socket_uid", socketOwnerCompletion(command(), request).text("status"))
        val multiple = JsonObject(record() + mapOf("status" to string("multiple"), "matches" to JsonArray(listOf(match(), match()))))
        assertEquals(2, socketOwnerCompletion(command(multiple), request).array("matches").size)
        val unknown = JsonObject(record() + mapOf("status" to string("unknown"), "matches" to JsonArray(emptyList())))
        assertEquals("unknown", socketOwnerCompletion(command(unknown), request).text("status"))
    }

    @Test fun incompleteWrongTupleOrAmbiguousProofNeverBecomesUnique() {
        val request = SocketOwnerRequest.parse(args())
        for (change in listOf(mapOf("remotePort" to JsonPrimitive(1)), mapOf("finishedBootMs" to JsonPrimitive(4000)),
            mapOf("matches" to JsonArray(listOf(match(), match()))), mapOf("matches" to JsonArray(emptyList())),
            mapOf("matches" to JsonArray(listOf(JsonObject(match() + ("inode" to JsonPrimitive(0)))))),
            mapOf("matches" to JsonArray(listOf(JsonObject(match() + ("cookie" to string("ffffffffffffffff")))))))) {
            assertFails { socketOwnerCompletion(command(JsonObject(record() + change)), request) }
        }
        for ((key, value) in listOf("captureComplete" to JsonPrimitive(false), "exitCode" to JsonPrimitive(1), "stopped" to JsonPrimitive(true)))
            assertFails { socketOwnerCompletion(JsonObject(command() + (key to value)), request) }
    }
}
