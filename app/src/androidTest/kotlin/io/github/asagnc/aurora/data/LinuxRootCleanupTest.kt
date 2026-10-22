package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.removeSkillTree
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Callback fixtures use App-owned files; no Root capability is simulated. */
@RunWith(AndroidJUnit4::class)
class LinuxRootCleanupTest {
    @Test fun unavailableRootRetainsDataAndLaterRecoveryFinishesDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "root-cleanup-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        val store = ConversationStore(database)
        var available = false
        lateinit var root: Path
        val spaces = SpaceStore(context, store) {
            assertTrue(Files.exists(root.resolve("ordinary.txt")))
            check(available) { "Root unavailable" }; removeSkillTree(root.resolve(".aurora-linux"))
        }
        val owner = store.create("cleanup")
        root = spaces.commandRoot(owner)
        Files.write(root.resolve("ordinary.txt"), "preserved".toByteArray(Charsets.UTF_8))
        Files.createDirectory(root.resolve(".aurora-linux"))
        try {
            try { spaces.delete(owner, {}); fail("Unavailable Root discarded data") } catch (_: LinuxFilesPendingException) { }
            assertEquals(1, spaces.recoverDeletes())
            assertTrue(Files.exists(root.resolve("ordinary.txt")))
            available = true
            assertEquals(0, spaces.recoverDeletes()); assertFalse(Files.exists(root))
        } finally { available = true; spaces.recoverDeletes(); database.close(); context.deleteDatabase(name) }
    }
    @Test fun blockedRootDeletionDoesNotBlockUnrelatedStartupCleanup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "root-cleanup-others-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        val store = ConversationStore(database)
        val spaces = SpaceStore(context, store) { error("Root unavailable") }
        val blocked = store.create("blocked"); val ordinary = store.create("ordinary")
        val blockedPath = spaces.commandRoot(blocked); val ordinaryPath = spaces.commandRoot(ordinary)
        Files.createDirectory(blockedPath.resolve(".aurora-linux"))
        try {
            store.write { it.execSQL("UPDATE conversations SET deleting=1") }
            assertEquals(1, spaces.recoverDeletes())
            assertTrue(Files.exists(blockedPath)); assertFalse(Files.exists(ordinaryPath))
        } finally { removeSkillTree(blockedPath); database.close(); context.deleteDatabase(name) }
    }
}
