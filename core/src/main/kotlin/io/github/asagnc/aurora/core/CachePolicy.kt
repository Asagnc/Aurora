package io.github.asagnc.aurora.core

enum class CacheImpact { UNCHANGED, NEW_SCOPE, THINKING_INVALIDATION, UNKNOWN }

object CachePolicy {
    fun impact(previous: Connection, next: Connection): CacheImpact = when {
        previous == next -> CacheImpact.UNCHANGED
        previous.id != next.id || previous.baseUrl != next.baseUrl ||
            previous.protocol != next.protocol || previous.model != next.model -> CacheImpact.NEW_SCOPE
        previous.effort == next.effort -> CacheImpact.UNCHANGED
        next.official && next.protocol == Protocol.ANTHROPIC -> CacheImpact.THINKING_INVALIDATION
        else -> CacheImpact.UNKNOWN
    }

    fun supportedEfforts(connection: Connection): List<String> {
        val model = connection.model.lowercase()
        return when {
            model.matches(Regex("gpt-6([.-].*)?")) -> listOf("auto", "low", "medium", "high", "xhigh", "max")
            model == "kimi-k3" -> listOf("auto", "low", "high", "max")
            model.startsWith("claude-") && connection.protocol == Protocol.ANTHROPIC ->
                listOf("auto", "low", "medium", "high")
            else -> listOf("auto")
        }
    }
}
