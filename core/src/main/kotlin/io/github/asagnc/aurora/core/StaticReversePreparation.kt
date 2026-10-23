package io.github.asagnc.aurora.core

import java.nio.file.*

/** Prepares an unpublished directory only. Publication, Root ownership and leases belong to the manager. */
object StaticReversePreparation {
    fun prepare(component: StaticReverseComponent, archive: Path, target: Path,
        mode: (Path, Int) -> Unit = { path, bits -> Files.setAttribute(path, "unix:mode", bits, LinkOption.NOFOLLOW_LINKS) }) {
        require(component in StaticReverseTools.components) { "Component is not in the compiled catalogue" }
        verifyStaticReverseArchive(component, archive)
        val absolute = target.toAbsolutePath().normalize()
        var parent = absolute.parent
        while (parent != null) {
            require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) { "Tool staging ancestor is not a directory" }
            parent = parent.parent
        }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        try { when (component.id) {
            "jre" -> {
                val unpacker = RootfsArchive(RootfsBudget(compressed = 128L*1024*1024, expanded = 512L*1024*1024))
                val plan = unpacker.scan(archive)
                require(plan.all { it.path == "jdk-27+35-jre" || it.path.startsWith("jdk-27+35-jre/") })
                require(plan.any { it.path == "jdk-27+35-jre/bin/java" && it.kind == "file" })
                unpacker.extract(archive, target, plan, mode = mode)
            }
            "jadx" -> {
                SkillArchive(SkillArchiveBudget(compressed = 128L*1024*1024, expanded = 256L*1024*1024,
                    file = 128L*1024*1024)).extract(archive, target)
                require(Files.isRegularFile(target.resolve("lib/jadx-1.5.6-all.jar"), LinkOption.NOFOLLOW_LINKS))
            }
            "apktool" -> {
                Files.createDirectory(target)
                Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS).use { input ->
                    Files.newOutputStream(target.resolve("apktool.jar"), StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                        val bytes = ByteArray(65536); var total = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Tool preparation cancelled")
                            val count = input.read(bytes); if (count < 0) break
                            total += count; require(total <= component.compressedBytes)
                            output.write(bytes, 0, count)
                        }
                    }
                }
                verifyStaticReverseArchive(component, target.resolve("apktool.jar"))
            }
        }
        verifyStaticReverseArchive(component, archive)
        } catch (failure: Throwable) {
            try { removeSkillTree(target) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
