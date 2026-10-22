package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Keeps one file schema across Root connection changes and preserves batch order. */
class LinuxFileToolRouter(private val ordinary: ToolExecutor,
    private val root: suspend (String, JsonObject) -> JsonObject) : ToolExecutor {
    override val definitions = ordinary.definitions.map { it.copy(description = it.description +
        " Conversation paths under .aurora-linux/ use the built-in Root channel. Edit batches must stay in one App or Linux domain. Root text batches use a 32KiB aggregate payload budget; read slices may be shorter with next offsets.") }
    override suspend fun execute(call: ToolCall): String {
        if (call.arguments.text("space") != "conversation") return ordinary.execute(call)
        return when (call.name) {
            "files_list" -> if (isLinux(call.arguments.text("path").orEmpty())) {
                responsePaths(root(call.name, JsonObject(call.arguments + ("path" to string(relative(call.arguments.text("path").orEmpty()))))), "entries").toString()
            } else ordinary.execute(call)
            "files_read_batch" -> read(call).toString()
            "files_edit_batch" -> {
                val edits = call.arguments.array("edits")
                val flags = edits.map { isLinux(requireNotNull(it.jsonObject.text("path"))) }
                require(flags.distinct().size == 1) { "Split App and Linux edits into separate batches; no cross-domain atomic commit" }
                if (flags.first()) root(call.name, rewrite(call.arguments, "edits", edits)).toString() else ordinary.execute(call)
            }
            else -> ordinary.execute(call)
        }
    }
    private suspend fun read(call: ToolCall): JsonObject {
        val requests = call.arguments.array("files")
        require(requests.size in 1..64)
        val output = arrayOfNulls<JsonElement>(requests.size)
        for (linux in listOf(false, true)) {
            val indices = requests.indices.filter { isLinux(requireNotNull(requests[it].jsonObject.text("path"))) == linux }
            if (indices.isEmpty()) continue
            val selected = JsonArray(indices.map { requests[it] })
            val result = if (linux) responsePaths(root(call.name, rewrite(call.arguments, "files", selected)), "files")
                else WireJson.parseToJsonElement(ordinary.execute(call.copy(arguments = JsonObject(call.arguments + ("files" to selected))))).jsonObject
            val values = result.array("files")
            require(values.size == indices.size) { "File batch response is incomplete" }
            indices.forEachIndexed { index, original -> output[original] = values[index] }
        }
        return objectOf("files" to JsonArray(output.map { requireNotNull(it) }))
    }
    private fun rewrite(arguments: JsonObject, field: String, values: JsonArray) = JsonObject(arguments + (field to JsonArray(values.map { value ->
        val item = value.jsonObject
        JsonObject(item + ("path" to string(relative(requireNotNull(item.text("path"))))))
    })))
    private fun responsePaths(value: JsonObject, field: String) = JsonObject(value + (field to JsonArray(value.array(field).map { item ->
        JsonObject(item.jsonObject + ("path" to string(".aurora-linux/" + requireNotNull(item.jsonObject.text("path")))))
    })))
    companion object {
        fun isLinux(path: String) = path == ".aurora-linux" || path.startsWith(".aurora-linux/")
        fun relative(path: String) = if (path == ".aurora-linux") "" else path.removePrefix(".aurora-linux/")
    }
}
