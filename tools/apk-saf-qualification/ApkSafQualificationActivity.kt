package io.github.asagnc.aurora.qualification

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.reverse.SharedToolchainManager
import io.github.asagnc.aurora.root.RootPhase
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonArray

/** Copied only into the isolated qualification APK, never the production source set. */
class ApkSafQualificationActivity : ComponentActivity() {
    val ready = CompletableDeferred<Unit>()
    val installed = CompletableDeferred<String>()
    var selectedAuthority: String? = null
        private set
    var installMillis: Long = 0
        private set
    internal lateinit var manager: SharedToolchainManager
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var detail: TextView
    private lateinit var select: Button
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) installed.completeExceptionally(IllegalStateException("Selection cancelled"))
        else work.launch {
            select.isEnabled = false
            detail.text = "正在通过实际文件授权读取归档并完整校验安装…"
            try {
                check(uri.scheme == "content")
                selectedAuthority = requireNotNull(uri.authority)
                val started = android.os.SystemClock.elapsedRealtime()
                val id = manager.installAndroidArchive(uri)
                installMillis = android.os.SystemClock.elapsedRealtime() - started
                detail.text = "完整安装通过；未授予构建运行资格。"
                installed.complete(id)
            } catch (failure: Throwable) {
                detail.text = "验收失败：${failure.javaClass.simpleName}"
                installed.completeExceptionally(failure)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        detail = TextView(this).apply { text = "正在准备独立空安装区…"; textSize = 18f }
        select = Button(this).apply {
            text = "选择本轮 android-host-arm64.tar.gz"
            isEnabled = false
            setOnClickListener { picker.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream")) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 100, 48, 48)
            addView(detail); addView(select)
        })
        work.launch {
            try {
                val app = application as AuroraApplication
                app.ready.await(); app.root.connect()
                check(app.root.state.value.phase == RootPhase.READY)
                manager = fixedOfflineManager(app)
                check(string(AndroidBuildTools.bundleId) !in manager.status(null, "android-build")["installed"]!!.jsonArray)
                detail.text = "独立空安装区已确认。请选择下载/Aurora-Acceptance-20261109 中的本轮归档。其余固定归档使用显式离线夹具。"
                select.isEnabled = true
                ready.complete(Unit)
            } catch (failure: Throwable) {
                detail.text = "准备失败：${failure.javaClass.simpleName}"
                ready.completeExceptionally(failure)
            }
        }
    }

    override fun onDestroy() { work.cancel(); super.onDestroy() }
}
