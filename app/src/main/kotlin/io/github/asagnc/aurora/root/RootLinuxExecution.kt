package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.core.LinuxRootIdentity

/** Identity and clients captured from one published Binder connection. */
internal data class RootLinuxExecution(
    val identity: LinuxRootIdentity,
    val run: RootLinuxRunClient,
    val terminal: RootTerminalClient?,
)
