package io.github.asagnc.aurora.skills

import android.net.Uri
import android.provider.OpenableColumns
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SkillInstallOptions(val expectedSha256: String? = null, val replaceId: String? = null)

/** A single installer, short catalog transactions, no permanent Skill process. */
internal class SkillManager(private val app: AuroraApplication, internal val registry: SkillRegistry = SkillRegistry(app.conversations)) {
    internal val catalog = Mutex()
    private val installer = Mutex()
    internal val store = SkillContentStore(app.filesDir.toPath().resolve("spaces/resources/skills"))
    private val staging = app.filesDir.toPath().resolve("spaces/resources/.skill-staging")
    private val network = SkillNetwork()
    val version = MutableStateFlow(0L)
    val status = MutableStateFlow<String?>(null)

    suspend fun recover() = withContext(Dispatchers.IO) { installer.withLock {
        app.spaces.sharedResources(); store.initialize()
        require(!Files.isSymbolicLink(staging)); Files.createDirectories(staging)
        registry.pending().forEach { (id, hashes) ->
            hashes.forEach { hash -> if (!registry.known(hash)) store.remove(hash) }
            registry.clearOperation(id)
        }
        Files.newDirectoryStream(staging).use { entries -> entries.forEach { removeSkillTree(it) } }
    } }

    suspend fun remote(source: SkillSource, options: SkillInstallOptions = SkillInstallOptions()): List<SkillEntry> {
        status.value = "解析来源与固定版本…"
        val resolved = try { network.resolve(source) } catch (failure: Exception) {
            status.value = if (failure is CancellationException) "安装已取消" else "来源解析失败，请检查网址与网络"
            throw failure
        }
        return install(resolved.source, options) { archive -> network.download(resolved.archiveUrl, archive) }
    }

    suspend fun import(uri: Uri, replaceId: String? = null): List<SkillEntry> {
        val name = withContext(Dispatchers.IO) { app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "本地 ZIP" }
        return install(SkillSource(uri.toString(), label = name), SkillInstallOptions(replaceId = replaceId)) { archive ->
            runInterruptible(Dispatchers.IO) { requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW).use { output -> copyBounded(input, output) }
            } }
        }
    }

    suspend fun local(owner: String, relative: String, selection: String = "", options: SkillInstallOptions = SkillInstallOptions()): List<SkillEntry> {
        val root = app.spaces.commandRoot(owner)
        var path = root
        for (part in skillRelativePath(relative)) { path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
        val sourcePath = path
        require(Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS) && Files.size(sourcePath) <= 64L * 1024 * 1024)
        return install(SkillSource("conversation:$owner/$relative", selection, "会话 ZIP · $relative"), options) { archive ->
            runInterruptible(Dispatchers.IO) { Files.newInputStream(sourcePath, LinkOption.NOFOLLOW_LINKS).use { input ->
                Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW).use { output -> copyBounded(input, output) }
            } }
        }
    }

    private suspend fun install(source: SkillSource, options: SkillInstallOptions, fetch: suspend (Path) -> Unit): List<SkillEntry> =
        installer.withLock { withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val stage = Files.createDirectory(staging.resolve(id))
            var journal = false
            try {
                status.value = "读取 ZIP…"
                val archive = stage.resolve("download.zip"); fetch(archive)
                options.expectedSha256?.let { expected ->
                    require(expected.matches(Regex("[a-fA-F0-9]{64}")))
                    val digest = runInterruptible { Files.newInputStream(archive).use { input ->
                        val sha = java.security.MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(65536)
                        while (true) { val count = input.read(bytes); if (count < 0) break; sha.update(bytes, 0, count) }
                        sha.digest().joinToString("") { "%02x".format(it) }
                    } }
                    require(digest.equals(expected, true)) { "Downloaded archive SHA256 mismatch" }
                }
                status.value = "校验归档与工作流…"
                val prepared = runInterruptible { prepare(archive, stage, source.selection) }
                require(options.replaceId == null || prepared.size == 1) { "Updating a local skill requires one selected skill" }
                val entries = prepared.map { skill ->
                    SkillEntry(options.replaceId ?: skillDigest("${source.input.substringBefore('?')}\n${skill.relative}".toByteArray()),
                        skill.manifest.digest, true, source.copy(selection = skill.relative), skill.manifest.metadata, skill.manifest.bytes)
                }
                currentCoroutineContext().ensureActive()
                // Once publication begins, finish the journal/index transaction even if its caller cancels.
                withContext(NonCancellable) { catalog.withLock {
                    options.replaceId?.let { require(registry.entries().any { entry -> entry.id == it }) { "Update target no longer exists" } }
                    registry.begin(id, prepared.map { it.manifest.digest }); journal = true
                    status.value = "发布已校验版本…"
                    prepared.forEach(store::publish)
                    registry.finish(id, entries); journal = false
                } }
                status.value = "已安装 ${entries.size} 个工作流"; version.update { it + 1 }
                entries
            } catch (failure: Throwable) {
                status.value = if (failure is CancellationException) "安装已取消" else "安装失败：${failure.message?.take(160) ?: failure.javaClass.simpleName}"
                throw failure
            } finally { withContext(NonCancellable) {
                if (journal) catalog.withLock {
                    registry.pending().firstOrNull { it.first == id }?.second?.forEach { if (!registry.known(it)) store.remove(it) }
                    registry.clearOperation(id)
                }
                removeSkillTree(stage)
            } }
        } }

    private fun prepare(archive: Path, stage: Path, selection: String): List<PreparedSkill> {
        val extracted = stage.resolve("extracted")
        val executable = SkillArchive().extract(archive, extracted)
        val children = Files.list(extracted).use { it.toList() }
        val base = children.singleOrNull()?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) } ?: extracted
        val candidates = store.discover(base).filter { path -> selection.isBlank() || base.relativize(path).joinToString("/") == selection }
        require(candidates.isNotEmpty() && candidates.size <= 128) { "No selected SKILL.md or more than 128 skills; specify selection" }
        val published = Files.createDirectory(stage.resolve("prepared"))
        return candidates.map { candidate ->
            store.prepare(extracted, candidate, published, executable).copy(relative = base.relativize(candidate).joinToString("/"))
        }
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream) {
        val bytes = ByteArray(65536); var total = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(bytes); if (count < 0) break
            total += count; require(total <= 64L * 1024 * 1024) { "Skill ZIP exceeds 64 MiB" }; output.write(bytes, 0, count)
        }
    }
}
