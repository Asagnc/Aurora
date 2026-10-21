package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class RootfsArchiveTest {
    private fun fixture(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("aurora-rootfs")
        try { block(root) } finally { removeSkillTree(root) }
    }
    private fun archive(root: Path, entries: List<TarArchiveEntry>): Path {
        val path = root.resolve("image.tar.gz")
        TarArchiveOutputStream(GZIPOutputStream(Files.newOutputStream(path))).use { tar ->
            entries.forEach { entry -> tar.putArchiveEntry(entry); if (entry.isFile) tar.write(ByteArray(entry.size.toInt()) { 42 }); tar.closeArchiveEntry() }
        }
        return path
    }
    private fun file(name: String, size: Long = 5) = TarArchiveEntry(name).apply { this.size = size; mode = 0x1ed }
    private fun link(name: String, target: String, symbolic: Boolean = true) =
        TarArchiveEntry(name, if (symbolic) TarConstants.LF_SYMLINK else TarConstants.LF_LINK).apply { linkName = target }

    @Test fun validatesLinuxLayoutAndNormalizesLeadingDotWithoutFollowingLinks() = fixture { root ->
        val source = archive(root, listOf(file("./usr/bin/tool"), link("bin", "usr/bin"), link("usr/bin/alias", "./usr/bin/tool", false)))
        val plan = RootfsArchive().scan(source)
        assertEquals(listOf("usr/bin/tool", "bin", "usr/bin/alias"), plan.map { it.path })
        assertEquals("symlink", plan[1].kind)
    }
    @Test fun rejectsTraversalLinkChildrenDuplicatesAndHardlinkToSymlink() = fixture { root ->
        val bad = listOf(listOf(file("../escape")), listOf(link("dir", "/tmp"), file("dir/escape")),
            listOf(file("same"), file("same")), listOf(link("sym", "/usr/bin/tool"), link("hard", "sym", false)),
            listOf(link("bad", "../../outside")))
        bad.forEach { entries -> assertFails { RootfsArchive().scan(archive(root, entries)) } }
        assertFalse(Files.exists(root.parent.resolve("escape")))
    }
    @Test fun rejectsSpecialFilesAndExpandedBudget() = fixture { root ->
        assertFails { RootfsArchive().scan(archive(root, listOf(TarArchiveEntry("device", TarConstants.LF_CHR)))) }
        assertFails { RootfsArchive(RootfsBudget(expanded = 4)).scan(archive(root, listOf(file("large", 10)))) }
    }
    @Test fun preservesLinuxNumericOwnersAndStripsPrivilegeBits() = fixture { root ->
        val source = archive(root, listOf(file("owned").apply {
            setUserId(1001L); setGroupId(1002L); mode = 0xfff
        }))
        val entry = RootfsArchive().scan(source).single()
        assertEquals(1001L, entry.uid); assertEquals(1002L, entry.gid)
        assertEquals(0x3ff, entry.mode)
        val encoded = WireJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(RootfsEntry.serializer()), listOf(entry))
        assertEquals(listOf(entry), WireJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(RootfsEntry.serializer()), encoded))
    }
    @Test fun rejectsNegativeLinuxOwners() = fixture { root ->
        val source = archive(root, listOf(file("owned")))
        val unpacker = RootfsArchive()
        val forged = unpacker.scan(source).map { it.copy(uid = -1) }
        assertFails { unpacker.extract(source, root.resolve("invalid"), forged) }
        assertFalse(Files.exists(root.resolve("invalid")))
    }
    @Test fun rejectsChangedMetadataBetweenScanAndExtraction() = fixture { root ->
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
        val source = archive(root, listOf(file("owned")))
        val unpacker = RootfsArchive(); val plan = unpacker.scan(source)
        archive(root, listOf(file("owned").apply { setUserId(123L) }))
        val target = root.resolve("changed")
        assertFails { unpacker.extract(source, target, plan) }
        assertFalse(Files.exists(target))
    }
    @Test fun checksGzipTrailerAndTarEndMarkers() = fixture { root ->
        val source = archive(root, listOf(file("value")))
        val bytes = Files.readAllBytes(source); bytes[bytes.size - 8] = (bytes[bytes.size - 8].toInt() xor 0xff).toByte()
        Files.write(source, bytes)
        assertFails { RootfsArchive().scan(source) }
    }
    @Test fun preventsForgedPlanPathsBeforeCreatingAnyTarget() = fixture { root ->
        val source = archive(root, listOf(file("valid")))
        assertFails { RootfsArchive().extract(source, root.resolve("target"), listOf(RootfsEntry("../escape", "symlink", 0, 0, "/tmp"))) }
        assertFalse(Files.exists(root.resolve("target")))
    }
    @Test fun extractsPosixLinksModesAndGuestAbsoluteSymlinks() = fixture { root ->
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"), "POSIX extraction requires a Linux/Android filesystem")
        val source = archive(root, listOf(file("usr/bin/tool"), link("bin", "/usr/bin"), link("usr/bin/alias", "usr/bin/tool", false)))
        val target = root.resolve("rootfs"); val archive = RootfsArchive()
        archive.extract(source, target, archive.scan(source))
        assertEquals(target.resolve("usr/bin/tool"), resolveRootfsPath(target, "/bin/tool"))
        assertTrue(Files.isSameFile(target.resolve("usr/bin/tool"), target.resolve("usr/bin/alias")))
        assertEquals(5L, Files.size(resolveRootfsPath(target, "/bin/tool")))
        assertTrue(Files.isExecutable(target.resolve("usr/bin/tool")))
    }
    @Test fun rejectsOversizedPaxHeaderBeforeReadingItsPayload() = fixture { root ->
        val header = ByteArray(512)
        TarArchiveEntry("pax", TarConstants.LF_PAX_EXTENDED_HEADER_LC).apply { size = 1024 * 1024 }.writeEntryHeader(header)
        val source = root.resolve("pax.gz")
        GZIPOutputStream(Files.newOutputStream(source)).use { it.write(header) }
        assertFailsWith<IllegalArgumentException> { RootfsArchive().scan(source) }
    }
    @Test fun pinsSupportedArchitecturesAndRejectsEmulationFallback() {
        assertEquals(LinuxArchitecture.ARM64, LinuxArchitecture.fromAbi("arm64-v8a"))
        assertFails { LinuxArchitecture.fromAbi("armeabi-v7a") }
        assertEquals(64, UbuntuBaseImages.current(LinuxArchitecture.AMD64).sha256.length)
    }
    @Test fun scansPinnedOfficialArm64ImageWhenSupplied() {
        val sample = System.getenv("AURORA_ROOTFS_SAMPLE")
        assumeTrue(!sample.isNullOrBlank(), "Official archive supplied by explicit validation workflow")
        val path = Path.of(requireNotNull(sample))
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input -> val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        assertEquals(UbuntuBaseImages.current(LinuxArchitecture.ARM64).sha256, hash.digest().joinToString("") { "%02x".format(it) })
        val plan = RootfsArchive().scan(path)
        assertEquals(113253544L, plan.sumOf { it.bytes })
        assertTrue(plan.any { it.path == "usr/bin/dash" })
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) fixture { root ->
            val target = root.resolve("official")
            RootfsArchive().extract(path, target, plan)
            LinuxElf.validateRootfs(target, LinuxArchitecture.ARM64)
        }
    }
}
