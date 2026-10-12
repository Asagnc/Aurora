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
    val tasks = OwnedTasks()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val ready by lazy { scope.async { conversations.recoverInterruptedRuns(); spaces.recoverDeletes() } }
}
