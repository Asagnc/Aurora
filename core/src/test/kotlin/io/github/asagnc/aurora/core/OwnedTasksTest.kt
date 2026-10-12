package io.github.asagnc.aurora.core

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class OwnedTasksTest {
    @Test fun deletionWaitsForCleanupAndRejectsNewWorkOnlyForItsOwner() = runTest {
        val tasks = OwnedTasks()
        var cleaned = false
        val entered = CompletableDeferred<Unit>()
        val job = launch { tasks.run("a") {
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { yield(); cleaned = true } }
        } }
        entered.await()
        tasks.quiesce("a")
        assertTrue(job.isCompleted)
        assertTrue(cleaned)
        assertFailsWith<IllegalArgumentException> { tasks.run("a") {} }
        assertEquals("ok", tasks.run("b") { "ok" })
    }
}
