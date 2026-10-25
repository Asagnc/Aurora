package io.github.asagnc.aurora.core

import java.net.URI
import kotlinx.serialization.json.*

enum class CacheImpact { UNCHANGED, PREFIX_PRESERVING, NEW_SCOPE, THINKING_INVALIDATION, UNKNOWN }

object CachePolicy {
    fun impact(previous: Connection, next: Connection): CacheImpact = when {
        previous == next -> CacheImpact.UNCHANGED
        previous.id != next.id || previous.baseUrl != next.baseUrl ||
            previous.protocol != next.protocol || previous.model != next.model ||
            previous.credentialRevision != next.credentialRevision || previous.authentication != next.authentication -> CacheImpact.NEW_SCOPE
        previous.effort == next.effort -> CacheImpact.UNCHANGED
        supportsEffortUpdates(next) && next.effort != "auto" -> CacheImpact.PREFIX_PRESERVING
        officialHost(next) == "api.anthropic.com" && next.protocol == Protocol.ANTHROPIC -> CacheImpact.THINKING_INVALIDATION
        officialHost(next) == "api.openai.com" -> CacheImpact.THINKING_INVALIDATION
        else -> CacheImpact.UNKNOWN
    }

    fun supportsEffortUpdates(connection: Connection): Boolean =
        officialHost(connection) == "api.openai.com" && connection.protocol == Protocol.RESPONSES &&
            connection.model in setOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-luna")

    private fun officialHost(connection: Connection): String? = runCatching {
        URI(connection.baseUrl).takeIf { it.scheme == "https" && it.userInfo == null }?.host?.lowercase()
    }.getOrNull()

    fun effectiveEffort(history: WireHistory): String = history.entries.asReversed()
        .firstOrNull { it.text("type") == "configuration_update" }
        ?.get("reasoning")?.jsonObject?.text("effort") ?: history.effort

    fun canReuse(history: WireHistory, connection: Connection, fingerprint: String): Boolean {
        if (history.connectionId != connection.id || history.model != connection.model ||
            history.protocol != connection.protocol || history.baseUrl != connection.baseUrl ||
            history.credentialRevision != connection.credentialRevision || history.authentication != connection.authentication ||
            history.prefixFingerprint != fingerprint) return false
        val effort = effectiveEffort(history)
        return effort == connection.effort || (supportsEffortUpdates(connection) && connection.effort != "auto")
    }

    fun switchExplanation(impact: CacheImpact): String = when (impact) {
        CacheImpact.PREFIX_PRESERVING -> "下一轮将追加思考等级更新，保留原有请求前缀；实际缓存命中以服务端统计为准。"
        CacheImpact.NEW_SCOPE -> "此变更会开启新的缓存范围。会话内容和已完成工具结果会保留，下一轮可能重新处理输入并产生费用。"
        CacheImpact.THINKING_INVALIDATION -> "此思考配置变化会影响缓存，下一轮可能重新处理输入并产生费用。"
        CacheImpact.UNKNOWN -> "无法确认此中转是否保留缓存，下一轮可能重新处理输入并产生费用。"
        CacheImpact.UNCHANGED -> "配置未发生有效变化。"
    } + " 当前任务保持原配置，变更用于下一轮。"

    fun supportedEfforts(connection: Connection): List<String> {
        val model = connection.model.lowercase()
        return when {
            model in setOf("gpt-6-astra", "gpt-6.1-sol") && connection.protocol in setOf(Protocol.CHAT, Protocol.RESPONSES) ->
                listOf("auto", "low", "medium", "high", "xhigh", "max")
            model == "gpt-6-luna" && connection.protocol in setOf(Protocol.CHAT, Protocol.RESPONSES) ->
                listOf("auto", "none", "low", "medium", "high", "xhigh", "max")
            model in setOf("kimi-k3", "kimi-k3.1-pro", "kimi-k3.1-pro-highspeed") && connection.protocol == Protocol.CHAT ->
                listOf("auto", "low", "high", "max")
            model == "minimax-m3.1-flash" && connection.protocol == Protocol.ANTHROPIC ->
                listOf("auto", "low", "medium", "high", "xhigh", "max")
            model in setOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1") && connection.protocol == Protocol.ANTHROPIC ->
                listOf("auto", "low", "medium", "high", "xhigh", "max")
            else -> listOf("auto")
        }
    }
}
