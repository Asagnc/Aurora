package io.github.asagnc.aurora.core

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.*

class SkillPrivateNetworkTest {
    private val credential = SkillGithubCredential("example/skills", "owned-fixture-token")
    private fun network(reply: (Request) -> Response) = SkillNetwork(OkHttpClient.Builder().followRedirects(false)
        .followSslRedirects(false).addInterceptor { reply(it.request()) }.build())
    private fun response(request: Request, body: String, code: Int = 200, redirect: String? = null) = Response.Builder()
        .request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("owned fixture").body(body.toResponseBody())
        .apply { redirect?.let { header("Location", it) } }.build()
    @Test fun authenticatedResolutionAndTemporaryDownloadNeverPersistOrForwardTheSecret() = runBlocking {
        val requests = mutableListOf<Request>()
        val sha = "a".repeat(40)
        val client = network { request ->
            requests += request
            when {
                request.url.encodedPath.contains("/commits/") -> response(request, "{\"sha\":\"$sha\"}")
                request.url.host == "api.github.com" -> response(request, "", 302, "https://codeload.github.com/example/skills/legacy.zip/$sha?token=temporary-owned")
                else -> response(request, "owned bytes")
            }
        }
        val resolved = client.resolve(SkillSource("https://github.com/example/skills", ref = "main"), credential)
        assertTrue(resolved.archiveUrl.startsWith("https://api.github.com/repos/example/skills/zipball/"))
        val root = Files.createTempDirectory("aurora-private-skill")
        try { client.downloadPrivate(resolved.archiveUrl, root.resolve("download"), credential) } finally { removeSkillTree(root) }
        assertEquals(3, requests.size)
        assertEquals("Bearer owned-fixture-token", requests[0].header("Authorization"))
        assertEquals("Bearer owned-fixture-token", requests[1].header("Authorization"))
        assertNull(requests[2].header("Authorization")); assertNull(requests[2].header("Cookie"))
        assertFalse(resolved.source.toString().contains("temporary-owned"))
        assertFalse(credential.toString().contains("owned-fixture-token"))
    }
    @Test fun crossHostCrossRepositoryAndNonstandardPortRedirectsAreStoppedBeforeNextRequest() = runBlocking {
        val root = Files.createTempDirectory("aurora-private-redirect")
        try {
            for (target in listOf("https://example.com/steal", "https://api.github.com/repos/other/repo/zipball/a",
                "https://codeload.github.com/example/skills-other/file", "https://api.github.com:8443/repos/example/skills/file")) {
                var count = 0
                val client = network { request -> count++; response(request, "", 302, target) }
                assertFails { client.downloadPrivate("https://api.github.com/repos/example/skills/zipball/a", root.resolve("never"), credential) }
                assertEquals(1, count); assertFalse(Files.exists(root.resolve("never")))
            }
        } finally { removeSkillTree(root) }
    }
    @Test fun signatureBudgetAuthenticationAndPrivateStatusAreExplicit() = runBlocking {
        val client = network { request ->
            assertEquals("application/vnd.github.raw+json", request.header("Accept"))
            assertEquals("Bearer owned-fixture-token", request.header("Authorization"))
            response(request, "x".repeat(1025))
        }
        assertFails { client.signature("https://api.github.com/repos/example/skills/contents/archive.sig", credential) }
        val unauthorized = network { request -> response(request, "do not expose response body", 403) }
        val failure = assertFailsWith<SkillHttpStatus> { unauthorized.resolve(SkillSource("https://github.com/example/skills", ref = "main"), credential) }
        assertEquals(403, failure.code); assertFalse(failure.toString().contains("response body"))
        assertFails { unauthorized.resolve(SkillSource("https://github.com/other/skills", ref = "main"), credential) }
    }
}
