package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.*

class RootManagedProcessWaitTest {
    @Test fun captureAcceptsItsFullBudgetWhileFridaStillRejectsIt() = fixture { directory ->
        child(directory, 20).useChild { process ->
            assertFailsWith<IllegalArgumentException> { RootManagedProcessWait.await(process, 1_825_000) }
            assertFailsWith<IllegalArgumentException> { RootManagedProcessWait.awaitCapture(process, 1_825_001) }
            assertEquals(RootManagedProcessExit(7, false, false), RootManagedProcessWait.awaitCapture(process, 1_825_000))
        }
    }

    @Test fun normalCoordinatorExitKeepsItsCode() = fixture { directory ->
        child(directory, 20).useChild { process ->
            assertEquals(RootManagedProcessExit(7, false, false), RootManagedProcessWait.await(process, 5_000))
        }
    }

    @Test fun interruptedOwnerWaitsForCooperativeExitAndRestoresInterrupt() = fixture { directory ->
        val ready = CountDownLatch(1)
        var result: RootManagedProcessExit? = null
        var failure: Throwable? = null
        var restored = false
        val owner = Thread {
            try {
                child(directory, 60_000).useChild { process ->
                    assertEquals("ready", process.inputStream.bufferedReader().readLine())
                    ready.countDown()
                    result = RootManagedProcessWait.await(process, 5_000)
                    restored = Thread.currentThread().isInterrupted
                }
            } catch (caught: Throwable) { failure = caught; ready.countDown() }
            finally { Thread.interrupted() }
        }
        owner.start()
        assertTrue(ready.await(10, TimeUnit.SECONDS))
        owner.interrupt(); owner.join(10_000)
        assertFalse(owner.isAlive)
        failure?.let { throw it }
        val exit = assertNotNull(result)
        assertNotNull(exit.code); assertTrue(exit.cancelled)
        assertFalse(exit.forced); assertTrue(restored)
        if (!System.getProperty("os.name").startsWith("Windows"))
            assertTrue(Files.exists(directory.resolve("cleaned")), "TERM cleanup must complete before returning")
    }

    @Test fun deadlineDoesNotConvertForcedExitIntoCleanCompletion() = fixture { directory ->
        child(directory, 60_000).useChild { process ->
            assertEquals("ready", process.inputStream.bufferedReader().readLine())
            val result = RootManagedProcessWait.await(process, 100)
            assertTrue(result.forced); assertFalse(result.cancelled)
            assertNotNull(result.code)
        }
    }

    private fun child(directory: Path, sleep: Long): Process = ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", directory.toString(),
        "ManagedWaitChild", sleep.toString(), directory.resolve("cleaned").toString(),
    ).redirectErrorStream(true).start()

    private fun <T> Process.useChild(block: (Process) -> T): T = try { block(this) }
    finally {
        val interrupted = Thread.interrupted()
        try {
            if (isAlive) destroyForcibly()
            check(waitFor(5, TimeUnit.SECONDS))
            outputStream.close(); inputStream.close(); errorStream.close()
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun fixture(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("aurora-managed-wait-")
        try {
            val source = directory.resolve("ManagedWaitChild.java")
            Files.writeString(source, """
                public class ManagedWaitChild {
                    public static void main(String[] args) throws Exception {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { Thread.sleep(250); java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]), "done"); }
                            catch (Exception ignored) { }
                        }));
                        System.out.println("ready"); System.out.flush();
                        Thread.sleep(Long.parseLong(args[0])); System.exit(7);
                    }
                }
            """.trimIndent())
            check(ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString()) == 0)
            block(directory)
        } finally { removeSkillTree(directory) }
    }
}
