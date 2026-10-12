package io.github.asagnc.aurora.core

import java.net.URI

class ConfigurationException(message: String) : IllegalArgumentException(message)

object ModelRules {
    fun validate(request: ModelRequest) {
        val connection = request.connection
        if (connection.effort !in CachePolicy.supportedEfforts(connection)) throw ConfigurationException("此模型和协议尚未验证该思考等级")
        if (URI(connection.baseUrl).host != "api.openai.com" || request.tools.isEmpty() || connection.protocol != Protocol.CHAT) return
        if (connection.model in setOf("gpt-6-astra", "gpt-6.1-sol")) throw ConfigurationException("此 OpenAI 模型的工具调用需要 Responses 协议，请修改连接协议")
        if (connection.model == "gpt-6-luna" && connection.effort != "none") throw ConfigurationException("GPT-6 Luna 在 Chat 协议下调用工具需要 none 思考等级；使用 Responses 可保留推理能力")
    }
}
