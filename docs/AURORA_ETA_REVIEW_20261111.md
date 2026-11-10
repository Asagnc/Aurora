# Aurora／Eta 底层比较与缓存优先改造（2026-11-11）

用户决定：UI 直接按 Eta 最新版的页面与交互统一，停止自创风格；底层逐项择优，不整体替换。按既有约定，新增底层、数据库、模型服务或持久化方案仍单独给出比较及确认；同一栈上的实现修正继续做。真机最后集中，不逐页要求连接；不调用付费模型、不访问正式签名密钥。

## 可复查基线

- Aurora：main `a619f4c67afd29e910c80932bab9ebcfb9409f07`，上一产品提交 `46feccea265ee5adaf2c94c5cb2e673c42c7df09`；本轮以实际本地源码为准。
- Eta 最新正式版：[v3.4.1](https://github.com/Mangi-11/Eta/releases/tag/v3.4.1)，tag commit `300b0d186d6339ee95d2f8b40aa19165b4b71e8c`。
- Eta 检索时 main：[7150c0d357e8b587f2adb2a70102caf7046e25e9](https://github.com/Mangi-11/Eta/commit/7150c0d357e8b587f2adb2a70102caf7046e25e9)。与该正式版 diff 仅 LICENSE、README 与许可说明，运行实现和 UI 代码无差异。发布后 main 不等于新版发行包。
- Eta 正式版 LICENSE 是 PolyForm Noncommercial 1.0.0；当前 main 已改 GPL-3.0。这里复核代码和布局，不将其受限源码直接复制进 Aurora，不更改 Aurora 项目许可；UI 使用已批准的 COUI 独立实现同一布局和交互。
- 原始源码克隆、检索中间材料仅存忽略目录 `app/build/eta-comparison`。本次没有运行 Eta 的 Gradle、安装其依赖或执行其脚本。

## 逐项取舍

“采用”基于当前需求、源码合同和已有资格，并非未经测试的全局性能排名。未在相同供应商、账号和任务上测过的真实命中率、成本、速度不作胜负结论。

| 层次 | Aurora 实际实现 | Eta 实际实现 | 取舍及依据 |
| --- | --- | --- | --- |
| Responses 原生历史 | SQLite owner／scope 分区保存完整 output，按原序追加，复用检查包含端点、模型、凭据修订、认证和前缀指纹 | ResponsesEphemeralState 完整 output 只在一次 run 内传递，稳定 conversation codec 主动丢弃这些临时项 | 保留 Aurora；官方要求手工续接时保留原始 input／output 及顺序，避免跨轮重新合成丢失 reasoning 等字段 |
| Anthropic thinking | 原生块与 signature 留在持久 wire 历史，不从 UI 文本重造 | AnthropicMessagesProvider 仅当前真实用户轮之后的助手保留 thinking，更早轮次主动剥离 | 保留 Aurora 原生路径；新模型支持 preserved thinking，不能沿用所有型号只保留当前轮的规则。旧型号由服务端按各自合同处理，不为提高数字伪造 thinking |
| 稳定 system／Skills 前缀 | 固定 SYSTEM，Skills 目录摘要仅在变化时追加为上下文，不回写已有 prefix | 固定进度不进入 system 是优点；system 还包含当前 model、Root、执行环境、记忆和 Skills 快照 | 保留 Aurora 的追加方式，借鉴 Eta 把临时状态放工具结果的原则；没有发现 Eta 每轮无条件塞当前时间，不能凭印象归咎时间戳 |
| 工具目录 | 同一 run 固定 ToolRouter，跨 run 根据当前 Root 通道生成，指纹变化会建新范围 | 单次 run 工具只增不减，Root 授权改变时重建 system／目录 | 当前保留 Aurora；不能为缓存伪装已经撤权的能力。稳定追加工具更新属于型号限定协议能力，另做合同与资格后再接 |
| Anthropic 断点 | 原先 system＋末消息，缺少工具目录断点，空文本／未知块也可能被误选 | 最后工具、system、末消息都有断点，并排除不适合的块 | 采用官方三层前缀布局，补 Aurora 工具断点与严格可缓存类型；不复制 Eta 实现 |
| OpenRouter 会话路由 | 原先没有稳定的 session 提示 | 当前 ProviderRequestHeaders 对特定订阅及 opencode.ai 管理会话头，未发现 OpenRouter 原生稳定 session 参数自动接入 | 两边都不照搬，按 OpenRouter 官方 session_id 接入；用既有 wire scope 生成匿名固定值，不建新数据库。只匹配官方 HTTPS host，不给未知中转塞字段 |
| OpenRouter Claude | 原先 Chat／Responses 仅走一般协议，不主动开启 Claude cache_control | 自定义 body 可手工配置；Chat 路径不自动套 Anthropic 原生标记 | 按官方路由合同，只在官方 OpenRouter 的 Claude 型号给自动 ephemeral 标记；不自动选择收费更高的长 TTL，不保活／预热 |
| OpenAI 当前协议 | 原生 Responses、store=false、受限型号 configuration_update 保留早期 effort | 自建 Responses input，store=false，移除 previous_response_id，原生临时项跨运行不保留 | 保留 Aurora 原生历史。当前官方文档说明 store=false 默认回传 encrypted reasoning，不按过时示例认定缺 include 就必然失败；新缓存字段按模型代际而非全局硬塞 |
| usage 和命中显示 | input/read/write 分开，未知保留 null；原先 Chat 缺若干兼容字段 | Chat 兼容读取字段更广；Anthropic 合并普通输入／读／写 | 保留 Aurora 计量边界，补兼容读取。字段存在不证明服务器缓存行为正确，不能把“未报告”显示成 0% 或命中 |
| HTTP 和失败 | OkHttp，禁止重定向与自动网络重试；不完整终态不执行工具 | OkHttp 模型流也关重试，普通请求和模型共享连接池，更多专用认证分支 | 不为选型而换库；保留 Aurora 明确失败边界。不可用时不暗中重放模型或切换供应商制造费用和冷缓存 |
| 存储／凭据 | SQLite 活动与恢复记录，Android KeyStore 凭据库，原始历史分区 | Room v25 等业务表，独立 KeyStore AES-GCM OAuth 凭据存储 | 没有同负载证据证明换 Room 或数据库提升命中，保留现有 SQLite／KeyStore；不把 Eta OAuth 凭据实现推广成全部 API Key 的结论 |
| Root／后台任务 | Binder 身份、guard／journal／cgroup、活动账本、unknown 删除屏障，有主包专项证据 | DetachedTaskSupervisor 使用 JSON 记录、setsid、PID＋ownership token 认领；另有 Android Runtime／checkpoint/outbox | 保留 Aurora 已验证的受管内核；不把两边整体任务系统按这一文件简单排名，未跑 Eta 故障矩阵，不声称其一定不可靠 |
| 功能覆盖 | 强项是受管执行、Frida、PCAP、TLS／逆向、手机构建 | GUI／数字助理／厂商个人上下文、语音、Gemini、MCP、订阅认证和压缩等范围更广 | 是互补范围；本轮不把缺少能力标成 UI 缺陷，也不为界面整体替换已完成后端。新增接入仍按独立方案实施 |
| 长上下文压缩 | 没有本轮新增自动付费摘要机制 | AgentContextSession 在实际输入接近85%窗口时尝试压缩，记录上下文快照 | 不能单看命中率选“不压缩”；应比较总成本、质量及故障恢复。自动付费摘要或服务端压缩需另做资格，不在本轮暗中开启 |
| UI／操作流程 | 同组件来源，但前期排版自行设计，连接与模型挤一页 | ProviderDetail 分连接配置／模型，字段分组、标签、清晰导航 | 采用 Eta 已确定的页面和交互；沿用 Aurora 已资格的 COUI alpha9，Eta 当前 alpha7 不构成降级依赖的理由；先统一主流程，再集中验收 |

## 关于“Eta 缓存低”的结论边界

可从源码提出的风险包括：跨 run 重建原生历史、剥离较早 thinking、Root／目录／系统快照变化、手工中转未开启缓存和供应商路由变化。不能仅凭源码证明用户这次低命中由其中哪项导致。TTL、最小可缓存长度、账号／区域、模型实际路由、上游是否报告 usage 都需要实际证据。

实际比较必须分开：原始前缀是否复用、服务端 cache read token 占比、cache write token 和费用、首 token 延迟、整次任务总输入与总成本。压缩后 token 少而比例下降可能仍更省；没有 usage 的中转保留未知。不增加无意义的长提示凑阈值，不循环请求刷热缓存。

## 官方来源与第三方案

- [OpenAI prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)：当前模型代际在断点、路由、TTL、写入计费等方面有差异，不能所有模型通用一个配置。
- [OpenAI 保持前缀](https://developers.openai.com/api/docs/guides/preserved-reasoning)与[reasoning](https://developers.openai.com/api/docs/guides/reasoning)：保持原始顺序、追加新项、保留原生 reasoning；stateless 默认 encrypted_content。
- [OpenAI cache diagnostics](https://developers.openai.com/api/docs/guides/prompt-caching/diagnostics)：服务端比较原因与 usage 分开，comparison_response_id 不会加载历史或保证命中。
- [Anthropic prompt caching](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)与[preserved thinking](https://platform.claude.com/docs/en/build-with-claude/preserved-thinking)：tools→system→messages，默认5m，断点、阈值和回看窗口按合同处理。
- [OpenRouter prompt caching](https://openrouter.ai/docs/guides/best-practices/prompt-caching)：稳定 session_id、sticky routing、Claude 显式／自动缓存及 Responses 字段转换。
- [OpenCode 官方模型说明](https://github.com/anomalyco/opencode/blob/dev/services/site/src/docs/content/models.mdx)：同样采用已知路由上的工具／system／消息缓存布局；支持可配置TTL，但这是实现参照，不是所有中转可用或全球最优的证明。不引入它的 JS runtime／整套 AI SDK。
- [Gemini context caching](https://ai.google.dev/gemini-api/docs/caching)：本次页面抓取被重定向／内容类型阻断；仅搜索结果不足以完成字段合同资格，不据此接入 Gemini 或新增显式缓存存储。

## 本轮已落地与验证

已实现：Anthropic 工具目录断点、排除空／thinking／未知标记目标，官方 OpenRouter 原生 scope 匿名稳定路由、Claude 自动 ephemeral，以及兼容 usage 字段读取；无新增依赖／表／守护进程。UI 按 Eta 做连接配置／模型分栏，并在切换分栏时保留目录状态、取消隐藏页读取。编辑目录保留当前实际模型，仅主动移除时才改选，避免无关目录操作重置缓存范围。原生 COUI 菜单、搜索、分栏，统一输入表面、对话操作、文件与上传入口继续采用同一批准依赖，无数据库或版本迁移。

宿主核心378项：362通过、16跳过、零失败；新增7项缓存合同测试和1项目录编辑测试。Debug／默认R8 Release／AndroidTest编译／双lint224任务52秒通过；最终文件／上传来源修正后的224任务51秒通过，发现378个注解方法／0非void；Debug原签名与Frida worker字节独立核对通过。新测试首次类型推断错误已修正后全量核心重跑，不把未运行的测试算通过。

尚未真机验证：新分栏与原生 COUI ChipTabRow 的视觉／输入／明暗／返回及布局。用户决定本轮全部准备好再集中验收，手机不逐页占用。真实付费请求和命中率未测，当前不宣称缓存命中更高或界面全部等同 Eta。

## 集中验收标准

1. 先离线验证同scope逐轮请求的历史原序前缀、跨应用重启的原生历史、模型／密钥／端点变化正确另起scope，UI命名／列表变化不改prompt。
2. UI按Eta连接、模型、对话、设置、文件等主要路径集中比对；不把现有后端功能隐藏成Eta缺少的入口，也不添加旧UI兼容分支。
3. 用户配置密钥并授权付费后，固定型号、端点、账号、effort，跑连续对话＋工具循环及一次重开App；记录实际usage、路由、延迟与费用。再做一项一变的对照，不根据一次命中宣布胜负。
4. 同批保留 unknown／失败／取消和真实来源撤权缺口；正式签名与完整发布包另绑最终字节，不用未签名预览冒充正式发布。

调用链复核：AgentExecution中的state来自ChatController每任务独立runProjection，不是当前浏览的界面state。曾据变量名提出“切换会话导致串scope”假设，实际调用方与既有AgentTaskTest排除了该假设；相应防御性重写已撤回，不把已实现的运行隔离重复建设，也不将其列为修复成果。唯一保留的运行接入为把当前原生scope传入请求路由。

## Skills 与凭据补充

Skills 主页面只保留 ZIP 导入、从链接添加、已安装列表与开关。私有来源／签名导入／旧版清理放入高级设置，历史版本及来源证据在详情中展开；SKILL.md 展开时再读取。不改变已有 Agent 按需加载、来源限制、公钥信任、哈希与租约检查。签名导入的材料在界面重建后丢失时明确拒绝，不能默默降级为普通导入。

Eta 的 AgentSkillsScreen 普通流程也是导入 ZIP 与开关列表；[Claude Code Skills](https://code.claude.com/docs/en/skills)采用 SKILL.md 及支持文件、按需加载，[Claude Skills 说明](https://support.claude.com/en/articles/12512176-what-are-skills)将添加／管理置于常用路径。这里学习其渐进展示，不声称它们使用 Aurora 的验签策略。高级管理不提高缓存命中率，也不额外授予系统权限。

凭据复核必须区分种类：Eta 的 ProviderEntities.api_key、ProviderConfigDraft 的保存状态和 RuntimeConfigRepository 的 API_KEY JSON 路径传递 API key 字符串；OAuth 另有 KeyStore AES-GCM 存储。这里只描述这些源码路径，不推断系统文件加密失效或设备已泄漏。Aurora 保留现有凭据库，输入中的新秘密只在内存，不写入保存的界面状态；编辑留空继续保留原凭据修订。

最终产品 `209be5e1742bce3556f0c24d5a816ddcf96342ed` 的[完整CI42886297026](https://github.com/Asagnc/Aurora/actions/runs/42886297026)五个任务全部成功。结果绑定该产品，不延伸为新界面真机或真实命中率资格；精简证据见 evidence/0.1.40/ui-eta-host-20261111.json。
