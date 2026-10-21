# 阅读、任务详情与图片（2026-10-21）

状态：2026-10-21用户已确认推荐组合，并要求以实际适合Aurora为准。0.1.12已接入Compose Markdown0.46.0、Coil3.6.3和会话图片产物；不把“不继承模型认证下载图片”误写成“不支持图片”。设备验收仍待补齐。

## 方案比较

| 方案 | 优点 | 需要承担的工作/限制 | 判断 |
| --- | --- | --- | --- |
| multiplatform-markdown-renderer | 原生 Compose、Material3、GFM、组件覆盖、异步解析、明确的增量流式接口、Coil3 接口 | 图片来源与保存、查看器、消息模型和预算仍由 Aurora 实现；长内容要按块虚拟化 | 推荐正文渲染候选 |
| Enriched Markdown Android | md4c 解析、图片/表格、选择、可选公式与高亮 | Compose 入口内部是 AndroidView；已查文档没有同等明确的增量尾部接口；JNI/Android16 ABI、选择与滚动需验证 | 有价值的对照候选，未实测不能宣称更慢 |
| Markwon | 原生 TextView，成熟插件和图片生态 | 现有 Compose 页面要桥接 View；流式、结果卡片与状态衔接需额外集成 | 可用，但不是当前首选 |
| CommonMark + 自建 Compose 渲染 | 可完全控制结构和布局 | Aurora 自行承担嵌套列表、表格、流式未闭合语法、选择、图片排版与回归成本 | 不优先自写完整渲染器 |
| Markdown 转 HTML + WebView | 网页公式、HTML和交互内容生态丰富 | 引入另一套正文生命周期、测量、触控和脚本边界 | 正文不选；浏览器仍使用既定 WebView |

GitHub官方API再次核查：renderer最新稳定release为v0.46.0（10月13日），搜索缓存仍显示0.45.0；Coil为3.6.3。Enriched仓库v1.1.1是另一发布线，不能当Android独立包版本；Maven Central的`com.swmansion.enriched.markdown:compose`最新release为0.3.0。实施时核对Maven兼容并固定确切版本，不仅照抄README旧示例。以上没有真机同负载性能比较。

## 已确认组合

1. 原生 Compose Markdown 组件负责文字、列表、表格、代码、链接及图片占位；解析在后台，流式只追加变化，不反复解析全部历史。
2. 单一共享 Coil3 ImageLoader 负责解码、请求取消与有界缩略图缓存，使用屏幕约束选择尺寸；长列表不用每图创建loader，不默认解码原始大图。
3. 独立会话产物模型负责 Agent 工具生成图、截图、图表、本地导入与下载文件。消息引用产物ID/相对位置、MIME、尺寸和说明，不把大段base64存入正文。模型厂商原生返回另行适配为同类产物，不伪造供应商图片能力。
4. 原图是会话文件，缩略图是可再生缓存；删除会话时协调取消解码、释放读取和缓存失效，不清理共享依赖。持久保留/导出使用显式目的地。
5. 图片卡片可点开全屏缩放、保存/分享，失败可重试；支持 Markdown 图片和结构化图片产物共用管线。远程图片需定义会话下载、代理/认证与过期策略；不能复用模型密钥作为任意图片请求头。
6. 图片展示、Agent 发图、用户发图给视觉模型、模型厂商生成图片是不同能力。展示图片不自动将原图重复加入每轮模型输入；协议历史仍按原生内容保留。
7. 验收覆盖长回复、未闭合代码/链接、宽表格、中文大字号、滚动暂停/恢复、图片混排跳动、超大图、多图、坏图、取消/切会话、删除/重开与离线。性能对照记录解析时间、帧耗时和内存峰值，不能预先保证某库“最优”。

## 本轮已实现的独立部分

### 0.1.12 图文实现

- 助手正文支持GFM，代码块包含语言和复制；链接进入现有会话浏览器。用户原文保持文字显示。
- 流式使用增量解析，在Default线程追加；用不可变源文本与AST发布包装，避免UI读取后台StringBuilder。新模型请求文本重置时重建流式解析状态；流式引用式链接延后到完整正文解析，不承诺所有未闭合语法立即可见。
- 聊天内预览最多24000 UTF-16单元、4个图片标记，不切断代理对；超出可进入完整阅读页，按顶层Markdown块惰性渲染。图片标记预算为保守显示判断，代码中的同形标记也可能提早进入完整阅读；原文/协议/复制不截断。单个超长代码块和超大表格仍需设备压力验收。
- Agent新增images_publish：读取会话相对路径，或下载HTTPS图片，返回类型化图片记录、aurora-image:UUID与Markdown；不是模型生图API。详情可看图片，最终回复可引用。
- 原图/清单在会话image-artifacts目录，复制为独立快照，SHA-256校验、尺寸/MIME/字节记录；文件和清单分别force后原子发布。两文件不是联合原子事务；进程中断产生的未引用产物/partial随会话清理，暂未做主动孤儿回收。普通文件工具与Root程序不是安全沙箱，仍可改写其UID允许的内容；读取发现哈希变化会失败。
- 远程图片请求不携带模型认证，默认不自动重定向/重试，45秒总超时；最多2个图片存储操作并行，下载32MiB、64MP尺寸预算。当前以会话快照缓存同一URL；动态刷新、需要独立认证/Referer的图片、浏览器代理复用尚未接入，不保证任意网站图片可取。
- 共享ImageLoader内存缓存48MiB，关闭额外磁盘缓存；缩略图采样至1200px，大图查看采样至3072px，支持缩放/平移。不是任意大图原始像素的分块深度缩放。Android可解码光栅图片可发布；SVG/独立GIF动画插件、公式和语法高亮未接入。
- 保存通过系统创建文档；分享用不导出的FileProvider和单URI读取授权。导出在IO线程，失败明确可能留下不完整目标。下载、文件读取、解码和导出登记会话任务；删除前取消/等待，清理内存缓存，再清理会话。已导出/分享的用户副本不随会话删除。
- 核心新增9项已执行用例；新增5项Android设备用例已编译，涵盖快照独立/重开、跨会话、坏图/同长度篡改、工具/导出及删除后不重建，当前无连接设备，未计运行通过。真实模型发图、实际远程站点、分享目标App、手势/视觉/内存压力均待验收。

### 0.1.11 独立基础（历史）

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
