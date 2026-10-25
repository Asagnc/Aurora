package io.github.asagnc.aurora.root

import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import io.github.asagnc.aurora.runtime.NativeCommandRunner
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException

internal class RootPacketInjection(context: Context) {
    private val binary = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_packet_inject.so")
    private val runner = NativeCommandRunner(context, context.applicationInfo.uid, artifactWorkingDirectory = true)
    suspend fun execute(root: Path, id: String, request: PacketInjectionRequest): String {
        val command = runner.execute(root, CommandRequest(request.argv(binary.toString()),
            timeoutMs = request.timeoutMs, environment = mapOf("LC_ALL" to "C")), id)
        val artifacts = mapOf("artifactDirectory" to string("runs/$id"))
        return try {
            val result = packetInjectionCompletion(command, request)
            JsonObject(command + artifacts + ("injection" to result)).toString()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            JsonObject(command + artifacts + ("error" to string("packet_injection_outcome_uncertain"))).toString()
        }
    }
}
