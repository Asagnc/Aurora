package io.github.asagnc.aurora.core

import okhttp3.HttpUrl

/** The exact API repository is the only destination that can receive the secret. */
class SkillGithubCredential(val repository: String, private val token: String) {
    init {
        require(repository.matches(Regex("[a-z0-9_.-]+/[a-z0-9_.-]+")) && repository.length <= 200)
        require(token.isNotBlank() && token.length <= 8192 && token.all { it.code in 33..126 })
    }
    fun authorization(url: HttpUrl): String? = if (api(url)) "Bearer $token" else null
    fun accepts(url: HttpUrl): Boolean = api(url) || (url.isHttps && url.port == 443 && url.host == "codeload.github.com" &&
        url.encodedPath.lowercase(java.util.Locale.ROOT).startsWith("/$repository/") &&
        url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
    private fun api(url: HttpUrl): Boolean = url.isHttps && url.port == 443 && url.host == "api.github.com" &&
        url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        (url.encodedPath.lowercase(java.util.Locale.ROOT) == "/repos/$repository" ||
            url.encodedPath.lowercase(java.util.Locale.ROOT).startsWith("/repos/$repository/"))
    override fun toString() = "SkillGithubCredential(repository=$repository, token=<redacted>)"
}
