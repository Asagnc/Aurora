package io.github.asagnc.aurora.core

import java.net.InetAddress
import java.net.Inet6Address
import kotlinx.serialization.json.*

data class SocketOwnerRequest(val family: String, val transport: String, val localAddress: String,
    val localPort: Int, val remoteAddress: String, val remotePort: Int) {
    fun argv(binary: String, initialUserNamespaceOnly: Boolean = false) =
        listOf(binary, family, transport, localAddress, localPort.toString(), remoteAddress, remotePort.toString()) +
            if (initialUserNamespaceOnly) listOf("--initial-user-namespace") else emptyList()

    companion object {
        private fun address(text: String, family: String): String {
            require(text.length in 2..45 && text.all { it in "0123456789abcdefABCDEF:." })
            require(if (family == "ipv4") ':' !in text && text.split('.').size == 4 else ':' in text)
            if (family == "ipv4") require(text.split('.').all { it.isNotEmpty() && it.length <= 3 && it.toInt() in 0..255 && (it == "0" || !it.startsWith('0')) })
            var value = InetAddress.getByName(text) // Numeric-only grammar above prevents DNS or scoped interface lookup.
            // Java collapses IPv4-mapped IPv6 literals to Inet4Address. The
            // kernel still reports those sockets as AF_INET6 in SOCK_DIAG.
            if (family == "ipv6" && value.address.size == 4) {
                value = Inet6Address.getByAddress(null, ByteArray(10) + byteArrayOf(-1, -1) + value.address, -1)
            }
            require(value.address.size == if (family == "ipv4") 4 else 16)
            return requireNotNull(value.hostAddress)
        }
        fun parse(args: JsonObject): SocketOwnerRequest {
            require(args.keys == setOf("family", "transport", "localAddress", "localPort", "remoteAddress", "remotePort"))
            val family = args.requiredText("family").also { require(it in setOf("ipv4", "ipv6")) }
            val transport = args.requiredText("transport").also { require(it in setOf("tcp", "udp")) }
            fun port(key: String, minimum: Int): Int {
                val value = args[key]; require(value is JsonPrimitive && !value.isString)
                return requireNotNull(value.intOrNull).also { require(it in minimum..65535) }
            }
            return SocketOwnerRequest(family, transport, address(args.requiredText("localAddress"), family), port("localPort", 1),
                address(args.requiredText("remoteAddress"), family), port("remotePort", 0))
        }
    }
}

/** A completed bounded scan is required, including for unknown/multiple outcomes. */
fun socketOwnerCompletion(command: JsonObject, request: SocketOwnerRequest, initialUserNamespaceOnly: Boolean = false): JsonObject {
    require(command["error"] == null && command.number("exitCode") == 0L)
    require(command["timedOut"] == JsonPrimitive(false) && command["stopped"] == JsonPrimitive(false) && command["captureComplete"] == JsonPrimitive(true))
    val stdout = requireNotNull(command["stdout"] as? JsonObject)
    require(stdout["previewTruncated"] == JsonPrimitive(false) && stdout["artifactTruncated"] == JsonPrimitive(false))
    val text = requireNotNull(stdout.text("text"))
    require(text.toByteArray(Charsets.UTF_8).size <= 8192 && jsonWithinDepth(text, 5))
    val value = WireJson.parseToJsonElement(text).jsonObject
    fun numeric(record: JsonObject, key: String): Long {
        val item = record[key]; require(item is JsonPrimitive && !item.isString)
        return requireNotNull(item.longOrNull)
    }
    require(numeric(value, "protocol") == 1L && value.text("scope") == "current_socket_snapshot")
    require(SocketOwnerRequest.parse(JsonObject(value.filterKeys { it in setOf("family", "transport", "localAddress", "localPort", "remoteAddress", "remotePort") })) == request)
    require(value.text("bootId")?.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) == true)
    require(value.text("netNamespace")?.matches(Regex("[0-9]{1,20}:[0-9]{1,20}")) == true)
    if (value["userNamespace"] == JsonNull) {
        require(initialUserNamespaceOnly && value.text("uidScope") == "initial_user_namespace")
    } else {
        require(value.text("userNamespace")?.matches(Regex("[0-9]{1,20}:[0-9]{1,20}")) == true)
        require(value.text("uidScope") in setOf(null, "recorded_user_namespace"))
    }
    val start = numeric(value, "startedBootMs"); val end = numeric(value, "finishedBootMs")
    require(start >= 0 && end >= start && end - start <= 3000 && numeric(value, "observedUnixMs") >= 0)
    val overflow = numeric(value, "overflowUid").also { require(it in 0..0xffffffffL) }
    val matches = requireNotNull(value["matches"] as? JsonArray).also { require(it.size <= 32) }
    matches.forEach { item ->
        val record = item.jsonObject
        for (key in listOf("uid", "inode", "interfaceIndex")) require(numeric(record, key) in 0..0xffffffffL)
        require(numeric(record, "state") in 0..255)
        require(record.text("cookie")?.matches(Regex("[a-f0-9]{16}")) == true)
    }
    when (value.text("status")) {
        "exact_socket_uid" -> {
            require(matches.size == 1 && request.remotePort != 0)
            val item = matches.single().jsonObject
            require(item.number("inode") != 0L && item.number("uid") != overflow && item.text("cookie") != "ffffffffffffffff" && item.number("state") != 6L)
        }
        "multiple" -> require(matches.size > 1)
        "unknown" -> require(matches.size <= 1)
        else -> error("Invalid socket snapshot status")
    }
    return value
}
