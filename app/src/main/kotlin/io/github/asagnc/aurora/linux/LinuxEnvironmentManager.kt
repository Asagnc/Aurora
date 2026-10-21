package io.github.asagnc.aurora.linux

import android.os.Build
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

internal class LinuxInstallCapabilityException(cause: Throwable) : java.io.IOException(
    "Linux rootfs requires hardlinks; connect the Root installation backend", cause)

/** Shared image generations, separate from conversation homes and from native Android executors. */
internal class LinuxEnvironmentManager(private val app: AuroraApplication) {
    internal val registry = LinuxRegistry(app.conversations)
    internal val catalog = Mutex()
    private val installer = Mutex()
    private val root = app.filesDir.toPath().resolve("spaces/resources/linux")
    private val staging = app.filesDir.toPath().resolve("spaces/resources/.linux-staging")
    private val network = SkillNetwork()
    val revision = MutableStateFlow(0L)
    val status = MutableStateFlow("尚未安装 Linux 环境")
    val image get() = UbuntuBaseImages.current(LinuxArchitecture.fromAbi(Build.SUPPORTED_ABIS.first()))

    suspend fun recover() = withContext(Dispatchers.IO) { installer.withLock {
        app.spaces.sharedResources()
        require(!Files.isSymbolicLink(root) && !Files.isSymbolicLink(staging))
        Files.createDirectories(root); Files.createDirectories(staging)
        catalog.withLock { registry.pending().forEach { (operation, id) ->
            if (registry.versions().none { it.id == id }) removeSkillTree(directory(id))
            registry.clear(operation)
        } }
        val retained = LinuxPreparationState.recover(staging, null)
        status.value = if (retained > 0) "有未确认的 Root 安装，连接 Root 后可清理恢复"
            else if (registry.current() == null) "尚未安装 Linux 环境" else "基础环境已安装，执行能力待探测"
    } }

    suspend fun install(): LinuxVersion = installer.withLock { withContext(Dispatchers.IO) {
        val source = image
        val privileged = app.root.linuxClient
        val retained = LinuxPreparationState.recover(staging, privileged)
        val installed = catalog.withLock {
            registry.versions().firstOrNull { it.id == source.sha256 }?.also { version ->
                LinuxElf.validateRootfs(directory(version.id).resolve("rootfs"), source.architecture)
                withContext(NonCancellable) {
                    registry.select(version.id); revision.update { it + 1 }
                    status.value = if (retained > 0) "有未确认的 Root 安装，连接 Root 后可清理恢复" else "基础环境已安装，执行能力待探测"
                }
            }
        }
        if (installed != null) return@withContext installed
        val operation = UUID.randomUUID().toString()
        val stage = Files.createDirectory(staging.resolve(operation))
        var committed = false
        var original: Throwable? = null
        try {
            require(android.os.StatFs(stage.toString()).availableBytes >= 128L * 1024 * 1024) { "Insufficient download space" }
            if (privileged == null) runInterruptible { checkHardlinks(stage) }
            status.value = "下载 Ubuntu ${source.version} ${source.architecture.imageName}…"
            val archive = stage.resolve("base.tar.gz"); network.download(source.url, archive)
            runInterruptible { verifyArchive(archive, source.sha256) }
            status.value = "校验 Linux 布局、链接与可用空间…"
            val unpacker = RootfsArchive()
            val plan = runInterruptible { unpacker.scan(archive) }
            val bytes = plan.sumOf { it.bytes }
            require(android.os.StatFs(stage.toString()).availableBytes >= bytes + plan.size * 4096L + 64L * 1024 * 1024) { "Insufficient expanded space" }
            val prepared = Files.createDirectory(stage.resolve("prepared"))
            val manifest = LinuxRootfsManifest(source, plan.size, bytes)
            status.value = "解压基础环境…"
            val hooks = privileged?.let { client -> LinuxRootLinkBatch(client, stage,
                LinuxPreparationTicket(operation, client.identity, source.sha256, app.applicationInfo.uid), manifest).hooks() }
                ?: RootfsExtractionHooks()
            runInterruptible {
                unpacker.extract(archive, prepared.resolve("rootfs"), plan, hooks) { path, mode -> android.system.Os.chmod(path.toString(), mode) }
            }
            runInterruptible { LinuxElf.validateRootfs(prepared.resolve("rootfs"), source.architecture) }
            writeMetadata(prepared.resolve("entries.json"), WireJson.encodeToString(plan))
            writeMetadata(prepared.resolve("manifest.json"), WireJson.encodeToString(manifest))
            val version = LinuxVersion(source.sha256, manifest, System.currentTimeMillis())
            currentCoroutineContext().ensureActive()
            catalog.withLock { withContext(NonCancellable) {
                registry.begin(operation, version.id)
                require(!Files.exists(directory(version.id), LinkOption.NOFOLLOW_LINKS))
                Files.move(prepared, directory(version.id), StandardCopyOption.ATOMIC_MOVE)
                registry.publish(operation, version); committed = true
                revision.update { it + 1 }; status.value = "基础环境已安装，执行能力待探测"
            } }
            version
        } catch (failure: Throwable) {
            original = failure
            status.value = if (failure is LinuxInstallCapabilityException) "当前权限无法创建硬链接，请先连接 Root 再安装"
                else if (committed) "基础环境已安装，执行能力待探测"
                else if (failure is CancellationException) "Linux 安装已取消" else "Linux 安装失败，原有环境保留"
            throw failure
        } finally { withContext(NonCancellable) {
            try {
                catalog.withLock { registry.pending().firstOrNull { it.first == operation }?.let { (_, id) ->
                    if (registry.versions().none { it.id == id }) removeSkillTree(directory(id))
                    registry.clear(operation)
                } }
                if (LinuxPreparationState.pending(stage)) privileged?.discard(operation) else removeSkillTree(stage)
            } catch (cleanup: Throwable) {
                if (LinuxPreparationState.pending(stage)) status.value = "Root 安装状态未确认，暂存已保留；重新连接 Root 后清理"
                if (original != null) original.addSuppressed(cleanup) else throw cleanup
            }
        } }
    } }

