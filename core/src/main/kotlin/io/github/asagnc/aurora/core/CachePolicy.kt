package io.github.asagnc.aurora.core

enum class CacheImpact { UNCHANGED, NEW_SCOPE, THINKING_INVALIDATION, UNKNOWN }

object CachePolicy {
    fun impact(previous: Connection, next: Connection): CacheImpact = when {
        previous == next -> CacheImpact.UNCHANGED
        previous.id != next.id || previous.baseUrl != next.baseUrl ||
            previous.protocol != next.protocol || previous.model != next.model ||
            previous.credentialRevision != next.credentialRevision || previous.authentication != next.authentication -> CacheImpact.NEW_SCOPE
        previous.effort == next.effort -> CacheImpact.UNCHANGED
        next.official && next.protocol == Protocol.ANTHROPIC -> CacheImpact.THINKING_INVALIDATION
        else -> CacheImpact.UNKNOWN
    }

    fun supportedEfforts(connection: Connection): List<String> {
        val model = connection.model.lowercase()
        return when {
            model in setOf("gpt-6-astra", "gpt-6.1-sol") && connection.protocol in setOf(Protocol.CHAT, Protocol.RESPONSES) ->
                listOf("auto", "low", "medium", "high", "xhigh", "max")
            model == "gpt-6-luna" && connection.protocol in setOf(Protocol.CHAT, Protocol.RESPONSES) ->
                listOf("auto", "none", "low", "medium", "high", "xhigh", "max")
            model == "kimi-k3" && connection.protocol == Protocol.CHAT -> listOf("auto", "low", "high", "max")
            model in setOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1") && connection.protocol == Protocol.ANTHROPIC ->
                listOf("auto", "low", "medium", "high", "xhigh", "max")
            else -> listOf("auto")
        }
    }
}
