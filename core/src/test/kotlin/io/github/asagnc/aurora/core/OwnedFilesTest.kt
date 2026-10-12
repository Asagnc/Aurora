package io.github.asagnc.aurora.core

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class OwnedFilesTest {
    private fun store() = Files.createTempDirectory("aurora-test").let { it to OwnedFiles(it) }

    @Test fun conflictPreventsAllWrites() = runTest {
        val (root, files) = store()
        Files.writeString(root.resolve("a"), "original")
        assertFailsWith<IllegalArgumentException> {
            files.editBatch(listOf(FileEdit("b", null, "new"), FileEdit("a", "wrong", "changed")))
        }
        assertFalse(Files.exists(root.resolve("b")))
        assertEquals("original", Files.readString(root.resolve("a")))
    }

    @Test fun editsRequireCurrentDigestAndReadsPreserveOrder() = runTest {
        val (root, files) = store()
        Files.writeString(root.resolve("a"), "original")
        val version = files.readBatch(listOf(FileRead("a"))).single().version
        files.editBatch(listOf(FileEdit("a", version, "changed"), FileEdit("nested/b", null, "new")))
        val results = files.readBatch(listOf(FileRead("nested/b"), FileRead("a"), FileRead("missing")))
        assertEquals(listOf("new", "changed", null), results.map { it.text })
        assertNotNull(results.last().error)
    }

    @Test fun ownedPathsRejectTraversalAndInternalJournal() {
        val (_, files) = store()
        assertFailsWith<IllegalArgumentException> { files.resolve("../outside") }
        assertFailsWith<IllegalArgumentException> { files.resolve(".aurora-transactions/journal") }
    }

    @Test fun sharedResourcesRejectEdits() = runTest {
        val (root, _) = store()
        assertFailsWith<IllegalArgumentException> { OwnedFiles(root, false).editBatch(listOf(FileEdit("a", null, "new"))) }
    }

    @Test fun interruptedGroupRestoresOldContentAndRemovesNewFile() = runTest {
        val (root, files) = store()
        val tx = root.resolve(".aurora-transactions/interrupted")
        Files.createDirectories(tx)
        Files.writeString(tx.resolve("0.old"), "old")
        Files.writeString(root.resolve("a"), "new")
        Files.writeString(root.resolve("b"), "created")
        val journal = EditJournal(listOf(
            FileBackup("a", true, OwnedFiles.digest("old".toByteArray()), OwnedFiles.digest("new".toByteArray())),
            FileBackup("b", false, null, OwnedFiles.digest("created".toByteArray()))))
        Files.writeString(tx.resolve("journal.json"), WireJson.encodeToString(EditJournal.serializer(), journal))
        files.recover()
        assertEquals("old", Files.readString(root.resolve("a")))
        assertFalse(Files.exists(root.resolve("b")))
        assertFalse(Files.exists(tx))
        files.recover()
    }

    @Test fun recoveryNeverOverwritesUnrelatedLaterChange() = runTest {
        val (root, files) = store()
        val tx = root.resolve(".aurora-transactions/interrupted")
        Files.createDirectories(tx)
        Files.writeString(tx.resolve("0.old"), "old")
        Files.writeString(root.resolve("a"), "foreign change")
        val journal = EditJournal(listOf(FileBackup("a", true,
            OwnedFiles.digest("old".toByteArray()), OwnedFiles.digest("new".toByteArray()))))
        Files.writeString(tx.resolve("journal.json"), WireJson.encodeToString(EditJournal.serializer(), journal))
        assertFailsWith<IllegalArgumentException> { files.recover() }
        assertEquals("foreign change", Files.readString(root.resolve("a")))
        assertTrue(Files.exists(tx))
    }
}
