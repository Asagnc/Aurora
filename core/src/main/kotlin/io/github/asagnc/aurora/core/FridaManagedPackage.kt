package io.github.asagnc.aurora.core

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class FridaManagedPackage(val appBuild: String, val workerSha256: String,
    val bridge: RootScriptSnapshot, val nativeProbePassed: Boolean, val javaProbePassed: Boolean, val protocol: Int = 1) {
    fun validate() {
        require(protocol == 1 && appBuild.matches(Regex("[1-9][0-9]{0,18}")))
        require(workerSha256.matches(Regex("[a-f0-9]{64}")))
        bridge.validate()
        require(!javaProbePassed || nativeProbePassed)
    }
}

/** Explicitly selected self-test, inside the current Root service only. No user target is modified. */
object FridaManagedProbe {
    private val nativeSource = """
        const cm = new CModule('int aurora_probe(int value) { return value + 7; }', {}, { toolchain: 'internal' });
        let hook = null;
        try {
          let seen = 0;
          hook = Interceptor.attach(cm.aurora_probe, { onLeave(result) { seen++; result.replace(42); } });
          Interceptor.flush();
          const call = new NativeFunction(cm.aurora_probe, 'int', ['int'], { traps: 'all' });
          if (call(1) !== 42 || seen !== 1) throw new Error('aurora_probe_native_hook_failed');
          send({ aurora: 'native_hook_observed', pid: Process.id });
        } finally {
          if (hook !== null) { hook.detach(); Interceptor.flush(); }
          cm.dispose();
        }
    """.trimIndent()
    fun bytes(java: Boolean): ByteArray = (nativeSource + if (java) """

        if (!Java.available) throw new Error('aurora_probe_java_unavailable');
        // The libsu app_process service has ART but no ordinary package data
        // directory. This bootstrap-class probe needs only a VM attachment.
        Java.vm.perform(() => {
          const value = Java.use('java.lang.String').${'$'}new('aurora');
          try { if (value.length() !== 6) throw new Error('aurora_probe_java_call_failed'); }
          finally { value.${'$'}dispose(); }
          send({ aurora: 'java_call_observed', pid: Process.id });
        });
    """.trimIndent() else "").toByteArray(Charsets.UTF_8)
    fun snapshot(java: Boolean): RootScriptSnapshot {
        val bytes = bytes(java)
        return RootScriptSnapshot(if (java) "aurora-self-probe-java-v2" else "aurora-self-probe-native-v1",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, bytes.size.toLong())
    }

    fun matches(ticket: RootManagedRunTicket, root: LinuxRootIdentity, bridge: RootScriptSnapshot): Boolean =
        ticket.identity == root && ticket.target == RootTargetIdentity(root.pid, root.startTicks) &&
            ticket.script == snapshot(ticket.bridge != null) && (ticket.bridge == null || ticket.bridge == bridge) && ticket.observeMs == 10_000L
}
