package io.github.asagnc.aurora.core

import java.nio.file.*

/** Caller must prove the recorded tool is interrupted and no longer owns an open writer. */
fun recoverBrowserDownload(root: Path, directory: String): String {
    require(root.isAbsolute && commandArtifactId(directory) != null)
    var path=root.root
    for(part in root){path=path.resolve(part);require(!Files.isSymbolicLink(path)) { "Download root contains a link" }}
    if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return "absent"
    require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
    val runs=root.resolve("runs")
    if(!Files.exists(runs,LinkOption.NOFOLLOW_LINKS))return "absent"
    require(Files.isDirectory(runs,LinkOption.NOFOLLOW_LINKS))
    val artifact=root.resolve(directory)
    if(!Files.exists(artifact,LinkOption.NOFOLLOW_LINKS))return "absent"
    require(Files.isDirectory(artifact,LinkOption.NOFOLLOW_LINKS))
    val complete=artifact.resolve("download.bin")
    if(Files.exists(complete,LinkOption.NOFOLLOW_LINKS))require(Files.isRegularFile(complete,LinkOption.NOFOLLOW_LINKS))
    val partial=artifact.resolve("download.bin.partial")
    var removed=false
    if(Files.exists(partial,LinkOption.NOFOLLOW_LINKS)){
        require(Files.isRegularFile(partial,LinkOption.NOFOLLOW_LINKS)) { "Unrecognised partial entry retained" }
        Files.delete(partial);removed=true
    }
    // Never remove a published file, sibling, directory or unrecorded artifact.
    return if(Files.exists(complete,LinkOption.NOFOLLOW_LINKS)) "published_retained" else if(removed) "partial_removed" else "absent"
}
