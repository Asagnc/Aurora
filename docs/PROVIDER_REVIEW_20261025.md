# 2026-10-25 公开模型与缓存核对

只读取官方公开资料，无模型请求、账号路由、token/费用/延迟或实际缓存命中测试。预设是可选目录，更新不会修改已保存连接或切换活动任务。

| 厂商 | 核对结果与官方来源 |
| --- | --- |
| OpenAI | [GPT6文档](https://developers.openai.com/api/docs/guides/latest-model)继续列 Astra、6.1 Sol、Luna；现有工具调用协议与effort限制保持。 |
| Claude | [当前目录](https://platform.claude.com/docs/en/models/overview)仍列 Fable5.1、Opus/Sonnet/Haiku5.5；未变更ID。 |
| DeepSeek | [官方入口](https://api-docs.deepseek.com/)的 deepseek-flash 当前映射 V4.1-Flash-1017，增加说明；保留稳定别名及v4-pro。 |
| Gemini | [官方目录](https://ai.google.dev/gemini-api/docs/models)继续推荐3.8 Flash/3.5 Flash-Lite；未加入preview型号。 |
| GLM | [官方目录](https://docs.bigmodel.cn/cn/guide/start/model-overview)继续列5.3/Flash/FlashX；不把厂商速度宣传当Aurora测量。 |
| MiMo | [官方Markdown目录](https://mimo.mi.com/static/docs/quick-start/summary/model.md)列2.6 Pro/Flash/Pro-Ultraspeed；2.5文本型号已停用，当前预设无2.5。 |
| Grok | [官方目录](https://docs.x.ai/developers/models)仍推荐4.7；未变更ID。 |
| MiniMax | [官方Anthropic兼容文档](https://platform.minimax.cn/docs/api-reference/text-anthropic-api)新增M3.1-Flash，与M3均受支持；新增前者预设及其low/medium/high/xhigh/max，none不支持。 |
| Kimi | [K3.1 Pro文档](https://platform.kimi.com/docs/guide/kimi-k3-1-pro-quickstart)列kimi-k3.1-pro与highspeed，新增两项；low/high/max经文档核实，none不支持。保留仍受支持的K3，提示Pro的账号解锁条件。 |
| 百炼 | [官方目录](https://help.aliyun.com/zh/model-studio/text-generation-model)仍列qwen3.8-max/flash与3.7-plus；地域/业务空间规则保持。 |

[OpenAI缓存文档](https://developers.openai.com/api/docs/guides/prompt-caching)确认新系列请求默认30m，另有12h及不同写入费率；当前不强制切换长TTL或增加写入成本。现有configuration_update仅对已核实OpenAI官方Responses组合开放，不扩散到中转/Kimi/MiniMax。前缀相同不能保证命中。

Kimi新型号说明包含5min/1h缓存写入及费用；本轮未新增其显式缓存控制，不从OpenAI字段猜测兼容。MiniMax与Claude均需要保留原始thinking/工具历史，现有协议原生历史路径保持。新增编码回归检查真实请求字段与不支持档位，不发送网络请求。

仍待用户允许真实连接后验证：账号可用性、网关协议差异、实际路由/缓存读写/计费、连续工具轮次与延迟。公开目录刷新不代表这些项目已经验收。
