package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.events.Event
import kotlinx.serialization.Serializable

@Serializable data class SkillMetadata(val name: String, val description: String, val compatibility: String = "",
    val license: String = "", val metadata: Map<String, String> = emptyMap(), val allowedTools: String = "",
    val warnings: List<String> = emptyList())
data class SkillDocument(val metadata: SkillMetadata, val body: String)

/** Parse real YAML only inside the bounded frontmatter; leave the original Markdown untouched. */
object SkillDocumentParser {
    private val settings = LoadSettings.builder().setCodePointLimit(32768).setAllowDuplicateKeys(false)
        .setMaxAliasesForCollections(0).setAllowRecursiveKeys(false).build()

    fun parse(bytes: ByteArray, directoryName: String): SkillDocument {
        require(bytes.size <= 262144) { "SKILL.md exceeds 256 KiB" }
        val raw = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            .removePrefix("\uFEFF").replace("\r\n", "\n")
        require(raw.startsWith("---\n")) { "SKILL.md requires YAML frontmatter" }
        val end = raw.indexOf("\n---", 4)
        require(end >= 0 && end <= 32768 && (end + 4 == raw.length || raw[end + 4] == '\n')) { "Missing or oversized frontmatter" }
        val header = raw.substring(4, end)
        preflight(header)
        val map = Load(settings).loadFromString(header) as? Map<*, *> ?: error("Frontmatter must be a mapping")
        require(map.keys.all { it is String })
        fun field(key: String): String = map[key]?.let { require(it is String) { "$key must be text" }; it }.orEmpty()
        val name = field("name")
        val description = field("description")
        require(name.isNotBlank() && name.length <= 1024 && description.isNotBlank() && description.length <= 1024)
        val warnings = buildList {
            if (!name.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) || name.length > 64) add("Non-standard skill name; identity uses a separate source ID")
            if (name != directoryName) add("Name differs from directory")
        }
        val extra = map["metadata"]?.let { value ->
            require(value is Map<*, *> && value.size <= 64)
            value.entries.associate { (key, item) -> require(key is String && item is String); key to item }
        }.orEmpty()
        val compatibility = field("compatibility").also { require(it.length <= 500) }
        return SkillDocument(SkillMetadata(name, description, compatibility, field("license"), extra, field("allowed-tools"), warnings),
            raw.substring(end + 4).trimStart('\n'))
    }

    private fun preflight(header: String) {
        var depth = 0
        var count = 0
        for (event in Parse(settings).parseString(header)) {
            require(++count <= 4096) { "Too many YAML events" }
            when (event.eventId) {
                Event.ID.MappingStart, Event.ID.SequenceStart -> { depth++; require(depth <= 16) { "YAML nesting limit exceeded" } }
                Event.ID.MappingEnd, Event.ID.SequenceEnd -> depth--
                Event.ID.Alias -> error("YAML aliases are unsupported in skill metadata")
                else -> Unit
            }
        }
    }
}
