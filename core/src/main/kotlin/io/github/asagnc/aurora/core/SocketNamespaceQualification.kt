package io.github.asagnc.aurora.core

/** Missing namespace files alone never establish the UID interpretation. */
fun initialUserNamespaceConfiguration(configuration: ByteArray): Boolean {
    if (configuration.size !in 1..2097152 || configuration.any { it < 0 || it == 0.toByte() }) return false
    val lines = configuration.toString(Charsets.US_ASCII).lineSequence().toList()
    return lines.count { it == "# CONFIG_USER_NS is not set" } == 1 && lines.none { it.startsWith("CONFIG_USER_NS=") }
}
