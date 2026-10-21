# 阅读、任务详情与图片（2026-10-21）

状态：用户认可先完善阅读与任务详情；Markdown/图片新增底层方案仍待确认。用户补充要求 Agent 后续能够发送图片，因此不将“不自动加载远程图片”误写成“不支持图片”。下述库尚未加入构建。

## 方案比较

| 方案 | 优点 | 需要承担的工作/限制 | 判断 |
| --- | --- | --- | --- |
| multiplatform-markdown-renderer | 原生 Compose、Material3、GFM、组件覆盖、异步解析、明确的增量流式接口、Coil3 接口 | 图片来源与保存、查看器、消息模型和预算仍由 Aurora 实现；长内容要按块虚拟化 | 推荐正文渲染候选 |
| Enriched Markdown Android | md4c 解析、图片/表格、选择、可选公式与高亮 | Compose 入口内部是 AndroidView；已查文档没有同等明确的增量尾部接口；JNI/Android16 ABI、选择与滚动需验证 | 有价值的对照候选，未实测不能宣称更慢 |
| Markwon | 原生 TextView，成熟插件和图片生态 | 现有 Compose 页面要桥接 View；流式、结果卡片与状态衔接需额外集成 | 可用，但不是当前首选 |
| CommonMark + 自建 Compose 渲染 | 可完全控制结构和布局 | Aurora 自行承担嵌套列表、表格、流式未闭合语法、选择、图片排版与回归成本 | 不优先自写完整渲染器 |
| Markdown 转 HTML + WebView | 网页公式、HTML和交互内容生态丰富 | 引入另一套正文生命周期、测量、触控和脚本边界 | 正文不选；浏览器仍使用既定 WebView |

GitHub官方API再次核查：renderer最新稳定release为v0.46.0（10月13日），搜索缓存仍显示0.45.0；Coil为3.6.3。Enriched仓库v1.1.1是另一发布线，不能当Android独立包版本；Maven Central的`com.swmansion.enriched.markdown:compose`最新release为0.3.0。实施时核对Maven兼容并固定确切版本，不仅照抄README旧示例。以上没有真机同负载性能比较。

## 推荐组合，待确认

1. 原生 Compose Markdown 组件负责文字、列表、表格、代码、链接及图片占位；解析在后台，流式只追加变化，不反复解析全部历史。
2. 单一共享 Coil3 ImageLoader 负责解码、请求取消与有界缩略图缓存，使用屏幕约束选择尺寸；长列表不用每图创建loader，不默认解码原始大图。
3. 独立会话产物模型负责 Agent 工具生成图、截图、图表、本地导入与下载文件。消息引用产物ID/相对位置、MIME、尺寸和说明，不把大段base64存入正文。模型厂商原生返回另行适配为同类产物，不伪造供应商图片能力。
4. 原图是会话文件，缩略图是可再生缓存；删除会话时协调取消解码、释放读取和缓存失效，不清理共享依赖。持久保留/导出使用显式目的地。
5. 图片卡片可点开全屏缩放、保存/分享，失败可重试；支持 Markdown 图片和结构化图片产物共用管线。远程图片需定义会话下载、代理/认证与过期策略；不能复用模型密钥作为任意图片请求头。
6. 图片展示、Agent 发图、用户发图给视觉模型、模型厂商生成图片是不同能力。展示图片不自动将原图重复加入每轮模型输入；协议历史仍按原生内容保留。
7. 验收覆盖长回复、未闭合代码/链接、宽表格、中文大字号、滚动暂停/恢复、图片混排跳动、超大图、多图、坏图、取消/切会话、删除/重开与离线。性能对照记录解析时间、帧耗时和内存峰值，不能预先保证某库“最优”。

## 本轮已实现的独立部分

0.1.11：消息复制；按会话保留的自动跟随状态，拖动暂停、回到最新恢复；稳定流式行标识；工具结果字段顺序无关的错误/退出状态；后台解析的有界详情；命令stdout/stderr、退出码、复制及到会话产物目录的入口。原始结果仍可展开，预览截断明确。未接入Markdown/图片、PTY或持续日志，也未增加数据库架构。

新增核心用例覆盖字段顺序、异常/超预算结果、输出截断、产物路径和未知终态。真机/模拟器当前均未连接，不将编译通过当作UI验收。保留用户认可B输入框。

本地验证：核心50项通过；debug、R8 release、AndroidTest构建通过；lint为0错误、既有MiMo VectorPath警告1项。没有新增渲染库、图片网络请求或真实模型请求。构建记录位于任务outputs/reading-build-20261021.txt。

## 一手来源

- [Compose渲染库固定版说明与流式接口](https://github.com/mikepenz/multiplatform-markdown-renderer/blob/v0.46.0/README.md)
- [Enriched Android独立包版本元数据](https://repo.maven.apache.org/maven2/com/swmansion/enriched/markdown/compose/maven-metadata.xml)
- [渲染库发布记录](https://github.com/mikepenz/multiplatform-markdown-renderer/releases)
- [Enriched Android说明](https://github.com/software-mansion/enriched-markdown/blob/main/packages/enriched-markdown-android/README.md)
- [Markwon](https://github.com/noties/Markwon)
- [CommonMark Java](https://github.com/commonmark/commonmark-java)
- [Coil Compose尺寸选择与列表说明](https://coil-kt.github.io/coil/compose/)
- [Coil共享ImageLoader与缓存](https://coil-kt.github.io/coil/image_loaders/)
- [Android Lazy列表与滚动](https://developer.android.com/develop/ui/compose/lists)
