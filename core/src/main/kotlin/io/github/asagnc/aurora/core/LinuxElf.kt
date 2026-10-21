package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.*

object LinuxElf {
    fun validateRootfs(root: Path, architecture: LinuxArchitecture) {
        val shell = resolveRootfsPath(root, "/bin/sh")
        val interpreter = inspect(shell, architecture)
        interpreter?.let { inspect(resolveRootfsPath(root, it), architecture) }
    }
    fun inspect(path: Path, architecture: LinuxArchitecture): String? {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Missing guest executable" }
        return FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val header = read(channel, 0, 64).order(ByteOrder.LITTLE_ENDIAN)
            require(header.getInt(0) == 0x464c457f && header[4] == 2.toByte() && header[5] == 1.toByte() && header[6] == 1.toByte()) { "Expected 64-bit little-endian ELF" }
            require(header.getShort(16).toInt() in setOf(2, 3) && header.getShort(18).toInt() == architecture.elfMachine) { "Guest architecture mismatch" }
            val offset = header.getLong(32); val width = header.getShort(54).toInt() and 65535
            val count = header.getShort(56).toInt() and 65535
            require(count <= 1024 && (count == 0 || width == 56) && offset >= 0 && offset <= channel.size() - width.toLong() * count)
            var interpreter: String? = null
            repeat(count) { index ->
                val program = read(channel, offset + index.toLong() * width, width).order(ByteOrder.LITTLE_ENDIAN)
                if (program.getInt(0) == 3) {
                    require(interpreter == null)
                    val start = program.getLong(8); val size = program.getLong(32)
                    require(size in 2..4096)
                    val bytes = read(channel, start, size.toInt()).array()
                    require(bytes.last() == 0.toByte() && bytes.dropLast(1).none { it == 0.toByte() })
                    interpreter = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, bytes.size - 1)).toString()
                    require(interpreter!!.startsWith('/')); rootfsName(interpreter!!, absolute = true)
                }
            }
            interpreter
        }
    }
    private fun read(channel: FileChannel, offset: Long, size: Int): ByteBuffer {
        require(offset >= 0 && offset <= channel.size() - size)
        val result = ByteBuffer.allocate(size)
        while (result.hasRemaining()) require(channel.read(result, offset + result.position()) > 0)
        return result.apply { flip() }
    }
}
