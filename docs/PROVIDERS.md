# 官方连接与型号核查

2026-10-12。这些是官方资料核查的稳定文本/Agent 型号，不是付费账户可用性或实际网关测试结果。目录不纳入 preview/experimental 型号、已退役的历史别名、仅音视频生成型号。当前目录随应用版本更新，动态刷新与账户能力检测仍未完成。品牌图标未添加。

| 供应商 | 推荐协议 | 认证 | 当前型号来源 |
| --- | --- | --- | --- |
| OpenAI | Responses | Bearer | [官方 GPT-6 指南](https://developers.openai.com/api/docs/guides/latest-model) |
| Claude | Messages | x-api-key | [当前模型目录](https://platform.claude.com/docs/en/models/overview) |
| DeepSeek | Chat Completions | Bearer | [首次调用与现行型号](https://api-docs.deepseek.com/) |
| Gemini | Chat Completions | Bearer | [稳定模型](https://ai.google.dev/gemini-api/docs/models)、[官方兼容接口](https://ai.google.dev/gemini-api/docs/openai) |
| GLM | Chat Completions | Bearer | [API 地址](https://docs.bigmodel.cn/cn/api/introduction)、[GLM-5.3](https://docs.bigmodel.cn/cn/guide/models/text/glm-5.3)、[Flash/FlashX](https://docs.bigmodel.cn/cn/guide/models/vlm/glm-5.3-flash) |
| 小米 MiMo | Chat Completions | api-key | [首次调用](https://mimo.mi.com/static/docs/quick-start/summary/first-api-call.md)、[当前模型](https://mimo.mi.com/static/docs/quick-start/summary/model.md) |
| Grok | Responses | Bearer | [当前模型](https://docs.x.ai/developers/models)、[API 地址](https://docs.x.ai/developers/quickstart) |
| MiniMax | Messages | Bearer | [当前模型](https://platform.minimaxi.com/docs/guides/models-intro)、[兼容接口](https://platform.minimaxi.com/docs/api-reference/text-anthropic-api) |
| Kimi | Chat Completions | Bearer | [中国站 K3](https://platform.kimi.com/docs/guide/kimi-k3-quickstart) |
| 阿里百炼 | Chat Completions | Bearer | [当前型号](https://help.aliyun.com/zh/model-studio/text-generation-model)、[业务空间域名与地域限制](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope) |

默认中国站按量接口；套餐、国际站和其他地域通过独立连接配置，不把密钥发送给其他站点。百炼北京使用业务空间专属域名，空间 ID 只接受合法 DNS 标签，拒绝地址注入。Gemini 稳定模型使用官方文档给出的 v1beta/openai 兼容路径；路径名称不代表使用 preview 模型。

协议、模型 ID 和端点能力分开处理。选择官方型号不会让自定义中转自动获得官方缓存更新能力。官方 API 地址与认证字段按各家实际文档配置；本地网络夹具验证 api-key 与 x-api-key/Bearer 分离，没有向服务商发送测试密钥或付费请求。
