package io.github.asagnc.aurora.core

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class HttpReplayTransportTest {
    @Test fun socksRouteUsesSelectedProxyAndRemoteDnsWithoutDirectFallback() = runBlocking {
        val root = Files.createTempDirectory("owned-socks-replay")
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val socket = AtomicReference<Socket?>(); val failure = AtomicReference<Throwable?>(); val target = AtomicReference<String>()
        server.soTimeout = 5000
        val worker = thread(isDaemon = true, name = "owned-socks-fixture") {
            try { server.accept().use { client ->
                socket.set(client); client.soTimeout = 5000
                val input = client.getInputStream(); val output = client.getOutputStream()
                check(input.read() == 5)
                val methods = input.read(); check(methods in 1..255); check(input.readNBytes(methods).contains(0))
                output.write(byteArrayOf(5, 0)); output.flush()
                check(input.read() == 5 && input.read() == 1 && input.read() == 0 && input.read() == 3)
                val length = input.read(); check(length in 1..253)
                target.set(input.readNBytes(length).toString(Charsets.US_ASCII)); check(input.readNBytes(2).size == 2)
                output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); output.flush()
                val headers = client.getInputStream().bufferedReader()
                var budget = 0
                while (true) {
                    val line = headers.readLine() ?: error("Incomplete owned HTTP request")
                    budget += line.length; check(budget <= 32768)
                    if (line.isEmpty()) break
                }
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 11\r\nConnection: close\r\n\r\nowned-socks".toByteArray())
                output.flush()
            } } catch (caught: Throwable) { failure.set(caught) }
        }
        try {
            val result = WireJson.parseToJsonElement(HttpReplayTool(root).execute(ToolCall("socks", "http_request", objectOf(
                "url" to string("http://owned-remote-dns.invalid/fixture"), "method" to string("GET"), "route" to string("socks"),
                "proxyHost" to string("127.0.0.1"), "proxyPort" to JsonPrimitive(server.localPort), "timeoutMs" to JsonPrimitive(5000))))).jsonObject
            assertEquals("owned-socks", result.text("preview")); assertEquals("owned-remote-dns.invalid", target.get())
        } finally {
            server.close(); socket.get()?.close(); worker.join(6000)
            root.toFile().deleteRecursively()
            assertFalse(worker.isAlive); failure.get()?.let { throw it }
        }
    }

    @Test fun selfSignedTlsIsRejectedWithoutPublishingOrInstallingTrust() = runBlocking {
        val root = Files.createTempDirectory("owned-tls-replay")
        try {
            val keystore = root.resolve("owned.p12")
            val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
            val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool$suffix")
            val process = ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "owned", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-dname", "CN=127.0.0.1", "-ext", "SAN=ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", "owned-fixture", "-noprompt")
                .redirectErrorStream(true).redirectOutput(root.resolve("keytool.txt").toFile()).start()
            try { check(process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0) }
            finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor() } }
            val keys = KeyStore.getInstance("PKCS12")
            Files.newInputStream(keystore).use { keys.load(it, "owned-fixture".toCharArray()) }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            manager.init(keys, "owned-fixture".toCharArray())
            val context = SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }
            val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.httpsConfigurator = HttpsConfigurator(context)
            server.createContext("/") { it.close(); error("Untrusted TLS must fail before owned HTTP handler") }
            server.start()
            try {
                val result = WireJson.parseToJsonElement(HttpReplayTool(root).execute(ToolCall("tls", "http_request", objectOf(
                    "url" to string("https://127.0.0.1:${server.address.port}/owned"), "method" to string("GET"),
                    "route" to string("direct"), "timeoutMs" to JsonPrimitive(5000))))).jsonObject
                assertEquals("http_outcome_uncertain", result.text("error")); assertEquals("tls_failed", result.text("reason"))
                assertFalse(Files.exists(root.resolve("runs")))
            } finally { server.stop(0) }
        } finally { root.toFile().deleteRecursively() }
    }
}
