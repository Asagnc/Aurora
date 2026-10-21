package io.github.asagnc.aurora.core

import okhttp3.HttpUrl

internal data class SkillGithubLocation(val owner: String, val repo: String, val ref: String?, val selection: String)

/** A slash-bearing branch is explicit, avoiding network guesses that can select the wrong directory. */
internal fun skillGithubLocation(url: HttpUrl, source: SkillSource): SkillGithubLocation? {
    if (url.host != "github.com") {
        require(source.ref.isBlank()) { "A Git reference requires a GitHub repository URL" }
        return null
    }
    val parts = url.pathSegments.dropLastWhile { it.isEmpty() }
    require(parts.size >= 2) { "A GitHub repository URL is required" }
    val owner = parts[0]; val repo = parts[1].removeSuffix(".git")
    require(owner.matches(Regex("[A-Za-z0-9_.-]+")) && repo.matches(Regex("[A-Za-z0-9_.-]+")))
    if (parts.getOrNull(2) in setOf("archive", "releases")) {
        require(source.ref.isBlank()) { "Use the repository root when specifying a Git reference" }
        return null
    }
    require(parts.size == 2 || (parts.size >= 4 && parts[2] == "tree")) {
        "Use a GitHub repository or tree URL, not a blob/settings page"
    }
    require(parts.size <= 36)
    val explicit = source.ref.takeIf { it.isNotBlank() }?.also {
        require(it.length <= 1024 && it.none { char -> char.isISOControl() || char == '\\' } && it == it.trim())
    }
    if (parts.size == 2) return SkillGithubLocation(owner, repo, explicit, source.selection)
    val tail = parts.drop(3)
    val ref = explicit ?: tail.first()
    val refParts = ref.split('/')
    val directory = if (explicit == null || tail.first() == explicit) tail.drop(1) else {
        require(tail.take(refParts.size) == refParts) { "Explicit reference does not match tree URL" }
        tail.drop(refParts.size)
    }
    return SkillGithubLocation(owner, repo, ref, source.selection.ifBlank { directory.joinToString("/") })
}

/** Preserve existing IDs for the original source format; explicit references have independent namespaces. */
fun skillSourceId(source: SkillSource, relative: String): String = skillDigest(
    ("${source.input.substringBefore('?')}\n$relative" + source.ref.takeIf { it.isNotBlank() }?.let { "\nref=$it" }.orEmpty()).toByteArray())
