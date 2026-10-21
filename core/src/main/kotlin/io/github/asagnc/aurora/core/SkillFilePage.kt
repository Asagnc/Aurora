package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** File discovery remains bounded even for packages with thousands of declared resources. */
fun skillFilePage(manifest: SkillManifest, query: String = "", offset: Int = 0): JsonObject {
    require(query.length <= 200)
    val matching = manifest.files.filter { it.path.contains(query, true) }
    require(offset in 0..matching.size)
    val next = minOf(matching.size, offset + 64)
    return objectOf("files" to JsonArray(matching.drop(offset).take(64).map { file ->
        objectOf("path" to string(file.path), "size" to JsonPrimitive(file.size), "executable" to JsonPrimitive(file.executable))
    }), "next" to JsonPrimitive(next), "more" to JsonPrimitive(next < matching.size), "total" to JsonPrimitive(matching.size))
}
