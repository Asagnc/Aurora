package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class RootLinuxFiles(private val context: Context) {
    private val files = RootLinuxRunFiles(context)
    private val tools = ConcurrentHashMap<String, FileToolExecutor>()
    suspend fun execute(owner: String, operation: String, arguments: JsonObject): String = withContext(Dispatchers.IO) {
        require(UUID.fromString(owner).toString() == owner)
        RootWorkspaceLocks.use(context, owner) {
        val root = files.workspace(owner, allowRetired = operation == "remove")
        val result = when (operation) {
            "remove" -> { require(!Files.exists(root.resolve(".active-run"))); files.retire(owner); remove(root); tools.remove(owner); objectOf("removed" to JsonPrimitive(true)) }
            "export" -> export(owner, root, requireNotNull(arguments.text("path")))
            "files_read_batch" -> RootLinuxFileReads.batch(root, arguments.array("files"))
            "files_list" -> {
                val path = arguments.text("path").orEmpty()
                val directory = if (path.isEmpty()) root else RootLinuxFileReads.resolve(root, path)
                var listedBytes = 0
                val entries = Files.newDirectoryStream(directory).use { children -> children.asSequence().map { child ->
                    root.relativize(child).toString() to Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                }.filter { (path, _) -> path.substringBefore('/') in setOf("home", "tmp", "project") }
                    .takeWhile { (path, _) -> listedBytes += path.toByteArray().size; listedBytes <= 32768 }.take(200).sortedBy { it.first }.toList() }
                objectOf("entries" to JsonArray(entries.map { (path, directory) -> objectOf("path" to string(path), "directory" to JsonPrimitive(directory)) }))
            }
            "files_edit_batch" -> {
                require(!Files.exists(root.resolve(".active-run"))) { "Guest task is still writing this workspace" }
                val edits = arguments.array("edits")
                require(edits.size in 1..64 && edits.sumOf { requireNotNull(it.jsonObject.text("text")).toByteArray().size.toLong() } <= 32768)
                edits.forEach { RootLinuxFileReads.resolve(root, requireNotNull(it.jsonObject.text("path"))) }
                val executor = tools.computeIfAbsent(owner) { FileToolExecutor(mapOf("conversation" to OwnedFiles(root))) }
                WireJson.parseToJsonElement(executor.execute(ToolCall(UUID.randomUUID().toString(), operation,
                    JsonObject(arguments + ("space" to string("conversation")))))).jsonObject
            }
            else -> error("Unknown Root Linux file operation")
        }
        result.toString().also { require(it.length <= 262144) }
        }
    }
    private suspend fun export(owner: String, root: Path, name: String): JsonObject {
        val source = RootLinuxFileReads.resolve(root, name)
        val targetRoot = files.conversation(owner).resolve("exports")
        require(!Files.isSymbolicLink(targetRoot)); Files.createDirectories(targetRoot)
        Os.chown(targetRoot.toString(), context.applicationInfo.uid, context.applicationInfo.uid)
        Os.chmod(targetRoot.toString(), 448)
        val id = UUID.randomUUID().toString()
        val target = targetRoot.resolve("root-$id.bin")
        val partial = targetRoot.resolve("root-$id.partial")
        try {
            val input = Os.open(source.toString(), OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
            try {
                val info = Os.fstat(input)
                require(OsConstants.S_ISREG(info.st_mode) && info.st_size in 1..32L * 1024 * 1024)
                Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = Os.read(input, buffer, 0, buffer.size); if (count == 0) break
                        total += count; require(total <= 32L * 1024 * 1024); output.write(buffer, 0, count)
                    }
                    val after = Os.fstat(input)
                    require(total == info.st_size && after.st_size == info.st_size && after.st_mtime == info.st_mtime && after.st_ctime == info.st_ctime && after.st_mtim.tv_nsec == info.st_mtim.tv_nsec && after.st_ctim.tv_nsec == info.st_ctim.tv_nsec)
                }
            } finally { Os.close(input) }
            Os.chown(partial.toString(), context.applicationInfo.uid, context.applicationInfo.uid)
            Os.chmod(partial.toString(), 384)
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
            return objectOf("path" to string("exports/${target.fileName}"))
        } finally { Files.deleteIfExists(partial) }
    }
    private suspend fun remove(root: Path) {
        val mounts = Files.readAllLines(Path.of("/proc/self/mountinfo")).mapNotNull { it.split(' ').getOrNull(4) }
        require(mounts.none { Path.of(it.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\")).startsWith(root) })
        val lifetime = currentCoroutineContext()
        Files.newDirectoryStream(root).use { children -> children.forEach { child ->
            if (child.fileName.toString() != ".workspace-owner") Files.walkFileTree(child, object : SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult { lifetime.ensureActive(); Files.delete(path); return FileVisitResult.CONTINUE }
                override fun postVisitDirectory(path: Path, error: java.io.IOException?): FileVisitResult {
                    if (error != null) throw error
                    lifetime.ensureActive(); Files.delete(path); return FileVisitResult.CONTINUE
                }
            })
        } }
        LinuxDurability.directory(root)
        Files.delete(root.resolve(".workspace-owner")); Files.delete(root); LinuxDurability.directory(root.parent)
    }
}
