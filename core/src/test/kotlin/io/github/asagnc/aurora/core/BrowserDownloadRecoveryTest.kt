package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.UUID
import kotlin.test.*

class BrowserDownloadRecoveryTest {
    @Test fun recoveryRemovesOnlyRecordedPartialAndRetainsPublishedAndSiblingData() = temporary { root ->
        val directory="runs/${UUID.randomUUID()}"
        val artifact=root.resolve(directory);Files.createDirectories(artifact)
        Files.writeString(artifact.resolve("download.bin.partial"),"partial")
        Files.writeString(artifact.resolve("download.bin"),"published")
        Files.writeString(artifact.resolve("keep"),"sibling")
        val other=root.resolve("runs/${UUID.randomUUID()}/download.bin.partial")
        Files.createDirectories(other.parent);Files.writeString(other,"unrecorded")
        assertEquals("published_retained",recoverBrowserDownload(root,directory))
        assertFalse(Files.exists(artifact.resolve("download.bin.partial")))
        assertEquals("published",Files.readString(artifact.resolve("download.bin")))
        assertEquals("sibling",Files.readString(artifact.resolve("keep")))
        assertTrue(Files.exists(other))
        assertEquals("published_retained",recoverBrowserDownload(root,directory))
    }
    @Test fun absentNamespacesAreNotCreatedAndInvalidIdsAreRejected() = temporary { root ->
        val missing=root.resolve("not-created")
        assertEquals("absent",recoverBrowserDownload(missing,"runs/${UUID.randomUUID()}"))
        assertFalse(Files.exists(missing))
        for(directory in listOf("../outside","runs/../outside","runs/not-a-uuid","/runs/${UUID.randomUUID()}"))
            assertFails{recoverBrowserDownload(root,directory)}
        assertFails{BrowserDownloadFile(root,1,"../bad")}
    }
    @Test fun unknownPartialTypesAreRetainedAndCanBeRetriedAfterInspection() = temporary { root ->
        val directory="runs/${UUID.randomUUID()}";val partial=root.resolve(directory).resolve("download.bin.partial")
        Files.createDirectories(partial)
        assertFails{recoverBrowserDownload(root,directory)}
        assertTrue(Files.isDirectory(partial))
        Files.delete(partial)
        Files.writeString(partial,"stale")
        assertEquals("partial_removed",recoverBrowserDownload(root,directory))
        assertEquals("absent",recoverBrowserDownload(root,directory))
    }
    @Test fun linksAtEveryControlledLevelAreRetainedOnPosix() = temporary { root ->
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
        val outside=Files.createTempDirectory("aurora-browser-outside-")
        try {
            Files.writeString(outside.resolve("download.bin.partial"),"keep")
            Files.createSymbolicLink(root.resolve("runs"),outside)
            assertFails{recoverBrowserDownload(root,"runs/${UUID.randomUUID()}")}
            Files.delete(root.resolve("runs"));Files.createDirectory(root.resolve("runs"))
            val directory="runs/${UUID.randomUUID()}";Files.createSymbolicLink(root.resolve(directory),outside)
            assertFails{recoverBrowserDownload(root,directory)}
            Files.delete(root.resolve(directory));Files.createDirectory(root.resolve(directory))
            Files.createSymbolicLink(root.resolve(directory).resolve("download.bin.partial"),outside.resolve("download.bin.partial"))
            assertFails{recoverBrowserDownload(root,directory)}
            assertEquals("keep",Files.readString(outside.resolve("download.bin.partial")))
            Files.delete(root.resolve(directory).resolve("download.bin.partial"))
        }finally{outside.toFile().deleteRecursively()}
    }
    private fun temporary(action:(Path)->Unit){val root=Files.createTempDirectory("aurora-download-recovery-")
        try{action(root)}finally{root.toFile().deleteRecursively()}}
}
