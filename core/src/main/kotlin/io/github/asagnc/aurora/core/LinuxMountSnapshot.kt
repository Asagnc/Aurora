package io.github.asagnc.aurora.core

/** proc_pid_mountinfo(5): ignore optional fields; decode the kernel's mount-path escapes exactly once. */
fun linuxMountPoints(snapshot: String): List<String> {
    require(snapshot.isNotBlank() && snapshot.length <= 4 * 1024 * 1024)
    val result = ArrayList<String>()
    for (line in snapshot.lineSequence().filter { it.isNotEmpty() }) {
        require(result.size < 65536)
        val fields = line.split(' ')
        val separator = fields.indexOf("-")
        require(fields.size >= 10 && separator >= 6 && fields.size >= separator + 4)
        require(fields[0].toLongOrNull()?.let { it > 0 } == true && fields[1].toLongOrNull()?.let { it > 0 } == true)
        require(fields[2].matches(Regex("[0-9]+:[0-9]+")) && fields[3].startsWith('/') && fields[4].startsWith('/'))
        result += linuxMountPath(fields[4])
    }
    require(result.isNotEmpty())
    return result
}

private fun linuxMountPath(encoded: String): String = buildString {
    var index = 0
    while (index < encoded.length) {
        if (encoded[index] != '\\') { append(encoded[index++]); continue }
        require(index + 4 <= encoded.length)
        append(when (encoded.substring(index + 1, index + 4)) {
            "040" -> ' '; "011" -> '\t'; "012" -> '\n'; "134" -> '\\'
            else -> error("Unknown mount-path escape; deletion cannot assume this snapshot is safe")
        })
        index += 4
    }
}
