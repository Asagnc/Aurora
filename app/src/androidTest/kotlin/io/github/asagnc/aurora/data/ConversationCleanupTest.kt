package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.FileEdit
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationCleanupTest {
    @Test fun browserCleanupFailureKeepsDeletionRecoverable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        val store = ConversationStore(database)
        val spaces = SpaceStore(context, store)
        val id = store.create()
        val root = context.filesDir.toPath().resolve("spaces/conversations/$id")
        try {
            spaces.conversation(id).editBatch(listOf(FileEdit("owned", null, "keep until browser cleared")))
            try {
                spaces.delete(id, {}, { error("browser cleanup interrupted") })
                fail("Deletion must not report success")
            } catch (_: IllegalStateException) { }
            assertTrue(Files.exists(root.resolve("owned")))
            val deleting = store.read { db -> db.rawQuery("SELECT deleting FROM conversations WHERE id = ?", arrayOf(id))
                .use { it.moveToFirst(); it.getInt(0) } }
            assertEquals(1, deleting)
            var recovered = false
            spaces.recoverDeletes { owner -> assertEquals(id, owner); recovered = true }
            assertTrue(recovered)
            assertFalse(Files.exists(root))
            assertNull(store.header(id))
        } finally {
            spaces.recoverDeletes()
            database.close()
            context.deleteDatabase(name)
        }
    }
}
