package io.github.asagnc.aurora.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.*

class SkillGithubLocationTest {
    private fun locate(url: String, ref: String = "", selection: String = "") =
        skillGithubLocation(url.toHttpUrl(), SkillSource(url, selection, ref = ref))

    @Test fun explicitSlashBranchSeparatesBranchAndDirectoryWithoutGuessing() {
        val result = requireNotNull(locate("https://github.com/example/skills/tree/feature/new/skills/reader/", "feature/new"))
        assertEquals("feature/new", result.ref); assertEquals("skills/reader", result.selection)
        assertEquals("feature/new", locate("https://github.com/example/skills", "feature/new")?.ref)
    }
    @Test fun encodedSlashBranchAndSelectionOverrideArePreserved() {
        val url = "https://github.com/example/skills/tree/feature%2Fnew/original"
        assertEquals("feature/new", locate(url)?.ref)
        assertEquals("chosen", locate(url, "feature/new", "chosen")?.selection)
    }
    @Test fun archiveNamedRepositoryAndDirectoriesAreNotMistakenForDownloads() {
        assertEquals("archive", locate("https://github.com/example/archive")?.repo)
        assertEquals("releases", locate("https://github.com/example/releases")?.repo)
        assertEquals("archive/reader", locate("https://github.com/example/skills/tree/main/archive/reader")?.selection)
        assertNull(locate("https://github.com/example/skills/archive/refs/heads/main.zip"))
        assertNull(locate("https://github.com/example/skills/releases/download/v1/skills.zip"))
    }
    @Test fun rejectsMismatchedExplicitRefAndUnsupportedPages() {
        assertFails { locate("https://github.com/example/skills/tree/main/reader", "different/branch") }
        assertFails { locate("https://github.com/example/skills/blob/main/SKILL.md") }
        assertFails { locate("https://github.com/example/skills/tree") }
        assertFails { locate("https://example.com/skills.zip", "main") }
    }
    @Test fun legacySourceIdsRemainStableAndExplicitRefsHaveSeparateNamespaces() {
        val original = SkillSource("https://github.com/example/skills")
        assertEquals(skillDigest("${original.input}\nreader".toByteArray()), skillSourceId(original, "reader"))
        assertNotEquals(skillSourceId(original.copy(ref = "main"), "reader"), skillSourceId(original.copy(ref = "dev"), "reader"))
        assertEquals(original, WireJson.decodeFromString<SkillSource>("{\"input\":\"${original.input}\"}"))
    }
}
