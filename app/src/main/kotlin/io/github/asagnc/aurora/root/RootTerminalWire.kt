package io.github.asagnc.aurora.root

import android.os.Bundle
import io.github.asagnc.aurora.terminal.PtyRequest

internal object RootTerminalWire {
    fun encode(request: PtyRequest): Bundle {
        request.validate()
        return Bundle().apply {
            putInt("protocol", 1); putStringArrayList("argv", ArrayList(request.argv))
            putString("directory", request.directory); putInt("columns", request.columns); putInt("rows", request.rows)
            putLong("timeoutMs", request.timeoutMs)
            putLong("awakeMs", request.awakeMs)
            putBundle("environment", Bundle().apply { request.environment.forEach { (key, value) -> putString(key, value) } })
        }
    }
    fun decode(wire: Bundle): PtyRequest {
        require(wire.getInt("protocol") == 1)
        val env = wire.getBundle("environment") ?: Bundle()
        require(env.size() <= 64)
        return PtyRequest(requireNotNull(wire.getStringArrayList("argv")), wire.getString("directory").orEmpty(),
            wire.getInt("columns"), wire.getInt("rows"), wire.getLong("timeoutMs"),
            env.keySet().associateWith { requireNotNull(env.getString(it)) }, wire.getLong("awakeMs")).also { it.validate() }
    }
}
