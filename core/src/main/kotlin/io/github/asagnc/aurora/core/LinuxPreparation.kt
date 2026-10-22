package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable data class LinuxRootIdentity(val pid: Int, val startTicks: Long, val bootId: String, val epoch: String) {
    fun validate() {
        require(pid > 0 && startTicks > 0)
        require(UUID.fromString(bootId).toString() == bootId && UUID.fromString(epoch).toString() == epoch)
    }
}

@Serializable data class LinuxPreparationTicket(val operation: String, val identity: LinuxRootIdentity,
    val image: String, val appUid: Int, val protocol: Int = 1, val purpose: String = "hardlinks") {
    fun validate() {
        require((protocol == 1 && purpose == "hardlinks" || protocol == 2 && purpose in setOf("root_ownership", "root_removal")) &&
            appUid > 0 && image.matches(Regex("[a-f0-9]{64}")))
        require(UUID.fromString(operation).toString() == operation)
        identity.validate()
    }
}

/** proc comm can contain spaces and parentheses; field 22 follows its final ')'. */
fun linuxProcessStartTicks(stat: String): Long {
    require(stat.length <= 65536 && stat.substringBefore(' ').toInt() > 0)
    val end = stat.lastIndexOf(')')
    require(end > stat.indexOf('(') && stat.indexOf('(') > 0)
    return requireNotNull(stat.substring(end + 1).trim().split(Regex("\\s+")).getOrNull(19)?.toLongOrNull())
        .also { require(it > 0) }
}
