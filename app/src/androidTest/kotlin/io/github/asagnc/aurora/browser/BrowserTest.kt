package io.github.asagnc.aurora.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import java.util.UUID
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun delete(browser: BrowserController, owner: String) = runBlocking { browser.deleteOwner(owner) }

    @Test fun profilesIsolateStorageAndClearOnlyTheirOwner() {
        lateinit var browser: BrowserController
        lateinit var first: BrowserTab
        lateinit var other: BrowserTab
        var verifier: BrowserTab? = null
        val owner = UUID.randomUUID().toString()
        val secondOwner = UUID.randomUUID().toString()
        main {
            browser = BrowserController(instrumentation.targetContext, saveProxy = false)
            assertTrue("WebView must support conversation profiles", browser.supported)
            first = requireNotNull(browser.open(owner))
            other = requireNotNull(browser.open(secondOwner))
        }
        try {
            load(first, "<script>localStorage.setItem('fixture','owned');document.cookie='fixture=owned; Secure; SameSite=Lax'</script>")
            load(other, "<script>localStorage.setItem('fixture','other')</script>")
            assertEquals("\"owned\"", js(first, "localStorage.getItem('fixture')"))
            assertEquals("\"other\"", js(other, "localStorage.getItem('fixture')"))
            assertTrue(js(first, "document.cookie").orEmpty().contains("fixture=owned"))
            assertFalse(js(other, "document.cookie").orEmpty().contains("fixture=owned"))
            delete(browser, owner)
            main {
                assertFalse(browser.tabs.contains(first))
                assertTrue(browser.tabs.contains(other))
                assertNull(browser.open(owner))
                val view = WebView(instrumentation.targetContext)
                WebViewCompat.setProfile(view, "aurora-$owner")
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                verifier = BrowserTab(owner, view)
            }
            load(requireNotNull(verifier), "<p>After cleanup</p>")
            assertEquals("null", js(requireNotNull(verifier), "localStorage.getItem('fixture')"))
            assertEquals("\"\"", js(requireNotNull(verifier), "document.cookie"))
            assertEquals("\"other\"", js(other, "localStorage.getItem('fixture')"))
        } finally {
            main { verifier?.view?.destroy() }
            delete(browser, owner)
            delete(browser, secondOwner)
        }
    }

    @Test fun invalidAddressesAndProxyCredentialsNeverNavigateOrApply() {
        val owner = UUID.randomUUID().toString()
        lateinit var browser: BrowserController
        main {
            browser = BrowserController(instrumentation.targetContext, saveProxy = false)
            val tab = requireNotNull(browser.open(owner))
            listOf("file:///data/data/", "javascript:alert(1)", "https://user:secret@example.com/").forEach {
                browser.navigate(tab, it)
                assertTrue(tab.address.isEmpty())
                assertNotNull(browser.error)
                browser.error = null
            }
            browser.close(tab)
            browser.applyProxy("http://user:secret@example.com:8080")
            assertEquals("", browser.proxy)
            assertFalse(browser.configuring)
            assertFalse(browser.error.orEmpty().contains("secret"))
        }
        delete(browser, owner)
    }

    @Test fun proxyChangesWaitForCallbackAndRejectLivePages() {
        lateinit var browser: BrowserController
        val applied = CountDownLatch(1)
        val cleared = CountDownLatch(1)
        val owner = UUID.randomUUID().toString()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 15_000 }
        val worker = Executors.newSingleThreadExecutor()
        val request = worker.submit<String> {
            server.accept().use { socket ->
                socket.soTimeout = 10_000
                val line = socket.getInputStream().bufferedReader().readLine()
                socket.getOutputStream().write("HTTP/1.1 502 Fixture\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                line
            }
        }
        main {
            browser = BrowserController(instrumentation.targetContext, saveProxy = false)
            assertTrue("WebView proxy override required for this check", browser.proxySupported)
            browser.applyProxy("http://127.0.0.1:${server.localPort}") { applied.countDown() }
        }
        assertTrue("Proxy callback missing", applied.await(15, TimeUnit.SECONDS))
        try {
            main {
                assertEquals("http://127.0.0.1:${server.localPort}", browser.proxy)
                val tab = requireNotNull(browser.open(owner))
                browser.navigate(tab, "https://proxy-fixture.invalid/")
                browser.applyProxy("direct")
                assertEquals("http://127.0.0.1:${server.localPort}", browser.proxy)
                assertNotNull(browser.error)
            }
            val line = try { request.get(15, TimeUnit.SECONDS) } catch (failure: Exception) {
                throw AssertionError("Proxy fixture received no connection; browser=${browser.error}; address=${server.localSocketAddress}", failure)
            }
            assertEquals("CONNECT proxy-fixture.invalid:443 HTTP/1.1", line)
        } finally {
            delete(browser, owner)
            main { browser.applyProxy("") { cleared.countDown() } }
            assertTrue("Proxy clear callback missing", cleared.await(15, TimeUnit.SECONDS))
            server.close()
            worker.shutdownNow()
        }
    }

    private fun load(tab: BrowserTab, html: String) {
        val finished = CountDownLatch(1)
        main {
            tab.view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { finished.countDown() }
                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    view.destroy(); return true
                }
            }
            tab.view.loadDataWithBaseURL("https://aurora.fixture/", html, "text/html", "UTF-8", null)
        }
        assertTrue("Fixture page did not load", finished.await(20, TimeUnit.SECONDS))
    }

    private fun js(tab: BrowserTab, script: String): String? {
        val finished = CountDownLatch(1)
        var result: String? = null
        main { tab.view.evaluateJavascript(script) { result = it; finished.countDown() } }
        assertTrue("JavaScript callback missing", finished.await(10, TimeUnit.SECONDS))
        return result
    }
}
