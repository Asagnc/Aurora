package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest

/** Fixed upstream standalone import into private staging. Does not grant a Root runtime qualification. */
object NetworkProxyPreparation {
    val component = StaticReverseComponent("mitmdump", "12.3.0",
        "https://downloads.mitmproxy.org/12.3.0/mitmproxy-12.3.0-linux-aarch64.tar.gz",
        "f8756fa831c4abe431dd922ae7291b158b465285562b35d12e5669cbfc29d1f3", 98927179)
    const val selectedSha256 = "3adf33868f5477ff65d954b3fbe5dc5eca9703b5f02692500e880b8789caccb1"
    private val members = mapOf("mitmproxy" to 33648424L, "mitmdump" to 32495056L, "mitmweb" to 33732336L)

    fun prepare(selected: StaticReverseComponent, archive: Path, target: Path, mode: (Path, Int) -> Unit) {
        require(selected == component)
        verifyStaticReverseArchive(selected, archive)
        var parent = target.toAbsolutePath().normalize().parent
        while (parent != null) {
            require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)); parent = parent.parent
        }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        try {
            val unpacker = RootfsArchive(RootfsBudget(compressed = 128L * 1024 * 1024,
                expanded = 128L * 1024 * 1024, file = 40L * 1024 * 1024, entries = 3))
            val plan = unpacker.scan(archive)
            require(plan.size == members.size && plan.all { it.kind == "file" && members[it.path] == it.bytes })
            unpacker.extract(archive, target, plan, mode = mode)
            for (name in members.keys) requireSharedArm64Tool(target.resolve(name))
            Files.newInputStream(target.resolve("mitmdump"), LinkOption.NOFOLLOW_LINKS).use { source ->
                val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Proxy import cancelled")
                    val count = source.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
                }
                require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == selectedSha256)
            }
            Files.delete(target.resolve("mitmproxy")); Files.delete(target.resolve("mitmweb"))
            mode(target.resolve("mitmdump"), 493)
            verifyStaticReverseArchive(selected, archive)
        } catch (failure: Throwable) {
            try { removeSkillTree(target) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
