package io.github.asagnc.aurora.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.webkit.*
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class BrowserTab(val owner: String, val view: WebView) {
    val id: String = UUID.randomUUID().toString()
    var title by mutableStateOf("新页面")
    var address by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    var revision = 0L
    var closed = false
        private set
    val document = kotlinx.coroutines.flow.MutableStateFlow(io.github.asagnc.aurora.core.BrowserDocumentState())
    fun changed() { document.value = io.github.asagnc.aurora.core.BrowserDocumentState(revision, loading, failure, closed) }
    fun markClosed() { closed = true; changed() }
}

internal class BrowserController(private val context: Context, private val saveProxy: Boolean = true,
    private val intercept: ((WebResourceRequest) -> WebResourceResponse?)? = null) {
    val tabs = mutableStateListOf<BrowserTab>()
    internal val transferSlots = kotlinx.coroutines.sync.Semaphore(2)
    internal val transfers = mutableStateMapOf<String, BrowserTransferProgress>()
    var error by mutableStateOf<String?>(null)
    var proxy by mutableStateOf("")
        private set
    var configuring by mutableStateOf(false)
        private set
    private var proxyFailed = false
    private var proxyRevision = 0
    private val retired = mutableSetOf<String>()
    private val agents = mutableMapOf<String, Int>()
    private val viewers = mutableSetOf<String>()
    val supported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    val proxySupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    @SuppressLint("SetJavaScriptEnabled")
    fun open(owner: String): BrowserTab? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { error = "当前系统 WebView 不支持独立会话配置，请更新系统 WebView"; return null }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) { error = "当前 WebView 不支持完整清理会话浏览数据，请更新 WebView"; return null }
        if (owner in retired) { error = "此会话的浏览空间正在删除"; return null }
        if (tabs.size >= 8) { error = "最多同时打开 8 个页面，请先关闭不用的页面"; return null }
        if (configuring || proxyFailed) { error = "代理尚未成功应用，请先在浏览器代理设置中重试"; return null }
        context.getSharedPreferences("browser", Context.MODE_PRIVATE).edit { putBoolean("used", true) }
        val view = WebView(context)
        val profile = "aurora-$owner"
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.setProfile(view, profile)
        val tab = BrowserTab(owner, view)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
        }
        // Renderer teardown is delegated to close(), which destroys and unregisters the WebView.
        @SuppressLint("MissingOnRenderProcessGone")
        val client = object : WebViewClient() {
            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? = intercept?.invoke(request)
            override fun onPageStarted(web: WebView, url: String, favicon: android.graphics.Bitmap?) {
                tab.loading = true; tab.failure = null; tab.address = url; tab.revision++; tab.changed()
            }
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme != "https") { error = "仅支持 HTTPS 网页；外部应用和本地文件链接未打开"; return true }
                return configuring || proxyFailed
            }
            override fun doUpdateVisitedHistory(web: WebView, url: String, isReload: Boolean) {
                tab.address = url; tab.revision++; tab.changed()
            }
            override fun onPageFinished(web: WebView, url: String) {
                tab.loading = false; tab.address = url; tab.title = web.title?.take(80) ?: "网页"; tab.changed()
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) {
                    tab.loading = false; tab.failure = "network ${failure.errorCode}"; tab.changed()
                    error = "网页加载失败（${failure.errorCode}），可检查网络后重新打开"
                }
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) { tab.failure = "HTTP ${response.statusCode}"; tab.changed() }
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                close(tab); error = "网页渲染进程已退出，请重新打开页面"; return true
            }
        }
        view.webViewClient = client
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(web: WebView, progress: Int) { tab.progress = progress }
        }
        view.setDownloadListener { _, _, _, _, _ -> error = "下载到会话空间尚未接入；没有启动下载" }
        tabs.add(tab)
        return tab
    }

    fun navigate(tab: BrowserTab, supplied: String): Boolean {
        if (configuring || proxyFailed || tab !in tabs) return false
        val address = supplied.trim().let { if ("://" in it) it else "https://$it" }
        val uri = runCatching { URI(address) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            error = "请输入不含用户名或密码的 HTTPS 地址"; return false
        }
        tab.address = address; tab.loading = true; tab.failure = null; tab.changed()
        tab.view.loadUrl(address)
        return true
    }

    fun close(tab: BrowserTab) {
        if (!tabs.remove(tab)) return
        tab.markClosed()
        (tab.view.parent as? ViewGroup)?.removeView(tab.view)
        tab.view.stopLoading()
        tab.view.destroy()
    }

    fun hide(owner: String) {
        viewers.remove(owner)
        pauseIdle(owner)
    }
    fun show(owner: String) { viewers.add(owner) }
    private fun pauseIdle(owner: String) {
        if (owner in agents) return
        tabs.filter { it.owner == owner }.forEach {
            if (it.loading) { it.failure = "Navigation stopped before completion"; it.loading = false }
            it.changed(); it.view.stopLoading(); it.view.onPause()
        }
        if (agents.isEmpty() && viewers.isEmpty()) tabs.firstOrNull()?.view?.pauseTimers()
    }
    fun beginAgent(owner: String) {
        check(owner !in retired) { "Conversation browser is being deleted" }
        check(!configuring && !proxyFailed) { "Browser proxy is not ready" }
        agents[owner] = (agents[owner] ?: 0) + 1
    }
    fun endAgent(owner: String) {
        val count = (agents[owner] ?: 1) - 1
        if (count > 0) agents[owner] = count else { agents.remove(owner); if (owner !in viewers) pauseIdle(owner) }
    }
    fun resume(tab: BrowserTab) { if (tab in tabs) { tab.view.resumeTimers(); tab.view.onResume() } }
    suspend fun deleteOwner(owner: String): Unit = withContext(Dispatchers.Main) {
        retired.add(owner)
        tabs.filter { it.owner == owner }.toList().forEach(::close)
        if (!context.getSharedPreferences("browser", Context.MODE_PRIVATE).getBoolean("used", false)) return@withContext
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val store = ProfileStore.getInstance()
            try { store.deleteProfile("aurora-$owner"); return@withContext }
            catch (loaded: IllegalStateException) {
                // Chromium can retain loaded profiles after WebView.destroy(); remove their data now,
                // then delete the empty profile metadata during the next process startup.
                check(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) { "WebView cannot clear loaded profile data" }
                val storage = store.getProfile("aurora-$owner")?.webStorage ?: return@withContext
                withTimeout(15_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                            WebStorageCompat.deleteBrowsingData(storage, context.mainExecutor) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                    }
                }
            }
        }
    }

    fun profileOwners(): List<String> = if (context.getSharedPreferences("browser", Context.MODE_PRIVATE).getBoolean("used", false) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) ProfileStore.getInstance().allProfileNames
        .filter { it.startsWith("aurora-") }.map { it.removePrefix("aurora-") } else emptyList()

    fun applyProxy(value: String, done: () -> Unit = {}) {
        if (configuring || tabs.isNotEmpty()) { error = "更改代理前请关闭所有浏览器页面"; return }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (value.isEmpty()) {
                proxy = ""; proxyFailed = false
                if (saveProxy) context.getSharedPreferences("browser", Context.MODE_PRIVATE).edit { remove("proxy") }
                done()
            } else { proxyFailed = true; error = "当前系统 WebView 不支持应用内代理设置，已阻止打开页面" }
            return
        }
        val config = when (value) {
            "" -> null
            "direct" -> ProxyConfig.Builder().addDirect().build()
            else -> {
                val uri = runCatching { URI(value) }.getOrNull()
                if (uri == null || uri.scheme !in listOf("http", "https", "socks") || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
                    uri.port !in 1..65535 || !uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null) {
                    error = "代理格式：http://主机:端口、https://主机:端口 或 socks://主机:端口，不含认证信息"; return
                }
                ProxyConfig.Builder().addProxyRule(value).build()
            }
        }
        configuring = true
        val revision = ++proxyRevision
        val callback = Runnable {
            if (revision != proxyRevision) return@Runnable
            proxy = value; configuring = false
            proxyFailed = false
            if (saveProxy) context.getSharedPreferences("browser", Context.MODE_PRIVATE).edit { putString("proxy", value) }
            done()
        }
        Handler(context.mainLooper).postDelayed({
            if (revision == proxyRevision && configuring) {
                ++proxyRevision
                configuring = false; proxyFailed = true
                error = "代理设置超时，已阻止打开页面，请重新应用代理设置"
            }
        }, 10_000)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val controller = ProxyController.getInstance()
                if (config == null) controller.clearProxyOverride(context.mainExecutor, callback)
                else controller.setProxyOverride(config, context.mainExecutor, callback)
            }
        } catch (_: RuntimeException) {
            configuring = false; proxyFailed = true; ++proxyRevision
            error = "代理设置未能应用，已阻止打开页面，请重试"
        }
    }
}
