package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class BrowserUploadSnapshotTest {
    @Test fun copiedFilesHaveStableBytesAndSafeGeneratedPathsIncludingEmptyFiles() = temporary { root ->
        val source=root.resolve("source.txt");Files.writeString(source,"before")
        val files=OwnedFiles(root)
        BrowserUploadSnapshot(root,UUID.randomUUID().toString()).use { lease ->
            val selected=lease.add(files,"source.txt")
            Files.writeString(source,"after")
            assertEquals("before",Files.readString(selected.path))
            val empty=lease.add("../name\\\n.txt",byteArrayOf().inputStream())
            assertEquals(0,Files.size(empty.path));assertFalse(empty.name.contains('/'));assertFalse(empty.name.contains('\\'))
            val typed=lease.add("display.noext",byteArrayOf(1).inputStream(),"png")
            assertEquals("display.noext",typed.name);assertTrue(typed.path.fileName.toString().endsWith(".png"))
            assertEquals(7,lease.bytes)
            if(Files.getFileAttributeView(selected.path,java.nio.file.attribute.PosixFileAttributeView::class.java)!=null)
                assertEquals(setOf(PosixFilePermission.OWNER_READ),Files.getPosixFilePermissions(selected.path))
        }
    }
    @Test fun byteBudgetCancellationAndFileCountDoNotLeavePartialFiles() = temporary { root ->
        val lease=BrowserUploadSnapshot(root,UUID.randomUUID().toString(),3)
        val directory=lease.directory
        lease.use {
            it.add("one",byteArrayOf(1,2).inputStream())
            assertFails{it.add("too-large",byteArrayOf(3,4).inputStream())}
            assertFailsWith<CancellationException>{it.add("cancelled",byteArrayOf(3).inputStream()){throw CancellationException("fixture")}}
            assertEquals(2,it.bytes)
            repeat(7){_->it.add("empty",byteArrayOf().inputStream())}
            assertFails{it.add("ninth",byteArrayOf().inputStream())}
            Files.list(directory).use{entries->assertFalse(entries.anyMatch{file->file.fileName.toString().endsWith(".partial")})}
        }
        assertFalse(Files.exists(directory,LinkOption.NOFOLLOW_LINKS))
    }
    @Test fun oldEpochRecoveryKeepsCurrentAndUnknownDataAndIsBounded() = temporary { root ->
        val current=UUID.randomUUID().toString();val old=UUID.randomUUID().toString()
        val active=BrowserUploadSnapshot(root,current)
        val first=BrowserUploadSnapshot(root,old);first.add("one.txt",byteArrayOf(1).inputStream())
        val second=BrowserUploadSnapshot(root,old);second.add("two.txt",byteArrayOf(2).inputStream())
        val unknown=BrowserUploadSnapshot(root,old);Files.writeString(unknown.directory.resolve("keep-me"),"foreign")
        assertTrue(BrowserUploadSnapshot.recoverOld(root,current,1) in 0..1)
        // Unknown entries are retained, current-process bytes remain usable.
        BrowserUploadSnapshot.recoverOld(root,current)
        assertTrue(Files.exists(active.directory));assertTrue(Files.exists(unknown.directory.resolve("keep-me")))
        assertFalse(Files.exists(first.directory));assertFalse(Files.exists(second.directory))
        Files.delete(unknown.directory.resolve("keep-me"));unknown.close();active.close()
    }
    @Test fun unmarkedDirectoriesAndInvalidInputsAreNeverReclaimed() = temporary { root ->
        val epoch=UUID.randomUUID().toString()
        val unknown=root.resolve(".aurora-browser-uploads/$epoch/${UUID.randomUUID()}")
        Files.createDirectories(unknown);Files.writeString(unknown.resolve("0.txt"),"keep")
        assertEquals(0,BrowserUploadSnapshot.recoverOld(root,UUID.randomUUID().toString()))
        assertEquals("keep",Files.readString(unknown.resolve("0.txt")))
        assertFails{BrowserUploadSnapshot(root,"../invalid")}
        assertFails{BrowserUploadSnapshot.recoverOld(root,epoch,0)}
    }
    @Test fun interruptedEmptyLeaseIsReclaimedButCurrentAndPartialMarkerAreRetained() = temporary { root ->
        val current = UUID.randomUUID().toString(); val old = UUID.randomUUID().toString()
        val base = root.resolve(".aurora-browser-uploads")
        val empty = Files.createDirectories(base.resolve("$old/${UUID.randomUUID()}"))
        val active = Files.createDirectories(base.resolve("$current/${UUID.randomUUID()}"))
        val partial = Files.createDirectories(base.resolve("$old/${UUID.randomUUID()}"))
        Files.write(partial.resolve("lease"), byteArrayOf(65))
        assertEquals(1, BrowserUploadSnapshot.recoverOld(root, current))
        assertFalse(Files.exists(empty)); assertTrue(Files.exists(active))
        assertContentEquals(byteArrayOf(65), Files.readAllBytes(partial.resolve("lease")))
        assertEquals(0, BrowserUploadSnapshot.recoverOld(root, current))
    }
    @Test fun recoveryAndSelectionDoNotFollowLinksOnPosix() = temporary { root ->
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
        val outside=Files.createTempFile("aurora-upload-outside-", ".txt")
        try {
            Files.writeString(outside,"keep")
            val old=BrowserUploadSnapshot(root,UUID.randomUUID().toString())
            Files.createSymbolicLink(old.directory.resolve("0.txt"),outside)
            assertEquals(0,BrowserUploadSnapshot.recoverOld(root,UUID.randomUUID().toString()))
            assertEquals("keep",Files.readString(outside))
            val link=root.resolve("source.txt");Files.createSymbolicLink(link,outside)
            BrowserUploadSnapshot(root,UUID.randomUUID().toString()).use { lease ->
                assertFails{lease.add(OwnedFiles(root),"source.txt")}
            }
            Files.delete(old.directory.resolve("0.txt"));old.close()
        } finally { Files.deleteIfExists(outside) }
    }
    private fun temporary(action:(java.nio.file.Path)->Unit){
        val root=Files.createTempDirectory("aurora-upload-snapshot-")
        try{action(root)}finally{root.toFile().deleteRecursively()}
    }
}
