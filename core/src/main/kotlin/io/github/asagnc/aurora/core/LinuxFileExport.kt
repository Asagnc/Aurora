package io.github.asagnc.aurora.core

/** File transport budgets are separate from inline reads; neither kind installs or executes a file. */
data class LinuxFileExport(val path: String, val apk: Boolean = false,
    val maxBytes: Long = if (apk) 512L * 1024 * 1024 else 32L * 1024 * 1024, val sha256: String? = null) {
    init {
        require(path.toByteArray(Charsets.UTF_8).size in 1..4096 && '\\' !in path && path.none { it.code < 32 || it.code == 127 })
        val parts = path.split('/')
        require(parts.first() in setOf("home", "tmp", "project") && parts.none { it.isEmpty() || it in setOf(".", "..", ".aurora-transactions") })
        require(maxBytes in 1..(if (apk) 512L else 32L) * 1024 * 1024)
        require(!apk || parts.last().endsWith(".apk", ignoreCase = true))
        require(sha256 == null || sha256.matches(Regex("[a-f0-9]{64}")))
    }
}
