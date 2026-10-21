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
    private val publication = SkillPublication(store, registry)
    private val staging = app.filesDir.toPath().resolve("spaces/resources/.skill-staging")
    private val network = SkillNetwork()
    val version = MutableStateFlow(0L)
    val status = MutableStateFlow<String?>(null)

    suspend fun recover() = withContext(Dispatchers.IO) { installer.withLock {
        app.spaces.sharedResources(); store.initialize()
        require(!Files.isSymbolicLink(staging)); Files.createDirectories(staging)
        publication.recover()
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
            val signal = android.os.CancellationSignal()
            SkillArchiveTransfer.copy(archive, {
                val descriptor = requireNotNull(app.contentResolver.openAssetFileDescriptor(uri, "r", signal))
                try { descriptor.createInputStream() } catch (failure: Throwable) { descriptor.close(); throw failure }
            }, signal::cancel)
        }
    }

    suspend fun local(owner: String, relative: String, selection: String = "", options: SkillInstallOptions = SkillInstallOptions()): List<SkillEntry> {
        val sourcePath = withContext(Dispatchers.IO) {
            var path = app.spaces.commandRoot(owner)
            for (part in skillRelativePath(relative)) { path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 64L * 1024 * 1024)
            path
        }
        return install(SkillSource("conversation:$owner/$relative", selection, "会话 ZIP · $relative"), options) { archive ->
            SkillArchiveTransfer.copy(archive, { Files.newInputStream(sourcePath, LinkOption.NOFOLLOW_LINKS) })
        }
    }

    private suspend fun install(source: SkillSource, options: SkillInstallOptions, fetch: suspend (Path) -> Unit): List<SkillEntry> =
        installer.withLock { withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val stage = Files.createDirectory(staging.resolve(id))
            var committed = false
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
                    SkillEntry(options.replaceId ?: skillSourceId(source, skill.relative),
                        skill.manifest.digest, true, source.copy(selection = skill.relative), skill.manifest.metadata, skill.manifest.bytes)
                }
                currentCoroutineContext().ensureActive()
                // Once publication begins, finish the journal/index transaction even if its caller cancels.
                catalog.withLock {
                    options.replaceId?.let { require(registry.entries().any { entry -> entry.id == it }) { "Update target no longer exists" } }
                    status.value = "发布已校验版本…"
                    publication.publish(id, prepared) {
                        registry.finish(id, entries)
                        committed = true
                        // Notify even if the caller cancels exactly as the committed result is dispatched.
                        version.update { it + 1 }
                        status.value = "已安装 ${entries.size} 个工作流"
                    }
                }
                val installed = registry.entries().associateBy { it.id }
                entries.map { installed.getValue(it.id) }
            } catch (failure: Throwable) {
                status.value = if (committed) "安装已完成，调用已结束；已安装版本可用"
                    else if (failure is CancellationException) "安装已取消"
                    else "安装失败，请检查归档、网络或存储；原有版本保留"
                throw failure
            } finally { withContext(NonCancellable) {
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

}
