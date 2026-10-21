package io.github.asagnc.aurora.core

import java.nio.file.Path

/** Optional batch phase and failure ownership; ordinary extraction uses local filesystem operations. */
data class RootfsExtractionHooks(val created: (Path) -> Unit = {}, val hardlinks: (() -> Unit)? = null,
    val cleanup: (Path) -> Unit = ::removeSkillTree)
