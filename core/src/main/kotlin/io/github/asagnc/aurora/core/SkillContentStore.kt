package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import kotlinx.serialization.encodeToString

data class PreparedSkill(val relative: String, val manifest: SkillManifest, val staged: Path)

/** Cooperative immutable content store. Root/same-UID programs are not sandboxed by this layout. */
class SkillContentStore(val root: Path) {
    init { require(root.isAbsolute) }
    fun initialize() {
        require(!Files.isSymbolicLink(root.parent) && !Files.isSymbolicLink(root))
        Files.createDirectories(root)
    }
    fun directory(hash: String): Path {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        require(!Files.isSymbolicLink(root) && !Files.isSymbolicLink(root.resolve(hash)))
        return root.resolve(hash)
    }
    fun discover(extracted: Path): List<Path> = Files.walk(extracted, 10).use { paths ->
        val roots = paths.filter { it.fileName.toString() == "SKILL.md" && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { it.parent }.sorted().toList()
        roots.filter { candidate -> roots.none { parent -> parent != candidate && candidate.startsWith(parent) } }
    }
    fun prepare(extracted: Path, selected: Path, stage: Path, executable: Set<String>): PreparedSkill {
        require(selected.startsWith(extracted) && !Files.isSymbolicLink(selected))
        val relative = extracted.relativize(selected).joinToString("/")
        val instruction = selected.resolve("SKILL.md")
        require(Files.size(instruction) <= 262144)
        val metadata = SkillDocumentParser.parse(Files.readAllBytes(instruction), selected.fileName.toString()).metadata
        val files = Files.walk(selected).use { paths -> paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { path ->
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val name = selected.relativize(path).joinToString("/")
                SkillFile(name, Files.size(path), hashFile(path), extracted.relativize(path).joinToString("/") in executable)
            }.toList().sortedBy { it.path } }
        val manifest = SkillManifest(metadata, files)
        val encoded = WireJson.encodeToString(manifest)
        require(encoded.toByteArray().size <= 2 * 1024 * 1024) { "Package manifest exceeds 2 MiB" }
        val target = stage.resolve(manifest.digest)
        if (!Files.exists(target)) {
            Files.createDirectory(target)
            Files.move(selected, target.resolve("content"), StandardCopyOption.ATOMIC_MOVE)
            atomicText(target.resolve("manifest.json"), encoded)
        }
        return PreparedSkill(relative, manifest, target)
    }
    fun publish(prepared: PreparedSkill) {
        val target = directory(prepared.manifest.digest)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            require(manifest(prepared.manifest.digest) == prepared.manifest)
            prepared.manifest.files.forEach { verifiedFile(prepared.manifest.digest, it.path) }
        } else Files.move(prepared.staged, target, StandardCopyOption.ATOMIC_MOVE)
    }
    fun manifest(hash: String): SkillManifest {
        val file = directory(hash).resolve("manifest.json")
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 2 * 1024 * 1024)
        val json = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { String(it.readNBytes(2 * 1024 * 1024 + 1), Charsets.UTF_8) }
        require(jsonWithinDepth(json))
        return WireJson.decodeFromString<SkillManifest>(json).also { require(it.digest == hash) { "Skill manifest integrity mismatch" } }
    }
    fun verifiedFile(hash: String, relative: String): Path {
        val expected = manifest(hash).files.singleOrNull { it.path == relative } ?: error("File absent from pinned skill manifest")
        var path = directory(hash).resolve("content")
        require(!Files.isSymbolicLink(path))
        for (part in skillRelativePath(relative)) { path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == expected.size && hashFile(path) == expected.sha256) {
            "Skill file integrity mismatch"
        }
        return path
    }
    fun read(hash: String, relative: String, offset: Long = 0, limit: Int = 65536): ByteArray {
        require(offset >= 0 && limit in 1..65536)
        return FileChannel.open(verifiedFile(hash, relative), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            require(offset <= channel.size())
            val buffer = ByteBuffer.allocate(minOf(limit.toLong(), channel.size() - offset).toInt())
            while (buffer.hasRemaining() && channel.read(buffer, offset + buffer.position()) > 0) { }
            buffer.array().copyOf(buffer.position())
        }
    }
    fun remove(hash: String) { removeSkillTree(directory(hash)) }
    private fun hashFile(path: Path): String {
        val hash = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val bytes = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(bytes); if (count < 0) break; hash.update(bytes, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun atomicText(path: Path, text: String) {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(text.toByteArray()); while (bytes.hasRemaining()) channel.write(bytes); channel.force(true)
        }
    }
}
