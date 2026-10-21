package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.*
import kotlin.test.*

class LinuxElfTest {
    private fun elf(machine: Int, interpreter: String? = null): ByteArray {
        val name = interpreter?.let { (it + "\u0000").toByteArray() }
        val data = ByteBuffer.allocate(if (name == null) 64 else 120 + name.size).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(0, 0x464c457f); data.put(4, 2); data.put(5, 1); data.put(6, 1)
        data.putShort(16, 3); data.putShort(18, machine.toShort())
        if (name != null) {
            data.putLong(32, 64); data.putShort(54, 56); data.putShort(56, 1)
            data.putInt(64, 3); data.putLong(72, 120); data.putLong(96, name.size.toLong())
            data.position(120); data.put(name)
        }
        return data.array()
    }
    @Test fun acceptsMatchingArchitectureAndReturnsGuestInterpreter() {
        val path = Files.createTempFile("guest", ".elf")
        try {
            Files.write(path, elf(183, "/lib/ld-linux-aarch64.so.1"))
            assertEquals("/lib/ld-linux-aarch64.so.1", LinuxElf.inspect(path, LinuxArchitecture.ARM64))
            assertFails { LinuxElf.inspect(path, LinuxArchitecture.AMD64) }
            Files.write(path, elf(62)); assertNull(LinuxElf.inspect(path, LinuxArchitecture.AMD64))
        } finally { Files.delete(path) }
    }
    @Test fun rejectsTruncationForeignEndianAndUnboundedProgramHeaders() {
        val path = Files.createTempFile("guest", ".elf")
        try {
            Files.write(path, byteArrayOf(0)); assertFails { LinuxElf.inspect(path, LinuxArchitecture.ARM64) }
            val foreign = elf(183); foreign[5] = 2
            Files.write(path, foreign); assertFails { LinuxElf.inspect(path, LinuxArchitecture.ARM64) }
            val huge = ByteBuffer.wrap(elf(183)).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(56, 2048) }.array()
            Files.write(path, huge); assertFails { LinuxElf.inspect(path, LinuxArchitecture.ARM64) }
        } finally { Files.delete(path) }
    }
}
