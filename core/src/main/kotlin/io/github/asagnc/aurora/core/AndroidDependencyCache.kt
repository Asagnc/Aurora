package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** A compiled immutable public seed, never copied back from a conversation. */
object AndroidDependencyCache {
    const val manifestSha256 = "712153f795d65584e56a8a9effeb34bd5a56de08882965715f4a2b8dffd326be"
    const val nativeBaseSha256 = "f899eda7aee47fe3edc11546f26d7853aa6f8962f08bb2225f59a327b2327042"
    const val seedProbeSha256 = "d151f5579b6c805d0420f5cdd39419c7c6fe6946744fe54cacdab9c27e4df2c1"
    const val guestPath = "/opt/android-host/dependency-cache"

    internal fun verify(root: Path, expectedManifest: String = manifestSha256) {
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root))
        val header = root.resolve("manifest.json")
        require(Files.isRegularFile(header, NOFOLLOW_LINKS))
        val bytes = Files.newInputStream(header).use { it.readNBytes(32 * 1024 * 1024 + 1) }
        require(bytes.isNotEmpty() && bytes.size <= 32 * 1024 * 1024)
        require(sha256(bytes) == expectedManifest) { "Public dependency manifest digest differs" }
        val manifest = WireJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(manifest.number("format") == 1L && manifest.text("gradleVersion") == "9.8.2")
        val seed = manifest.getValue("seed").jsonObject
        require(seed.text("nativeSdkSha256") == nativeBaseSha256 && seed.text("probeSha256") == seedProbeSha256)
        val files = manifest.getValue("files").jsonObject
        require(files.size in 1..64000 && manifest.number("fileCount") == files.size.toLong())
        val total = files.values.sumOf { requireNotNull(it.jsonObject.number("bytes")) }
        require(total in 1..(2L shl 30) && manifest.number("bytes") == total)
        files.forEach { (name, value) -> verifyFile(root, name, value.jsonObject) }
        val expected = files.keys + "manifest.json"
        val actual = mutableSetOf<String>()
        Files.walk(root).use { paths -> paths.forEach { path ->
            require(!Files.isSymbolicLink(path)) { "Dependency cache links are forbidden" }
            require(Files.isDirectory(path, NOFOLLOW_LINKS) || Files.isRegularFile(path, NOFOLLOW_LINKS))
            if (Files.isRegularFile(path, NOFOLLOW_LINKS)) actual += root.relativize(path).joinToString("/")
        } }
        require(actual == expected) { "Public dependency cache inventory differs" }
    }

    private fun verifyFile(root: Path, name: String, record: JsonObject) {
        require(name.startsWith("modules-2/") && name.matches(Regex("[A-Za-z0-9_./+@=-]+")))
        require(name.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
        val size = requireNotNull(record.number("bytes")); val sha = requireNotNull(record.text("sha256"))
        require(size in 0..(512L shl 20) && sha.matches(Regex("[a-f0-9]{64}")))
        var path = root
        name.split('/').forEach { part -> path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) == size)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(65536); var count = 0L
            while (true) {
                val read = input.read(buffer); if (read < 0) break
                count += read; require(count <= size); digest.update(buffer, 0, read)
            }
            require(count == size)
        }
        require(hex(digest.digest()) == sha) { "Public dependency file digest differs" }
    }

    private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
