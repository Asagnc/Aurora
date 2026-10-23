package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.*

class StaticReverseRuntimeTest {
    @Test fun productionProfileIsHeadlessAndHonorsHeapAndProcessorBudgetsOnHostJvm() {
        val root = Files.createTempDirectory("aurora-jvm-profile-")
        var process: Process? = null
        try {
            val source = root.resolve("AuroraRuntimeProbe.java")
            Files.writeString(source, """
                public class AuroraRuntimeProbe {
                    public static void main(String[] args) {
                        if (!java.awt.GraphicsEnvironment.isHeadless()) throw new AssertionError("headless");
                        if (Runtime.getRuntime().availableProcessors() != 1) throw new AssertionError("processor budget");
                        if (Runtime.getRuntime().maxMemory() > 128L*1024*1024) throw new AssertionError("heap budget");
                        System.out.print("profile-qualified");
                    }
                }
            """.trimIndent())
            val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler())
            assertEquals(0, compiler.run(null, null, null, source.toString()))
            val java = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            val output = root.resolve("result.txt")
            process = ProcessBuilder(listOf(java.toString()) + staticReverseJava(128, 1).drop(1) +
                listOf("-cp", root.toString(), "AuroraRuntimeProbe"))
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Runtime profile fixture exceeded deadline")
            assertEquals(0, process.exitValue(), Files.readString(output))
            assertEquals("profile-qualified", Files.readString(output))
        } finally {
            process?.let { if (it.isAlive) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) } }
            root.toFile().deleteRecursively()
        }
    }
}
