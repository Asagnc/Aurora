package io.github.asagnc.aurora.core

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.*

class SkillNetworkTest {
    private fun network(reply: (Request) -> Pair<Int, String>) = SkillNetwork(OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = reply(chain.request())
        Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("fixture")
            .body(body.toResponseBody()).build()
    }.build())

    @Test fun choosesLatestStableReleaseAndPinsItsCommit() = runBlocking {
        val requests = mutableListOf<String>()
        val sha = "a".repeat(40)
        val client = network { request ->
            assertNull(request.header("Authorization"))
            requests += request.url.encodedPath
            200 to if (request.url.encodedPath.endsWith("latest")) "{\"tag_name\":\"v2.0\"}" else "{\"sha\":\"$sha\"}"
        }
        val resolved = client.resolve(SkillSource("https://github.com/example/workflows"))
        assertEquals(2, requests.size)
        assertEquals("https://codeload.github.com/example/workflows/zip/$sha", resolved.archiveUrl)
        assertEquals(sha, resolved.source.revision)
        assertTrue(resolved.source.label.endsWith("v2.0"))
    }
    @Test fun repositoryWithoutReleaseUsesDefaultBranchButOtherErrorsDoNotFallback() = runBlocking {
        val sha = "b".repeat(40)
        val client = network { request -> when {
            request.url.encodedPath.endsWith("latest") -> 404 to "{}"
            request.url.encodedPath.endsWith("/workflows") -> 200 to "{\"default_branch\":\"main\"}"
            else -> 200 to "{\"sha\":\"$sha\"}"
        } }
        val fallback = client.resolve(SkillSource("https://github.com/example/workflows"))
        assertEquals(sha, fallback.source.revision)
        assertTrue(fallback.source.label.contains("分支快照"))
        assertFailsWith<SkillHttpStatus> { network { 403 to "{}" }.resolve(SkillSource("https://github.com/example/workflows")) }
    }
    @Test fun rejectsCredentialsPlaintextAndFragmentsBeforeNetwork() = runBlocking {
        val client = network { error("Network must not be reached") }
        listOf("http://example.com/skill.zip", "https://user:secret@example.com/skill.zip", "https://example.com/skill.zip#fragment").forEach {
            assertFailsWith<IllegalArgumentException> { client.resolve(SkillSource(it)) }
        }
    }
    @Test fun explicitBranchUsesOneCommitRequestAndSurvivesReResolution() = runBlocking {
        val requests = mutableListOf<String>()
        val sha = "c".repeat(40)
        val client = network { request ->
            requests += request.url.encodedPath
            assertEquals("feature/new", request.url.pathSegments.last())
            200 to "{\"sha\":\"$sha\"}"
        }
        val source = SkillSource("https://github.com/example/skills/tree/feature/new/reader", ref = "feature/new")
        val resolved = client.resolve(source)
        assertEquals(1, requests.size); assertTrue(requests.single().endsWith("feature%2Fnew"))
        assertEquals("reader", resolved.source.selection); assertEquals("feature/new", resolved.source.ref)
        assertEquals(resolved.source, client.resolve(resolved.source).source)
    }
}
