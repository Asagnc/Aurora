package io.github.asagnc.aurora.runtime

import android.app.Application
import android.net.Uri
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SessionHeader
import io.github.asagnc.aurora.data.ToolRun
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
    val toolRuns: List<ToolRun> = emptyList(),
    val totalUsage: Usage = Usage(), val requests: Int = 0,
    val steps: Long = 0,
    val taskId: String? = null, val taskStatus: String? = null,
)

internal class ChatController(application: Application,
    private val transportFactory: (String) -> ModelTransport = { token -> HttpModelTransport { token } }) {
    private val app = application as AuroraApplication
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    val rootState = app.root.state
    internal val commandsAvailable get() = app.commands.available
    internal val browser get() = app.browser
    private var rootRequest: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeOwner: String? = null
    private var runProjection: MutableStateFlow<ChatState>? = null
    private var projectionObserver: Job? = null
    private var preparing: Job? = null
    private var importing: Job? = null
    private var navigation = 0L

    init { action { app.ready.await(); refresh(); if (state.value.sessions.isNotEmpty()) open(state.value.sessions.first().id) } }

    private fun action(block: suspend () -> Unit) = scope.launch {
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
        importing?.cancelAndJoin()
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

    fun selectConversation(id: String) = action { importing?.cancelAndJoin(); open(id) }

    private suspend fun open(id: String, keepMetrics: Boolean = false) {
        val ticket = ++navigation
        val loaded = load(id, keepMetrics)
        if (ticket != navigation) return
        mutable.update { loaded.copy(sessions = it.sessions, connections = it.connections, selected = it.selected) }
        if (activeOwner == id) runProjection?.value?.let { project(it) }
    }

    private suspend fun load(id: String, keepMetrics: Boolean = false): ChatState {
        val header = requireNotNull(app.conversations.header(id))
        val rows = app.conversations.messages(id)
        val toolRuns = app.toolRuns.recent(id)
        val task = app.agentRuns.latest(id)
        return state.value.let { it.copy(running = false, taskId = task?.id, taskStatus = task?.state, current = header, messages = rows.map { row -> row.message }, toolRuns = toolRuns, streaming = "",
            olderBefore = rows.firstOrNull()?.sequence?.takeIf { sequence -> sequence > 0 },
            attachments = if (it.current?.id == id) it.attachments else emptyList(),
            usage = if (keepMetrics) it.usage else Usage(), seconds = if (keepMetrics) it.seconds else 0.0,
            totalUsage = if (keepMetrics) it.totalUsage else Usage(), requests = if (keepMetrics) it.requests else 0,
            steps = if (keepMetrics) it.steps else 0,
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
    suspend fun searchSessions(query: String) = app.conversations.searchTitle(query)
    private suspend fun <T> inspectSpace(name: String, inspect: suspend (OwnedFiles) -> T): T = when (name) {
        "conversation" -> {
            val owner = requireNotNull(state.value.current?.id) { "请先创建会话" }
            app.tasks.run(owner) { inspect(app.spaces.conversation(owner)) }
        }
        "resources" -> inspect(app.spaces.sharedResources())
        "workspace" -> inspect(app.spaces.sharedWorkspace())
        else -> error("Unknown space")
    }
    suspend fun listSpace(name: String, path: String): List<Pair<String, Boolean>> =
        if (name == "conversation" && LinuxFileToolRouter.isLinux(path)) rootBrowser().list(path) else inspectSpace(name) { it.list(path) }
    suspend fun readSpace(name: String, path: String): FileSlice =
        if (name == "conversation" && LinuxFileToolRouter.isLinux(path)) rootBrowser().read(path)
        else inspectSpace(name) { it.readBatch(listOf(FileRead(path))).single() }
    private fun rootBrowser() = RootFileBrowser(app, requireNotNull(state.value.current?.id) { "请先创建会话" })
    fun enableRoot() {
        if (rootRequest?.isActive != true) rootRequest = action {
            app.root.connect()
            app.spaces.recoverDeletes(app.browser::deleteOwner)
            refresh()
        }
    }
    fun disableRoot() = action { rootRequest?.cancelAndJoin(); app.root.disconnect() }
    fun stop() { if (app.agentTasks.busy) app.agentTasks.stop() else preparing?.cancel() }

    private fun project(runState: ChatState) {
        mutable.update { current ->
            if (current.current?.id != activeOwner) current else runState.copy(
                current = current.current, sessions = current.sessions, connections = current.connections, selected = current.selected,
                moreSessions = current.moreSessions, attachments = current.attachments,
                messages = (current.messages + runState.messages).distinctBy { it.id },
                olderBefore = listOfNotNull(current.olderBefore, runState.olderBefore).minOrNull())
        }
    }

    fun rename(id: String, title: String) = action {
        app.conversations.rename(id, title); refresh()
        if (state.value.current?.id == id) {
            val header = app.conversations.header(id)
            mutable.update { it.copy(current = header) }
        }
    }
    fun pin(ids: List<String>, pinned: Boolean) = action { app.conversations.pin(ids, pinned); refresh() }
    fun delete(ids: List<String>) = action {
        importing?.cancelAndJoin()
        if (activeOwner in ids) { preparing?.cancelAndJoin(); app.agentTasks.stopAndJoin() }
        if (state.value.current?.id in ids) mutable.update { it.copy(current = null, messages = emptyList(), streaming = "", toolRuns = emptyList(), taskId = null) }
        ids.forEach { id ->
            app.spaces.delete(id, { owner -> app.tasks.quiesce(owner); app.managedRuns.recover(owner, discardCapture = true) }) { owner ->
                app.terminals.forget(owner)
                app.browser.deleteOwner(owner)
                app.imageLoader.memoryCache?.let { cache -> cache.keys.filter { it.key.startsWith("$owner:") }.forEach(cache::remove) }
            }
        }
        refresh()
        if (state.value.current?.id in ids) mutable.update { it.copy(current = null, messages = emptyList(), streaming = "") }
    }

    fun send(text: String): Boolean {
        if (app.agentTasks.busy || activeOwner != null || text.isBlank()) {
            if (app.agentTasks.busy) mutable.update { it.copy(error = "已有任务正在运行，请先停止或等待完成") }
            return false
        }
        val connection = state.value.selected ?: run { mutable.update { it.copy(error = "先添加供应商连接") }; return false }
        // Reserve admission before the first suspension, including conversation creation.
        val initial = state.value
        activeOwner = initial.current?.id ?: "preparing"
        mutable.update { it.copy(running = true, error = null, stage = "准备任务") }
        preparing = action { prepare(text, connection, initial) }
        return true
    }

    private suspend fun prepare(text: String, connection: Connection, initial: ChatState) {
        var queuedOwner: String? = null
        val taskId = java.util.UUID.randomUUID().toString()
        try {
            app.ready.await()
            val id = initial.current?.id ?: app.conversations.create(text.take(40))
            val content = text + initial.attachments.takeIf { it.isNotEmpty() }?.joinToString(
                "\n", "\n\nConversation-space attachments:\n").orEmpty()
            activeOwner = id
            val seed = load(id)
            if (state.value.current == null && initial.current == null) mutable.value = seed
            val projection = MutableStateFlow(seed.copy(running = true, streaming = "", error = null,
                attachments = emptyList(), stage = "启动任务", usage = Usage(), totalUsage = Usage(),
                requests = 0, steps = 0, seconds = 0.0, taskId = taskId, taskStatus = "queued"))
            queuedOwner = id
            app.agentRuns.queue(taskId, id)
            mutable.update { it.copy(attachments = emptyList()) }
            runProjection = projection
            projectionObserver = scope.launch { projection.collect { project(it) } }
            startExecution(id, content, connection, projection, taskId)
        } catch (failure: Exception) {
            try {
                withContext(NonCancellable) {
                    queuedOwner?.let { owner ->
                        if (app.agentRuns.latest(owner)?.id == taskId) {
                            app.conversations.setRunState(owner, "interrupted")
                            app.agentRuns.finish(taskId, if (failure is CancellationException) "cancelled" else "failed")
                        }
                    }
                }
            } finally {
                activeOwner = null
                mutable.update { it.copy(running = false) }
            }
            throw failure
        }
    }

    private suspend fun startExecution(id: String, content: String, connection: Connection,
        projection: MutableStateFlow<ChatState>, taskId: String) {
        val execution = AgentExecution(app, projection, transportFactory)
        val accepted = app.agentTasks.start(id, {
            app.tasks.run(id) {
                app.agentRuns.running(taskId)
                projection.update { it.copy(taskStatus = "running") }
                try { execution.execute(id, content, connection, System.nanoTime()) }
                catch (failure: Throwable) { withContext(NonCancellable) { execution.markInterrupted(id) }; throw failure }
            }
        }, { failure -> finish(id, projection, failure, taskId) }, taskId)
        if (!accepted) finish(id, projection, IllegalStateException("Task admission failed"), taskId)
    }

    private suspend fun finish(id: String, projection: MutableStateFlow<ChatState>, failure: Throwable?, taskId: String) {
        var terminalFailure = failure
        val status = when (failure) { null -> "completed"; is CancellationException -> "cancelled"; else -> "failed" }
        try {
            if (failure != null) AgentExecution(app, projection).markInterrupted(id)
            app.agentRuns.finish(taskId, status)
        } catch (storage: Exception) { terminalFailure = storage }
        projectionObserver?.cancelAndJoin()
        projection.value = projection.value.copy(running = false, taskStatus = if (terminalFailure === failure) status else "uncertain",
            stage = if (failure is CancellationException) "已停止；不会自动重放工具" else projection.value.stage,
            error = when (terminalFailure) { null, is CancellationException -> null
                is Exception -> safeError(terminalFailure)
                else -> "任务中断，已保留恢复记录" })
        project(projection.value)
        activeOwner = null
        runProjection = null
        try {
            refresh()
            if (state.value.current?.id == id) open(id, true)
        } catch (storage: Exception) {
            mutable.update { it.copy(error = safeError(storage)) }
        }
    }

    internal fun close() { scope.cancel() }

    private fun safeError(failure: Exception): String = when (failure) {
        is ModelNetworkException -> failure.message.orEmpty()
        is ConfigurationException -> failure.message.orEmpty()
        is io.github.asagnc.aurora.data.LinuxFilesPendingException -> "Root 文件待清理，请重新连接 Root 后继续"
        is io.github.asagnc.aurora.data.RootManagedRunPendingException -> "Root 注入清理尚未确认，已保留会话与恢复记录"
        is io.github.asagnc.aurora.data.LinuxRunPendingException -> "Linux 任务尚未确认退出，请在 Linux 环境中恢复"
        is IllegalArgumentException -> "配置或文件版本不符合要求，请检查连接和任务状态"
        else -> "操作失败（${failure.javaClass.simpleName}），已保留会话和恢复记录"
    }

}
