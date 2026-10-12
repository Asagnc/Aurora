package io.github.asagnc.aurora.core

data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val protocol: Protocol,
    val authentication: Authentication,
    val models: List<String>,
    val source: String,
    val note: String = "",
) {
    val requiresWorkspace: Boolean get() = baseUrl.contains("{workspace}")

    fun endpoint(workspace: String = ""): String {
        if (!requiresWorkspace) return baseUrl
        require(workspace.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"))) { "请填写百炼业务空间 ID" }
        return baseUrl.replace("{workspace}", workspace.lowercase())
    }
}

/** Curated stable model lines. Account access and gateway capabilities are separate evidence. */
object ProviderCatalog {
    const val checkedOn = "2026-10-12"
    val presets = listOf(
        ProviderPreset("OpenAI", "https://api.openai.com/v1", Protocol.RESPONSES, Authentication.BEARER,
            listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-luna"), "https://developers.openai.com/api/docs/guides/latest-model"),
        ProviderPreset("Claude", "https://api.anthropic.com/v1", Protocol.ANTHROPIC, Authentication.API_KEY,
            listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1"),
            "https://platform.claude.com/docs/en/models/overview"),
        ProviderPreset("DeepSeek", "https://api.deepseek.com", Protocol.CHAT, Authentication.BEARER,
            listOf("deepseek-flash", "deepseek-v4-pro"), "https://api-docs.deepseek.com/"),
        ProviderPreset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", Protocol.CHAT, Authentication.BEARER,
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), "https://ai.google.dev/gemini-api/docs/models",
            "使用官方 OpenAI 兼容接口；此目录选择稳定型号。"),
        ProviderPreset("GLM", "https://open.bigmodel.cn/api/paas/v4", Protocol.CHAT, Authentication.BEARER,
            listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx"), "https://docs.bigmodel.cn/cn/guide/start/model-overview",
            "按量 API；Coding Plan 的专用地址和密钥需另建连接。"),
        ProviderPreset("小米 MiMo", "https://api.xiaomimimo.com/v1", Protocol.CHAT, Authentication.API_KEY_HEADER,
            listOf("mimo-v2.6-pro", "mimo-v2.6-flash", "mimo-v2.6-pro-ultraspeed"),
            "https://mimo.mi.com/static/docs/quick-start/summary/model.md", "按量 API；Token Plan 使用专用地址和密钥。"),
        ProviderPreset("Grok", "https://api.x.ai/v1", Protocol.RESPONSES, Authentication.BEARER,
            listOf("grok-4.7"), "https://docs.x.ai/developers/models"),
        ProviderPreset("MiniMax", "https://api.minimax.cn/anthropic/v1", Protocol.ANTHROPIC, Authentication.BEARER,
            listOf("MiniMax-M3"), "https://platform.minimaxi.com/docs/guides/models-intro",
            "中国站按量 API；M Plan 和国际站需使用相应地址与密钥。"),
        ProviderPreset("Kimi", "https://api.moonshot.cn/v1", Protocol.CHAT, Authentication.BEARER,
            listOf("kimi-k3"), "https://platform.kimi.com/docs/guide/kimi-k3-quickstart",
            "中国站按量 API；Kimi Code 与国际站使用独立密钥。"),
        ProviderPreset("阿里百炼 · 北京", "https://{workspace}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", Protocol.CHAT,
            Authentication.BEARER, listOf("qwen3.8-max", "qwen3.8-flash", "qwen3.7-plus"),
            "https://help.aliyun.com/zh/model-studio/text-generation-model", "填写北京业务空间 ID，使用同地域 API 密钥。其他地域可另建连接。"),
    )
}
