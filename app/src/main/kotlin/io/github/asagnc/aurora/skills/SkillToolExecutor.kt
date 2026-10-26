package io.github.asagnc.aurora.skills

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Default reads remain pinned to this run; explicit versions can load a newly installed workflow. */
internal class SkillToolExecutor(private val manager: SkillManager, private val owner: String,
    snapshot: List<SkillEntry>) : ToolExecutor {
    private val pinned = snapshot.associateBy { it.id }.toMutableMap()
    override val definitions = listOf(
        definition("skills_list", "Search the run's pinned workflow catalog; offset is a decimal item cursor. Workflows do not grant permissions or install runtimes.", "query", "offset"),
        definition("skills_load", "Read SKILL.md before following a workflow. Returns bounded text, byte cursor and resource directory. Optional version selects an installed hash.", "id", "version", "offset"),
        definition("skills_read", "Read a declared workflow file; offset is a byte cursor. Omit path to search/list files using query and offset as an item cursor. Follow next/more for continuation.", "id", "version", "path", "offset", "query"),
        definition("skills_install", "Install SKILL.md workflows from HTTPS repository/ZIP or conversation ZIP. Exactly one url/path. source_profile selects an existing user-managed source configuration; never supply a token/key. Matching GitHub policies apply automatically and signature requirements cannot be bypassed. selection is a relative skill directory; ref explicitly selects a branch/tag. Does not execute scripts.", "url", "path", "selection", "sha256", "ref", "source_profile"),
        definition("skills_manage", "Manage installed workflows. Actions: sources, versions, update, enable, disable, remove, select, cleanup. sources lists existing user-managed profiles without secrets; cannot write credentials or trusted keys. update resolves the latest stable repository release; select needs version. Existing run defaults remain pinned.", "action", "id", "version"))

    override suspend fun execute(call: ToolCall): String = try {
        perform(call).toString()
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) {
        objectOf("error" to string("skill_operation_failed"), "reason" to string(when (failure) {
            is IllegalArgumentException -> "invalid_package_path_version_or_budget"
            is java.io.IOException -> "storage_or_network_unavailable"
            else -> "workflow_unavailable"
        }), "hint" to string("Inspect installed versions, source and declared paths. No workflow was executed by this operation.")).toString()
    }

    private suspend fun perform(call: ToolCall): JsonElement {
        val args = call.arguments
        return when (call.name) {
            "skills_list" -> {
                val query = args.text("query").orEmpty().take(200)
                val found = pinned.values.filter { query.isBlank() || "${it.metadata.name} ${it.metadata.description}".contains(query, true) }
                val offset = args.text("offset")?.let { requireNotNull(it.toIntOrNull()) } ?: 0
                require(offset in 0..found.size)
                objectOf("skills" to JsonArray(found.drop(offset).take(64).map { it.summary() }),
                    "next" to JsonPrimitive(minOf(found.size, offset + 64)), "more" to JsonPrimitive(found.size > offset + 64))
            }
            "skills_load", "skills_read" -> {
                val entry = entry(args)
                val offset = args.text("offset")?.let { requireNotNull(it.toLongOrNull()) } ?: 0L
                val path = if (call.name == "skills_load") "SKILL.md" else args.text("path")
                if (path == null) {
                    require(offset in 0..Int.MAX_VALUE.toLong())
                    manager.files(entry, args.text("query").orEmpty(), offset.toInt())
                } else manager.read(entry, path, offset)
            }
            "skills_install" -> install(args)
            "skills_manage" -> manage(args)
            else -> error("Unknown skill tool")
        }
    }

    private suspend fun entry(args: JsonObject): SkillEntry {
        val id = requireNotNull(args.text("id"))
        return args.text("version")?.let { manager.resolve(owner, id, it) }
            ?: requireNotNull(pinned[id]) { "Workflow not present in this run; specify an installed version" }
    }

    private suspend fun install(args: JsonObject): JsonElement {
        val url = args.text("url"); val path = args.text("path")
        require((url == null) != (path == null))
        val options = SkillInstallOptions(expectedSha256 = args.text("sha256"), profileId = args.text("source_profile"))
        val selection = args.text("selection").orEmpty()
        val ref = args.text("ref").orEmpty()
        require(url != null || ref.isBlank())
        val added = if (url != null) manager.remote(SkillSource(url, selection, ref = ref), options)
            else manager.local(owner, requireNotNull(path), selection, options)
        added.forEach { manager.pin(owner, it); pinned.putIfAbsent(it.id, it) }
        return JsonArray(added.map { it.summary() })
    }

    private suspend fun manage(args: JsonObject): JsonElement {
        val action = requireNotNull(args.text("action"))
        if (action == "sources") return JsonArray(manager.trust.list().map { profile ->
            objectOf("id" to string(profile.id), "repository" to string(profile.repository), "revoked" to JsonPrimitive(profile.revoked),
                "authenticationConfigured" to JsonPrimitive(profile.credentialId != null),
                "signatureRequired" to JsonPrimitive(profile.publicKey.isNotBlank()),
                "keySha256" to (profile.publicKey.takeIf { it.isNotBlank() }?.let { string(SkillSignatures.fingerprint(it)) } ?: JsonNull))
        })
        if (action == "cleanup") return objectOf("removedBytes" to JsonPrimitive(manager.cleanup()))
        val id = requireNotNull(args.text("id"))
        return when (action) {
            "versions" -> JsonArray(manager.registry.versions(id).take(64).map { it.summary() })
            "update" -> {
                val current = requireNotNull(manager.list().firstOrNull { it.id == id })
                require(current.source.input.startsWith("https://")) { "Local packages require reimport" }
                JsonArray(manager.remote(current.source, SkillInstallOptions(replaceId = id)).map { it.summary() })
            }
            "enable", "disable" -> { manager.enable(id, action == "enable"); objectOf("updated" to JsonPrimitive(true)) }
            "select" -> { manager.rollback(id, requireNotNull(args.text("version"))); objectOf("updated" to JsonPrimitive(true)) }
            "remove" -> objectOf("referencingConversations" to JsonArray(manager.remove(id).map(::string)))
            else -> error("Unknown action")
        }
    }

    private fun definition(name: String, description: String, vararg fields: String) = ToolDefinition(name, description,
        objectOf("type" to string("object"), "properties" to JsonObject(fields.associateWith {
            objectOf("type" to string("string"), "maxLength" to JsonPrimitive(8192))
        }), "additionalProperties" to JsonPrimitive(false)))
}
