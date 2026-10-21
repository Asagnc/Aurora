package io.github.asagnc.aurora.ui

import coil3.Image
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.ImageArtifact
import kotlinx.coroutines.coroutineScope

/** Keep decode in the deletion lease, including Coil's asynchronous work. */
internal suspend fun loadOwnedImage(app: AuroraApplication, owner: String, artifact: ImageArtifact, pixels: Int, full: Boolean = false): Image = coroutineScope {
    app.tasks.run(owner) {
        val file = app.images.file(owner, artifact)
        val request = ImageRequest.Builder(app).data(file).size(pixels, pixels)
            .memoryCacheKey("$owner:${artifact.id}:${artifact.sha256}:$pixels")
            .memoryCachePolicy(if (full) CachePolicy.DISABLED else CachePolicy.ENABLED).build()
        val result = app.imageLoader.execute(request)
        require(result is SuccessResult) { "Image decode failed" }
        result.image
    }
}
