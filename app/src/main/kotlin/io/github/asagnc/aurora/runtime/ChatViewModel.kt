package io.github.asagnc.aurora.runtime

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SessionHeader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ChatState(
    val sessions: List<SessionHeader> = emptyList(), val current: SessionHeader? = null,
    val messages: List<ChatMessage> = emptyList(), val connections: List<Connection> = emptyList(),
    val selected: Connection? = null, val running: Boolean = false, val streaming: String = "",
    val stage: String = "", val error: String? = null, val usage: Usage = Usage(), val seconds: Double = 0.0,
    val attachments: List<String> = emptyList(),
    val moreSessions: Boolean = false,
    val olderBefore: Long? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AuroraApplication
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    private var run: Job? = null
    private var importing: Job? = null

    init { action { app.ready.await(); refresh(); if (state.value.sessions.isNotEmpty()) open(state.value.sessions.first().id) } }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { mutable.update { it.copy(error = safeError(failure), stage = "操作未完成") } }
    }

    suspend fun refresh() {
        val sessions = app.conversations.list()
        val connections = app.conversations.connections()
        mutable.update { it.copy(sessions = sessions, moreSessions = sessions.size == 100, connections = connections, selected =
            connections.firstOrNull { connection -> connection.id == it.selected?.id } ?: connections.firstOrNull()) }
    }

    fun loadMoreSessions() = action {
        val last = state.value.sessions.lastOrNull() ?: return@action
        val page = app.conversations.list(io.github.asagnc.aurora.data.HistoryCursor(last.pinned, last.updatedAt, last.id))
        mutable.update { it.copy(sessions = (it.sessions + page).distinctBy { session -> session.id }, moreSessions = page.size == 100) }
    }

    fun newConversation() = action {
        stopAndJoin()
        val id = app.conversations.create()
        refresh()
        open(id)
    }

    fun importFiles(uris: List<Uri>) {
        if (importing?.isActive == true) return
        importing = action {
            require(uris.size in 1..16)
            val id = state.value.current?.id ?: app.conversations.create().also { open(it) }
            app.tasks.run(id) {
                val imported = uris.map { app.spaces.importFile(id, it) }
                mutable.update { it.copy(attachments = it.attachments + imported) }
                refresh()
            }
        }
    }

    fun selectConversation(id: String) = action { stopAndJoin(); open(id) }

    private suspend fun open(id: String, keepMetrics: Boolean = false) {
        val header = requireNotNull(app.conversations.header(id))
        val rows = app.conversations.messages(id)
        mutable.update { it.copy(current = header, messages = rows.map { row -> row.message }, streaming = "",
            olderBefore = rows.firstOrNull()?.sequence?.takeIf { sequence -> sequence > 0 },
            attachments = if (it.current?.id == id) it.attachments else emptyList(),
            usage = if (keepMetrics) it.usage else Usage(), seconds = if (keepMetrics) it.seconds else 0.0,
            stage = if (header.runState == "interrupted") "上次任务中断；不会自动重放工具" else if (keepMetrics) it.stage else "") }
    }

    fun loadOlderMessages() = action {
        val id = state.value.current?.id ?: return@action
        val before = state.value.olderBefore ?: return@action
        val rows = app.conversations.messages(id, before)
        mutable.update { if (it.current?.id != id) it else it.copy(
            messages = (rows.map { row -> row.message } + it.messages).distinctBy { message -> message.id },
            olderBefore = rows.firstOrNull()?.sequence?.takeIf { sequence -> sequence > 0 }) }
    }

    fun saveConnection(connection: Connection, token: String) = action {
        HttpModelTransport.endpoint(connection)
        val revision = if (token.isNotEmpty()) app.credentials.put(connection.id, token)
            else requireNotNull(app.credentials.get(connection.id)) { "请填写 API 密钥" }.revision
        app.conversations.saveConnection(connection.copy(credentialRevision = revision))
        refresh()
        mutable.update { it.copy(selected = it.connections.first { saved -> saved.id == connection.id }) }
    }

    fun selectConnection(connection: Connection) {
        mutable.update { it.copy(selected = connection) }
        action { app.conversations.saveConnection(connection); refresh() }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun stop() { run?.cancel() }
    private suspend fun stopAndJoin() { importing?.cancelAndJoin(); run?.cancelAndJoin() }

    fun rename(id: String, title: String) = action {
        app.conversations.rename(id, title); refresh()
        if (state.value.current?.id == id) {
            val header = app.conversations.header(id)
            mutable.update { it.copy(current = header) }
        }
    }
    fun pin(ids: List<String>, pinned: Boolean) = action { app.conversations.pin(ids, pinned); refresh() }
    fun delete(ids: List<String>) = action {
        stopAndJoin()
        ids.forEach { id -> app.spaces.delete(id, app.tasks::quiesce) }
        refresh()
        if (state.value.current?.id in ids) mutable.update { it.copy(current = null, messages = emptyList(), streaming = "") }
    }

    fun send(text: String): Boolean {
        if (run?.isActive == true || state.value.running || text.isBlank()) return false
        val connection = state.value.selected ?: run { mutable.update { it.copy(error = "先添加供应商连接") }; return false }
        val content = text + if (state.value.attachments.isEmpty()) "" else "\n\nConversation-space attachments:\n" + state.value.attachments.joinToString("\n")
        mutable.update { it.copy(attachments = emptyList()) }
        run = action {
            app.ready.await()
            val id = state.value.current?.id ?: app.conversations.create(text.take(40))
            open(id)
            val started = System.nanoTime()
            app.tasks.run(id) {
                mutable.update { it.copy(running = true, streaming = "", error = null, stage = "连接模型") }
                try { execute(id, content, connection, started) }
                catch (cancel: CancellationException) { withContext(NonCancellable) { markInterrupted(id) }; throw cancel }
                catch (failure: Exception) { markInterrupted(id); throw failure }
                finally {
                    mutable.update { it.copy(running = false) }
                    withContext(NonCancellable) { refresh(); open(id, true) }
                }
            }
        }
        return true
    }

    private suspend fun execute(id: String, text: String, connection: Connection, started: Long) {
        val files = FileToolExecutor(linkedMapOf("conversation" to app.spaces.conversation(id),
            "resources" to app.spaces.sharedResources(), "workspace" to app.spaces.sharedWorkspace()))
        val fingerprint = requestFingerprint(SYSTEM, files.definitions)
        val credential = requireNotNull(app.credentials.get(connection.id)) { "API 密钥不存在" }
        require(credential.revision == connection.credentialRevision) { "API 密钥已改变" }
        ModelRules.validate(ModelRequest(connection, SYSTEM, emptyList(), files.definitions))
        app.conversations.setRunState(id, "running")
        val user = ChatMessage(role = "user", text = text)
        val previous = state.value.current?.activeScope?.let { app.nativeHistory.load(id, it) }
        val compatible = previous?.takeIf { CachePolicy.canReuse(it, connection, fingerprint) && state.value.current?.runState != "interrupted" }
        val projected = compatible?.entries ?: allMessages(id).filter { it.complete }.map { MessageProjection.message(connection.protocol, it.role, it.text) }
        app.conversations.appendMessage(id, user)
        mutable.update { it.copy(messages = it.messages + user) }
        val ledger = if (compatible == null) app.toolRuns.list(id) else emptyList()
        val observations = if (ledger.isEmpty()) emptyList() else listOf(MessageProjection.message(connection.protocol, "user",
            "Prior tool execution ledger; completed actions must not be automatically replayed, uncertain actions require inspection:\n" +
                ledger.joinToString("\n") { "${it.tool} (${it.state}): ${it.result ?: "outcome unknown"}" }))
        val continuation = compatible?.let { NativeContinuation.append(it, connection, fingerprint, text) }
        val history = continuation?.history ?: (projected + observations + MessageProjection.message(connection.protocol, "user", text))
        val scope = if (compatible != null) requireNotNull(state.value.current?.activeScope).also {
            app.nativeHistory.append(id, it, compatible.entries.size.toLong(), requireNotNull(continuation).appended)
        } else app.nativeHistory.begin(id, WireHistory(connection.id, connection.model, connection.protocol, connection.effort,
            history, connection.credentialRevision, connection.baseUrl, fingerprint, connection.authentication))
        var savedCount = history.size
        val transport = HttpModelTransport { credential.token }
        val result = AgentLoop(transport, app.toolRuns.recording(id, files)).run(
            ModelRequest(continuation?.connection ?: connection, SYSTEM, history, files.definitions),
            { update -> mutable.update { it.copy(streaming = update.text, stage = update.stage) } },
            { entries -> app.nativeHistory.append(id, scope, savedCount.toLong(), entries.drop(savedCount)); savedCount = entries.size })
        app.conversations.appendMessage(id, ChatMessage(role = "assistant", text = result.text))
        app.conversations.setRunState(id, "idle")
        mutable.update { it.copy(usage = result.usage, seconds = (System.nanoTime() - started) / 1e9, stage = "完成") }
    }

    private suspend fun markInterrupted(id: String) {
        if (app.conversations.header(id)?.runState != "running") return
        state.value.streaming.takeIf { it.isNotEmpty() }?.let {
            app.conversations.appendMessage(id, ChatMessage(role = "assistant", text = it, complete = false))
        }
        app.conversations.setRunState(id, "interrupted")
        app.toolRuns.markUncertain(id)
    }

    private suspend fun allMessages(id: String): List<ChatMessage> {
        val chunks = mutableListOf<List<ChatMessage>>()
        var before = Long.MAX_VALUE
        while (true) {
            val page = app.conversations.messages(id, before, 200)
            if (page.isEmpty()) break
            chunks += page.map { it.message }
            before = page.first().sequence
        }
        return chunks.asReversed().flatten()
    }

    private fun safeError(failure: Exception): String = when (failure) {
        is ModelNetworkException -> failure.message.orEmpty()
        is ConfigurationException -> failure.message.orEmpty()
        is IllegalArgumentException -> "配置或文件版本不符合要求，请检查连接和任务状态"
        else -> "操作失败（${failure.javaClass.simpleName}），已保留会话和恢复记录"
    }

    companion object {
        private const val SYSTEM = "You are Aurora, an Android assistant. Use tools to inspect actual file state before editing. Conversation files are disposable; resources are read-only installed assets; workspace is retained. Tool errors and interrupted operations are not successes. Do not repeat completed actions without an explicit reason."
    }
}
