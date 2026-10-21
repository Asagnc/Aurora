package io.github.asagnc.aurora.linux

/** Explicit opt-in: may download/install the pinned official Linux base. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LinuxImageRequired
