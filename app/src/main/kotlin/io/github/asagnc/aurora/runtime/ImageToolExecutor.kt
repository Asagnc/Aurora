package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.ImageArtifactStore
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException

internal class ImageToolExecutor(private val store: ImageArtifactStore, private val owner: String) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("images_publish",
        "Show an image to the user. Use a conversation-relative file path for an image produced by tools, or an HTTPS URL. " +
            "Returns a persistent aurora-image link and Markdown to include in your reply. Does not generate images or send pixels to a model. " +
            "Supports Android-decodable raster images, up to 32 MiB and 64 megapixels.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "source" to objectOf("type" to string("string")), "description" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(300))),
            "required" to JsonArray(listOf(string("source"), string("description"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String = try {
        publish(call)
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) {
        objectOf("error" to string("image_publish_failed"), "reason" to string(when (failure) {
            is IllegalArgumentException -> "invalid_source_format_or_budget"
            is java.io.IOException -> "file_or_network_unavailable"
            else -> "artifact_unavailable"
        }), "hint" to string("Inspect the source path/URL and image format. Use a conversation-relative raster image or HTTPS URL, up to 32 MiB and 64 megapixels. Do not treat failure as a published image.")).toString()
    }

    private suspend fun publish(call: ToolCall): String {
        require(call.name == "images_publish")
        val source = requireNotNull(call.arguments.text("source"))
        val description = requireNotNull(call.arguments.text("description"))
        require(description.length <= 300 && source.length <= 8192)
        val image = if (source.startsWith("https://") || source.startsWith("aurora-image:")) store.resolve(owner, source)
            else store.publish(owner, source, description)
        return objectOf("image" to WireJson.encodeToJsonElement(ImageArtifact.serializer(), image),
            "markdown" to string(imageMarkdown(image.copy(description = description)))).toString()
    }
}
