package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

class FileToolExecutor(private val spaces: Map<String, OwnedFiles>) : ToolExecutor {
    override val definitions = listOf(
        ToolDefinition("files_list", "List up to 200 entries in an owned space. Root directory is the empty path.", schema(mapOf("path" to textSchema()), listOf("space"))),
        ToolDefinition("files_read_batch", "Read ordered UTF-8 slices, at most 64 KiB each. version is SHA-256 for editable files. Non-text slices return metadata only.",
            schema(mapOf("files" to arraySchema(objectSchema(mapOf("path" to textSchema(), "offset" to integerSchema(), "limit" to integerSchema()), listOf("path")))), listOf("space", "files"))),
        ToolDefinition("files_edit_batch", "Replace file contents with preflight version checks and recoverable group rollback. expectedVersion must match SHA-256; null creates a new file. Shared resources cannot be edited.",
            schema(mapOf("edits" to arraySchema(objectSchema(mapOf("path" to textSchema(), "expectedVersion" to objectOf("type" to JsonArray(listOf(string("string"), string("null")))), "text" to textSchema()), listOf("path", "expectedVersion", "text")))), listOf("space", "edits"))),
    )

    override suspend fun execute(call: ToolCall): String {
        val arguments = call.arguments
        val files = requireNotNull(spaces[arguments.text("space")]) { "Unknown file space" }
        return when (call.name) {
            "files_list" -> objectOf("entries" to JsonArray(files.list(arguments.text("path").orEmpty()).map { (path, directory) ->
                objectOf("path" to string(path), "directory" to JsonPrimitive(directory))
            })).toString()
            "files_read_batch" -> {
                val requests = arguments.array("files").map { value -> value.jsonObject.let {
                    FileRead(requireNotNull(it.text("path")), it.number("offset") ?: 0, Math.toIntExact(it.number("limit") ?: 65536))
                } }
                objectOf("files" to JsonArray(files.readBatch(requests).map { slice -> objectOf(
                    "path" to string(slice.path), "text" to (slice.text?.let(::string) ?: JsonNull),
                    "size" to JsonPrimitive(slice.size), "next" to (slice.next?.let(::JsonPrimitive) ?: JsonNull),
                    "version" to (slice.version?.let(::string) ?: JsonNull), "bytesRead" to JsonPrimitive(slice.bytes.size),
                    "encoding" to string(if (slice.text != null) "UTF-8" else "binary-or-partial-UTF8"),
                    "error" to (slice.error?.let(::string) ?: JsonNull)
                ) })).toString()
            }
            "files_edit_batch" -> {
                val edits = arguments.array("edits").map { value -> value.jsonObject.let {
                    require(it.containsKey("expectedVersion"))
                    FileEdit(requireNotNull(it.text("path")), it.text("expectedVersion"), requireNotNull(it.text("text")))
                } }
                files.editBatch(edits)
                objectOf("status" to string("committed"), "files" to JsonPrimitive(edits.size)).toString()
            }
            else -> error("Unknown file tool")
        }
    }

    private fun schema(properties: Map<String, JsonElement>, required: List<String>) = objectSchema(
        linkedMapOf<String, JsonElement>("space" to objectOf("type" to string("string"), "enum" to JsonArray(spaces.keys.sorted().map(::string)))) + properties, required)
    private fun objectSchema(properties: Map<String, JsonElement>, required: List<String>) = objectOf(
        "type" to string("object"), "properties" to JsonObject(properties), "required" to JsonArray(required.map(::string)), "additionalProperties" to JsonPrimitive(false))
    private fun arraySchema(items: JsonElement) = objectOf("type" to string("array"), "items" to items, "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(64))
    private fun textSchema() = objectOf("type" to string("string"))
    private fun integerSchema() = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(0))
}
