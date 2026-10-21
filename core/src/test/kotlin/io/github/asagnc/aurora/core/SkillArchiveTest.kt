package io.github.asagnc.aurora.core

import java.nio.file.*
import org.apache.commons.compress.archivers.zip.*
import kotlin.test.*

class SkillArchiveTest {
    private fun fixture(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("aurora-skill-test")
        try { block(root) } finally { removeSkillTree(root) }
    }
    private fun archive(root: Path, entries: List<Pair<String, String>>, symlink: Boolean = false): Path {
        val zip = root.resolve("source.zip")
        ZipArchiveOutputStream(zip).use { output ->
            entries.forEach { (name, value) ->
                val entry = ZipArchiveEntry(name)
                if (symlink) entry.unixMode = 0xa1ff
                output.putArchiveEntry(entry); output.write(value.toByteArray()); output.closeArchiveEntry()
            }
        }
        return zip
    }
    @Test fun extractsUnicodeAndPublishesVerifiedContent() = fixture { root ->
        val zip = archive(root, listOf("example/SKILL.md" to "---\nname: example\ndescription: Reads files\n---\nRead first.", "example/参考.txt" to "内容"))
        val target = root.resolve("extracted")
        val executable = SkillArchive().extract(zip, target)
        val store = SkillContentStore(root.resolve("store")); store.initialize()
        val stage = Files.createDirectory(root.resolve("prepared"))
        val prepared = store.prepare(target, store.discover(target).single(), stage, executable)
        store.publish(prepared)
        assertEquals("内容", store.read(prepared.manifest.digest, "参考.txt").toString(Charsets.UTF_8))
        assertFails { store.read(prepared.manifest.digest, "../source.zip") }
        Files.writeString(store.directory(prepared.manifest.digest).resolve("content/参考.txt"), "破坏")
        assertFails { store.read(prepared.manifest.digest, "参考.txt") }
    }
    @Test fun rejectsTraversalWithoutWritingOutsideStaging() = fixture { root ->
        val target = root.resolve("extracted")
        assertFails { SkillArchive().extract(archive(root, listOf("../escaped" to "bad")), target) }
        assertFalse(Files.exists(root.resolve("escaped"))); assertFalse(Files.exists(target))
    }
    @Test fun rejectsDuplicateEntries() = fixture { root ->
        val target = root.resolve("extracted")
        assertFails { SkillArchive().extract(archive(root, listOf("same" to "one", "same" to "two")), target) }
        assertFalse(Files.exists(target))
    }
    @Test fun rejectsSymbolicLinks() = fixture { root ->
        val target = root.resolve("extracted")
        assertFails { SkillArchive().extract(archive(root, listOf("link" to "../outside"), true), target) }
        assertFalse(Files.exists(target))
    }
    @Test fun rejectsFileDirectoryConflictsAndExpansionBudget() = fixture { root ->
        assertFails { SkillArchive().extract(archive(root, listOf("folder" to "file", "folder/child" to "text")), root.resolve("conflict")) }
        Files.delete(root.resolve("source.zip"))
        assertFails { SkillArchive(SkillArchiveBudget(expanded = 20, file = 20)).extract(
            archive(root, listOf("large" to "x".repeat(10000))), root.resolve("large")) }
        assertFalse(Files.exists(root.resolve("large")))
    }
    @Test fun corruptedArchiveFailsWithoutPublishedContent() = fixture { root ->
        val zip = archive(root, listOf("value" to "hello"))
        val bytes = Files.readAllBytes(zip)
        // Corrupt both local data and its checksum expectations through the central CRC.
        val central = bytes.indices.first { i -> i + 3 < bytes.size && bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte() }
        bytes[central + 16] = (bytes[central + 16].toInt() xor 0xff).toByte(); Files.write(zip, bytes)
        assertFails { SkillArchive().extract(zip, root.resolve("extracted")) }
        assertFalse(Files.exists(root.resolve("extracted")))
    }
}
