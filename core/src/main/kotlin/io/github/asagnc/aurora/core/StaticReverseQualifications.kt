package io.github.asagnc.aurora.core

/** Bounded process-local startup evidence; this never replaces persistent dispatch leases. */
class StaticReverseQualifications {
    data class Scope(val generation: String, val identity: LinuxRootIdentity, val bundle: String)
    class Probe internal constructor(val scope: Scope)
    private data class Entry(val probe: Probe, var ready: Boolean = false)
    private val entries = LinkedHashMap<Scope, Entry>()

    @Synchronized fun begin(scope: Scope): Probe {
        // A new attempt invalidates only its own scope. Other running generations stay qualified.
        entries.remove(scope)
        val probe = Probe(scope)
        entries[scope] = Entry(probe)
        while (entries.size > 32) entries.remove(entries.keys.first())
        return probe
    }

    @Synchronized fun complete(probe: Probe): Boolean {
        val entry = entries[probe.scope] ?: return false
        if (entry.probe !== probe) return false
        entry.ready = true
        return true
    }

    @Synchronized fun ready(scope: Scope): Boolean = entries[scope]?.ready == true

    /** Also revokes in-flight attempts, so they cannot publish evidence after removal. */
    @Synchronized fun invalidate(bundle: String) {
        entries.keys.removeAll { it.bundle == bundle }
    }
}
