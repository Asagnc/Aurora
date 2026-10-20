package io.github.asagnc.aurora.runtime

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.Connection

/** Recreating the screen must not own or cancel an Agent run. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as AuroraApplication).chat
    val state = controller.state
    val rootState = controller.rootState
    internal val commandsAvailable get() = controller.commandsAvailable
    internal val browser get() = controller.browser
    fun newConversation() = controller.newConversation()
    fun selectConversation(id: String) = controller.selectConversation(id)
    fun importFiles(uris: List<Uri>) = controller.importFiles(uris)
    fun loadMoreSessions() = controller.loadMoreSessions()
    fun loadOlderMessages() = controller.loadOlderMessages()
    fun saveConnection(connection: Connection, token: String) = controller.saveConnection(connection, token)
    fun selectConnection(connection: Connection) = controller.selectConnection(connection)
    fun clearError() = controller.clearError()
    suspend fun searchSessions(query: String) = controller.searchSessions(query)
    suspend fun listSpace(name: String, path: String) = controller.listSpace(name, path)
    suspend fun readSpace(name: String, path: String) = controller.readSpace(name, path)
    fun enableRoot() = controller.enableRoot()
    fun disableRoot() = controller.disableRoot()
    fun stop() = controller.stop()
    fun rename(id: String, title: String) = controller.rename(id, title)
    fun pin(ids: List<String>, pinned: Boolean) = controller.pin(ids, pinned)
    fun delete(ids: List<String>) = controller.delete(ids)
    fun send(text: String) = controller.send(text)
}