    suspend fun cleanup(): Int = installer.withLock { withContext(Dispatchers.IO) {
        val retained = LinuxPreparationState.recover(staging, app.root.linuxClient)
        catalog.withLock {
        var count = 0
        registry.unused().forEach { id -> withContext(NonCancellable) {
            val operation = UUID.randomUUID().toString()
            registry.reserveRemoval(operation, id); removeSkillTree(directory(id)); registry.clear(operation); count++
        } }
        status.value = if (retained > 0) "有未确认的 Root 安装，连接 Root 后可清理恢复"
            else if (registry.current() == null) "尚未安装 Linux 环境" else "基础环境已安装，执行能力待探测"
        revision.update { it + 1 }; count
    } } }
    suspend fun probe(owner: String? = null): kotlinx.serialization.json.JsonObject {
        if (owner == null) return catalog.withLock {
            val current = requireNotNull(registry.current()) { "Install a Linux base first" }
            LinuxProotProbe(app).inspect(directory(current).resolve("rootfs"))
        }
        var token: String? = null
        var failure: Throwable? = null
        try {
            currentCoroutineContext().ensureActive()
            val current = withContext(NonCancellable) { catalog.withLock {
                val id = requireNotNull(registry.current()) { "Install a Linux base first" }
                token = registry.lease(owner, id)
                id
            } }
            currentCoroutineContext().ensureActive()
            return LinuxProotProbe(app).inspect(directory(current).resolve("rootfs"))
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { token?.let { lease -> withContext(NonCancellable) {
            try { catalog.withLock { registry.release(lease) } }
            catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        } } }
    }
    suspend fun probeRoot(owner: String, artifact: String) = LinuxRootProbe(app).inspect(owner, artifact)
    internal fun directory(id: String): Path {
        require(id.matches(Regex("[a-f0-9]{64}")) && !Files.isSymbolicLink(root) && !Files.isSymbolicLink(root.resolve(id)))
        return root.resolve(id)
    }
    private fun writeMetadata(path: Path, value: String) {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val encoded = ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
            while (encoded.hasRemaining()) channel.write(encoded)
            channel.force(true)
        }
    }
    private fun checkHardlinks(stage: Path) {
        val source = stage.resolve(".link-source")
        val target = stage.resolve(".link-target")
        var original: Throwable? = null
        try {
            Files.createFile(source)
            Files.createLink(target, source)
            require(Files.isSameFile(source, target)) { "Hardlink identity unavailable" }
        } catch (failure: java.io.IOException) {
            val unavailable = LinuxInstallCapabilityException(failure)
            original = unavailable; throw unavailable
        } catch (failure: Throwable) { original = failure; throw failure }
        finally {
            try { Files.deleteIfExists(target); Files.deleteIfExists(source) }
            catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
        }
    }
    private fun verifyArchive(path: Path, expected: String) {
        val hash = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input -> val bytes = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(bytes); if (count < 0) break; hash.update(bytes, 0, count)
            }
        }
        require(hash.digest().joinToString("") { "%02x".format(it) } == expected) { "Official image SHA256 mismatch" }
    }
}
