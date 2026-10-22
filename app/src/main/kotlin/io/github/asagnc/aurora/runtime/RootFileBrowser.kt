package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

/** Browser previews use the same scoped Root channel as Agent file tools. */
internal class RootFileBrowser(private val app: AuroraApplication, private val owner: String) {
    suspend fun list(path: String): List<Pair<String, Boolean>> = invoke("files_list",
        objectOf("path" to string(LinuxFileToolRouter.relative(path)))).array("entries").map {
        ".aurora-linux/" + requireNotNull(it.jsonObject.text("path")) to (it.jsonObject["directory"] == JsonPrimitive(true))
    }
    suspend fun read(path: String): FileSlice {
        val file = invoke("files_read_batch", objectOf("files" to JsonArray(listOf(objectOf("path" to string(LinuxFileToolRouter.relative(path)))))))
            .array("files").single().jsonObject
        val text = file.text("text")
        return FileSlice(path, text, text?.toByteArray(Charsets.UTF_8) ?: byteArrayOf(), file.number("size") ?: 0,
            file.number("next"), file.text("version"), file.text("error"))
    }
    private suspend fun invoke(operation: String, arguments: JsonObject): JsonObject = app.tasks.run(owner) {
        requireNotNull(app.root.linuxFileClient) { "请先连接 Root" }.invoke(owner, operation, arguments)
    }
}
