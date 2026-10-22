package io.github.asagnc.aurora.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import io.github.asagnc.aurora.core.OwnedFiles
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class LinuxFilesPendingException(cause: Throwable) : java.io.IOException("Root file cleanup remains pending", cause)

internal class LinuxRunPendingException : IllegalArgumentException("Linux task exit must be confirmed before deleting this conversation")

class SpaceStore(private val context: Context, private val conversations: ConversationStore,
    private val releaseRoot: suspend (String) -> Unit = {}) {
    private val root = context.filesDir.toPath().resolve("spaces")
    private val openSpaces = ConcurrentHashMap<Path, OwnedFiles>()

    suspend fun conversation(id: String): OwnedFiles = open(path(id), true)
    internal suspend fun commandRoot(id: String): Path { conversation(id); return path(id) }
    suspend fun sharedResources(): OwnedFiles = open(root.resolve("resources"), false)
    suspend fun sharedWorkspace(): OwnedFiles = open(root.resolve("workspace"), true)
    private suspend fun open(path: Path, writable: Boolean): OwnedFiles = withContext(Dispatchers.IO) {
        openSpaces.computeIfAbsent(path) { OwnedFiles(it, writable) }.also { it.recover() }
    }

    suspend fun importFile(id: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val supplied = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
        val name = supplied.take(80).replace(Regex("[^\\p{L}\\p{N}._-]"), "_").ifBlank { "attachment" }
        val relative = "imports/${UUID.randomUUID()}-$name"
        val target = path(id).resolve(relative)
        Files.createDirectories(target.parent)
        val staged = target.resolveSibling(target.fileName.toString() + ".partial")
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                Files.newOutputStream(staged, StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 64 * 1024 * 1024) { "Attachment exceeds 64 MiB import budget" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE)
            relative
        } finally { Files.deleteIfExists(staged) }
    }

    suspend fun delete(id: String, quiesce: suspend (String) -> Unit) = delete(id, quiesce, {})

    suspend fun delete(id: String, quiesce: suspend (String) -> Unit, releaseBrowser: suspend (String) -> Unit) {
        quiesce(id)
        conversations.write { db ->
            val state = db.rawQuery("SELECT run_state FROM conversations WHERE id = ?", arrayOf(id))
                .use { if (it.moveToFirst()) it.getString(0) else null }
            require(state != "running") { "Conversation tasks must stop before deletion" }
            requireNoLinuxLeases(db, id)
            db.update("conversations", values("deleting" to 1), "id = ?", arrayOf(id))
        }
        releaseBrowser(id)
        finishDelete(id)
    }

    suspend fun recoverDeletes(releaseBrowser: suspend (String) -> Unit = {}): Int {
        val pending = conversations.read { db -> db.rawQuery("SELECT id FROM conversations WHERE deleting = 1", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } } }
        var retained = 0
        pending.forEach { id ->
            try {
                conversations.read { requireNoLinuxLeases(it, id) }
                releaseBrowser(id); finishDelete(id)
            } catch (_: LinuxRunPendingException) { retained++ }
            catch (_: LinuxFilesPendingException) { retained++ }
        }
        return retained
    }

    private fun requireNoLinuxLeases(db: SQLiteDatabase, id: String) {
        if (db.rawQuery("SELECT 1 FROM linux_leases WHERE owner=? LIMIT 1", arrayOf(id)).use { it.moveToFirst() })
            throw LinuxRunPendingException()
    }

    private suspend fun finishDelete(id: String) = withContext(Dispatchers.IO) {
        conversations.read { requireNoLinuxLeases(it, id) }
        val target = path(id)
        val linux = target.resolve(".aurora-linux")
        if (Files.exists(linux, LinkOption.NOFOLLOW_LINKS)) {
            try {
                releaseRoot(id)
                require(!Files.exists(linux, LinkOption.NOFOLLOW_LINKS)) { "Connect Root to finish deleting Linux conversation files" }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (failure: Exception) { throw LinuxFilesPendingException(failure) }
        }
        require(!Files.isSymbolicLink(root) && !Files.isSymbolicLink(target.parent))
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target)) Files.delete(target) else {
                val canonical = target.toRealPath()
                val mounted = Files.readAllLines(Path.of("/proc/self/mountinfo")).mapNotNull { line -> line.split(' ').getOrNull(4) }
                require(mounted.none { entry -> Path.of(entry.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\")).startsWith(canonical) }) {
                    "Unmount conversation resources before deletion"
                }
                Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        Files.delete(file)
                        return FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                        if (error != null) throw error
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        }
        openSpaces.remove(target)
        conversations.write { db -> db.delete("conversations", "id = ? AND deleting = 1", arrayOf(id)) }
    }

    private fun path(id: String): Path {
        require(UUID.fromString(id).toString() == id)
        return root.resolve("conversations").resolve(id)
    }
}
