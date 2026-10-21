package io.github.asagnc.aurora.root

import android.os.Bundle
import io.github.asagnc.aurora.runtime.CommandRequest

internal object RootCommandWire {
    fun encode(request: CommandRequest) = Bundle().apply {
        putInt("version", 2)
        putStringArrayList("argv", ArrayList(request.argv))
        putString("directory", request.directory)
        putLong("timeoutMs", request.timeoutMs)
        putInt("outputLimit", request.outputLimit)
        putBundle("environment", Bundle().apply { request.environment.forEach { (key, value) -> putString(key, value) } })
    }

    fun decode(bundle: Bundle): CommandRequest {
        val parcel = android.os.Parcel.obtain()
        try { parcel.writeBundle(bundle); require(parcel.dataSize() <= 65536) { "Root request exceeds Binder budget" } }
        finally { parcel.recycle() }
        require(bundle.getInt("version") == 2)
        require(bundle.getInt("outputLimit") in 1024..8192)
        require(bundle.getLong("timeoutMs") in 1..300_000)
        val environment = requireNotNull(bundle.getBundle("environment"))
        require(environment.size() <= 64)
        return CommandRequest(requireNotNull(bundle.getStringArrayList("argv")).toList(),
            requireNotNull(bundle.getString("directory")), bundle.getLong("timeoutMs"), bundle.getInt("outputLimit"),
            environment.keySet().associateWith { requireNotNull(environment.getString(it)) })
    }
}
