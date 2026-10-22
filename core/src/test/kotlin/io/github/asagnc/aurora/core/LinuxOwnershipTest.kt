package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue

class LinuxOwnershipTest {
    @Test fun backendIdsAndLegacyManifestsKeepGenerationsSeparate() {
        val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
        val ordinary = LinuxRootfsManifest(image, 1, 5)
        val root = ordinary.copy(backend = LinuxBackend.CHROOT, ownershipVersion = 1)
        assertEquals(image.sha256, ordinary.generationId())
        assertNotEquals(ordinary.generationId(), root.generationId())
        assertEquals(root.generationId(), linuxGenerationId(image.sha256, LinuxBackend.CHROOT))
        assertEquals(ordinary, WireJson.decodeFromString<LinuxRootfsManifest>(
            """{"image":${WireJson.encodeToString(LinuxImage.serializer(), image)},"entries":1,"expandedBytes":5}"""))
        assertFails { root.copy(ownershipVersion = 0).generationId() }
        assertFails { ordinary.copy(expandedBytes = -1).generationId() }
    }
    @Test fun ownershipTicketsRequireTheNewProtocolAndExactPurpose() {
        val identity = LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val ticket = LinuxPreparationTicket(UUID.randomUUID().toString(), identity, "a".repeat(64), 10000,
            protocol = 2, purpose = "root_ownership")
        ticket.validate()
        ticket.copy(purpose = "root_removal").validate()
        assertEquals(ticket, WireJson.decodeFromString<LinuxPreparationTicket>(
            WireJson.encodeToString(LinuxPreparationTicket.serializer(), ticket)))
        assertFails { ticket.copy(protocol = 1).validate() }
        assertFails { ticket.copy(purpose = "remove_arbitrary_path").validate() }
    }
    @Test fun ownershipRestoresImplicitDirectoriesAndRootAfterFilesWithoutOverridingHardlinks() = fixture { root ->
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
        Files.createDirectories(root.resolve("sub")); Files.write(root.resolve("sub/data"), byteArrayOf(42))
        Files.createLink(root.resolve("alias"), root.resolve("sub/data"))
        Files.createSymbolicLink(root.resolve("guest"), Path.of("/outside-guest-only"))
        val applied = mutableListOf<RootfsEntry>()
        val plan = listOf(RootfsEntry("sub/data", "file", 1, 0x180, uid = 42, gid = 8),
            RootfsEntry("alias", "hardlink", 0, 0x1ff, "sub/data", uid = 99),
            RootfsEntry("guest", "symlink", 0, 0x1ff, "/outside-guest-only", uid = 42))
        RootfsOwnership.restore(root, plan, apply = { _, entry -> applied += entry })
        assertEquals(listOf("sub/data", "guest", "sub", ""), applied.map { it.path })
        assertEquals(42L, applied.first().uid); assertEquals(8L, applied.first().gid)
        assertEquals(0L, applied.last().uid); assertEquals(0x1ed, applied.last().mode)
        assertEquals(Path.of("/outside-guest-only"), Files.readSymbolicLink(root.resolve("guest")))
    }
    @Test fun badSizeFailsFullPreflightBeforeAnyOwnershipMutation() = fixture { root ->
        Files.write(root.resolve("good"), byteArrayOf(42)); Files.write(root.resolve("bad"), byteArrayOf(42))
        var applied = 0
        assertFails { RootfsOwnership.restore(root, listOf(RootfsEntry("good", "file", 1, 0x180),
            RootfsEntry("bad", "file", 2, 0x180)), apply = { _, _ -> applied++ }) }
        assertEquals(0, applied)
    }
    @Test fun physicalAncestorLinkFailsBeforeAnyMutation() = fixture { root ->
        assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
        Files.createDirectories(root.resolve("real")); Files.write(root.resolve("real/data"), byteArrayOf(42))
        Files.createSymbolicLink(root.resolve("sub"), Path.of("real"))
        var applied = 0
        assertFails { RootfsOwnership.restore(root, listOf(RootfsEntry("sub/data", "file", 1, 0x180)),
            apply = { _, _ -> applied++ }) }
        assertEquals(0, applied)
    }
    @Test fun applyFailureStopsWithoutPretendingToRollBackAlreadyChangedFiles() = fixture { root ->
        Files.write(root.resolve("first"), byteArrayOf(42)); Files.write(root.resolve("second"), byteArrayOf(42))
        val changed = mutableListOf<String>()
        assertFails { RootfsOwnership.restore(root, listOf(RootfsEntry("first", "file", 1, 0x180),
            RootfsEntry("second", "file", 1, 0x180)), apply = { _, entry ->
                changed += entry.path; if (entry.path == "second") error("chown rejected")
            }) }
        assertEquals(listOf("first", "second"), changed)
    }
    @Test fun cancellationBeforePreflightDoesNotApplyOwnership() = fixture { root ->
        var applied = 0
        Thread.currentThread().interrupt()
        try { assertFailsWith<InterruptedException> { RootfsOwnership.restore(root, emptyList(), apply = { _, _ -> applied++ }) } }
        finally { Thread.interrupted() }
        assertEquals(0, applied)
    }
    private fun fixture(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("root-ownership-")
        try { block(root) } finally { removeSkillTree(root) }
    }
}
