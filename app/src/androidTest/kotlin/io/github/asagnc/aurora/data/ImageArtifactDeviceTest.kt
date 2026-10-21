package io.github.asagnc.aurora.data

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.ImageToolExecutor
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageArtifactDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication

    @Test fun publishSnapshotSurvivesSourceChangeAndStoreReopen() = runBlocking {
        fixture { owner ->
            val source = writePng(owner)
            val image = app.images.publish(owner, "source.png", "示例图片")
            assertEquals(16, image.width)
            assertEquals(12, image.height)
            Files.write(source, byteArrayOf(0))
            val reopened = ImageArtifactStore(app)
            assertEquals(image, reopened.resolve(owner, image.link))
            assertTrue(reopened.file(owner, image).length() > 1)
        }
    }

    @Test fun foreignConversationCannotResolveImage() = runBlocking {
        fixture { owner -> fixture { other ->
            writePng(owner)
            val image = app.images.publish(owner, "source.png", "")
            assertTrue(runCatching { app.images.resolve(other, image.link) }.isFailure)
        } }
    }

    @Test fun invalidImageAndChangedArtifactFailExplicitly() = runBlocking {
        fixture { owner ->
            val root = app.spaces.commandRoot(owner)
            Files.write(root.resolve("invalid.png"), byteArrayOf(1, 2, 3))
            assertTrue(runCatching { app.images.publish(owner, "invalid.png", "") }.isFailure)
            val failed = ImageToolExecutor(app.images, owner).execute(ToolCall("invalid-image", "images_publish",
                objectOf("source" to string("invalid.png"), "description" to string(""))))
            assertEquals("image_publish_failed", WireJson.parseToJsonElement(failed).jsonObject.text("error"))
            writePng(owner)
            val image = app.images.publish(owner, "source.png", "")
            val file = app.images.file(owner, image)
            val changed = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            file.writeBytes(changed)
            assertTrue(runCatching { app.images.file(owner, image) }.isFailure)
        }
    }

    @Test fun agentToolReturnsPersistentMarkdownAndExportCopiesExactBytes() = runBlocking {
        fixture { owner ->
            writePng(owner)
            val tool = ImageToolExecutor(app.images, owner)
            val result = tool.execute(ToolCall("image-test", "images_publish", objectOf("source" to string("source.png"), "description" to string("结果图"))))
            val record = WireJson.parseToJsonElement(result).jsonObject
            val image = WireJson.decodeFromJsonElement(ImageArtifact.serializer(), record.getValue("image"))
            assertTrue(record.getValue("markdown").jsonPrimitive.content.contains(image.link))
            val output = ByteArrayOutputStream()
            app.images.copyTo(owner, image, output)
            assertArrayEquals(app.images.file(owner, image).readBytes(), output.toByteArray())
        }
    }

    @Test fun deletedConversationDoesNotRecreateArtifactDirectory() = runBlocking {
        app.ready.await()
        val owner = app.conversations.create("image-test")
        val root = app.spaces.commandRoot(owner)
        try {
            writePng(owner)
            val image = app.images.publish(owner, "source.png", "")
            app.spaces.delete(owner, app.tasks::quiesce)
            assertFalse(Files.exists(root))
            assertTrue(runCatching { app.images.resolve(owner, image.link) }.isFailure)
            assertFalse(Files.exists(root))
        } finally {
            if (app.conversations.header(owner) != null) app.spaces.delete(owner, app.tasks::quiesce)
        }
    }

    private suspend fun fixture(block: suspend (String) -> Unit) {
        app.ready.await()
        val owner = app.conversations.create("image-test")
        try { block(owner) } finally { app.spaces.delete(owner, app.tasks::quiesce) }
    }

    private suspend fun writePng(owner: String): java.nio.file.Path {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) } finally { bitmap.recycle() }
        return app.spaces.commandRoot(owner).resolve("source.png").also { Files.write(it, output.toByteArray()) }
    }
}
