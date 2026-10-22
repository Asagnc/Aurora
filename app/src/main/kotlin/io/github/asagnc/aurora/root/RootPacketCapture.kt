package io.github.asagnc.aurora.root

import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import io.github.asagnc.aurora.runtime.NativeCommandRunner
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal class RootPacketCapture(private val context: Context) {
    private val runner = NativeCommandRunner(context, context.applicationInfo.uid, artifactWorkingDirectory = true)
    suspend fun execute(root: Path, id: String, request: PacketCaptureRequest): String {
        val binary = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_packet_worker.so")
        val command = runner.execute(root, CommandRequest(request.argv(binary.toString()),
            timeoutMs = request.timeoutMs, outputLimit = 8192), id)
        val artifacts = mapOf("artifactDirectory" to string("runs/$id"))
        if (command["error"] != null) return JsonObject(command + artifacts).toString()
        val completion = try { packetCaptureCompletion(command, request) }
        catch (failure: Exception) {
            return JsonObject(command + artifacts + ("error" to string("capture_result_invalid"))).toString()
        }
        return try {
            val capture = RootPacketPublication.publish(root, id, completion, request, context.applicationInfo.uid)
            JsonObject(command + artifacts + ("capture" to capture)).toString()
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            JsonObject(command + artifacts + ("error" to string("capture_publish_failed"))).toString()
        }
    }
}
