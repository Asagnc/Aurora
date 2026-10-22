package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootLinuxClient
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import kotlinx.serialization.encodeToString

/** A ticket blocks ordinary cleanup until Root stopped and its privileged files
 * were either published elsewhere or removed by Root. */
internal object LinuxPreparationState {
    private const val NAME = ".root-preparation.json"
    fun pending(stage: Path) = Files.exists(stage.resolve(NAME), LinkOption.NOFOLLOW_LINKS)
    fun settled(stage: Path) { Files.delete(stage.resolve(NAME)); LinuxDurability.directory(stage) }
    fun mark(stage: Path, ticket: LinuxPreparationTicket) {
        ticket.validate()
        val temporary = stage.resolve(".root-preparation.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(WireJson.encodeToString(ticket).toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        Files.move(temporary, stage.resolve(NAME), StandardCopyOption.ATOMIC_MOVE)
        LinuxDurability.directory(stage); LinuxDurability.directory(stage.parent)
    }
    suspend fun recover(staging: Path, client: RootLinuxClient?): Int {
        var retained = 0
        Files.newDirectoryStream(staging).use { entries -> entries.forEach { stage ->
            if (pending(stage)) {
                if (client == null) retained++ else client.discard(stage.fileName.toString())
            } else removeSkillTree(stage)
        } }
        return retained
    }
}
