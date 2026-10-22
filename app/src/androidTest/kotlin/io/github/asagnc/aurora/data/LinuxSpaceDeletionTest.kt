package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxSpaceDeletionTest {
    @Test fun deletionCannotCascadeAwayUnconfirmedLinuxLease() = checkDeletion(false)
    @Test fun recoveryCannotDeleteFilesWhileLinuxLeaseRemains() = checkDeletion(true)

    private fun checkDeletion(recovery: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linux-space-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        val store = ConversationStore(database)
        val spaces = SpaceStore(context, store)
        val registry = LinuxRegistry(store)
        val owner = store.create("lease deletion")
        val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
        val manifest = LinuxRootfsManifest(image, 1, 1, LinuxBackend.CHROOT)
        val version = LinuxVersion(manifest.generationId(), manifest, 1)
        registry.begin("publish", version.id); registry.publish("publish", version)
        val token = registry.lease(owner, version.id)
        val root = spaces.commandRoot(owner)
        Files.writeString(root.resolve("preserved.txt"), "keep")
        try {
            if (recovery) store.write { it.update("conversations", values("deleting" to 1), "id=?", arrayOf(owner)) }
            if (recovery) assertEquals(1, spaces.recoverDeletes()) else {
                try { spaces.delete(owner, {}); fail("Unconfirmed Linux lease was ignored") }
                catch (_: LinuxRunPendingException) { }
            }
            assertEquals("keep", Files.readString(root.resolve("preserved.txt")))
            assertTrue(store.read { db -> db.rawQuery("SELECT 1 FROM linux_leases WHERE token=?", arrayOf(token)).use { it.moveToFirst() } })
            registry.release(token)
            if (recovery) spaces.recoverDeletes() else spaces.delete(owner, {})
            assertFalse(Files.exists(root))
            assertFalse(store.read { db -> db.rawQuery("SELECT 1 FROM conversations WHERE id=?", arrayOf(owner)).use { it.moveToFirst() } })
        } finally {
            registry.release(token)
            spaces.delete(owner, {})
            database.close(); context.deleteDatabase(name)
        }
    }
}
