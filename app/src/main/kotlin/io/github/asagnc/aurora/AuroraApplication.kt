package io.github.asagnc.aurora

import android.app.Application
import io.github.asagnc.aurora.data.AuroraDatabase
import io.github.asagnc.aurora.data.ConversationStore
import io.github.asagnc.aurora.data.CredentialVault
import io.github.asagnc.aurora.data.NativeHistoryStore
import io.github.asagnc.aurora.data.SpaceStore
import io.github.asagnc.aurora.data.ToolRunStore
import kotlinx.coroutines.*
import io.github.asagnc.aurora.core.OwnedTasks

class AuroraApplication : Application() {
    val database by lazy { AuroraDatabase(this) }
    val conversations by lazy { ConversationStore(database) }
    val credentials by lazy { CredentialVault(this) }
    val nativeHistory by lazy { NativeHistoryStore(conversations) }
    val spaces by lazy { SpaceStore(this, conversations) }
    val toolRuns by lazy { ToolRunStore(conversations) }
    internal val commands by lazy { io.github.asagnc.aurora.runtime.NativeCommandRunner(this) }
    internal val agentRuns by lazy { io.github.asagnc.aurora.data.AgentRunStore(conversations) }
    val tasks = OwnedTasks()
    internal val agentTasks by lazy { io.github.asagnc.aurora.runtime.AgentTaskHost(this) }
    internal val chat by lazy { io.github.asagnc.aurora.runtime.ChatController(this) }
    val root by lazy { io.github.asagnc.aurora.root.RootController(this) }
    internal val browser by lazy { io.github.asagnc.aurora.browser.BrowserController(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val ready by lazy { scope.async {
        conversations.recoverInterruptedRuns()
        spaces.recoverDeletes(browser::deleteOwner)
        val owners = withContext(Dispatchers.Main) { browser.profileOwners() }
        owners.filter { conversations.header(it) == null }.forEach { owner ->
            withContext(Dispatchers.Main) { browser.deleteOwner(owner) }
        }
        val proxy = getSharedPreferences("browser", MODE_PRIVATE).getString("proxy", "").orEmpty()
        if (proxy.isNotEmpty()) withContext(Dispatchers.Main) { browser.applyProxy(proxy) }
    } }
}
