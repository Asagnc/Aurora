# 实现与验证证据

## 0.1.10（2026-10-21）

按用户确认实现本地命令有界CPU唤醒范围；普通执行use收尾，Root在应用侧持有至结果/取消清理后finally释放，UID0服务不重复取得。没有新增依赖、整轮Agent持锁或设备策略修改。见[COMMAND_POWER.md](COMMAND_POWER.md)。真实ARM64普通41、浏览器恢复1、Root4、亮/熄屏2通过，核心45重跑；构建/lint通过，0错误/既有1警告。失败探索记录与策略调整条件保留，不能单独归因于锁；模拟器未完成。证据见[DEVICE_ACCEPTANCE_2026-10-21.md](DEVICE_ACCEPTANCE_2026-10-21.md)。

## 页面审计与状态文案（2026-10-21）

实际点击对比与边界见 [UI_AUDIT_2026-10-21.md](UI_AUDIT_2026-10-21.md)。纠正 ExecutionStatusPage 将已实现后台Agent误列为未接入的旧文案；后台前台服务和未实现交互式终端分别显示，未新增执行逻辑。该小范围显示修正构建与模拟器实际页面核对记录见 evidence/ui-audit-20261021/README.md，不重复扩大为底座回归通过结论。用户同意最后补齐 DSHA 受阻 ARM64 页面。

## 0.1.9（2026-10-20）

已确认的后台Agent架构落地：原生specialUse前台服务、通知停止/返回会话、独立运行投影、SQLite非破坏升级及任务记录。切换会话不取消、不串流，进程死亡只标记中断/未知，不重放工具。实现、验收边界见 BACKGROUND_TASKS.md；本轮新版真机后台/Root联合测试仍待统一验收。

## 0.1.6（2026-10-16）

普通原生命令接入真实 Agent 工具循环。独立原生监督程序负责信号、子进程组、两路输出预算和原子结果元数据；Kotlin 传递 argv、协调取消、返回退出码/产物与不确定状态。无新增运行库，不给普通 UID 自动提权，不默认经过 PRoot。详见 NATIVE_COMMANDS.md。

核心 45 项；模拟器普通测试扩为 26 项，另进程恢复 1 项通过。新增 8 项覆盖真实 native ELF 调用、双路各 9 MiB 输出预算、取消/超时/父退出和异常监督程序、参数/环境/UTF-8、AgentLoop 实际文件产物。ARM64 与 x86_64 均编译；ARM64 运行、Root 执行、实际供应商与性能基准未验收。FEATURE_STATUS.md 为当前能力/缺口索引。

## 0.1.5（2026-10-16）

认可的交互预览已落地，唯一指定图标修订为灯泡。输入区不再嵌套模型边框，指标改为原位展开，主菜单统一底部面板；空白/消息/执行步骤复用同一套正式组件。debug 专用 ChatReviewActivity 提供明确标注的内存示例，release manifest 不含该 Activity。没有新增执行后端或伪造任务结果。

扩展主题对比度用例覆盖输入区表面；极端颜色失败已修正，模拟器 18 项和独立进程恢复 1 项通过，核心 45 项通过。debug/release/lint 构建通过。真机和真实模型待后续，用户本轮明确要求先做好模拟器。证据与截图见 CHAT_REFINEMENT_2026-10-16.md。

## 0.1.4（2026-10-16）

用户确认的外置指标布局已实现；模型/思考控制压缩，无连接模型页显示添加入口。供应商编辑支持返回列表。执行记录使用动作名与准确状态，可展开调用 ID 和有界格式化预览。ToolRunStore 持久化后发送开始/完成事件，界面保留最近 50 项及最多 8001 字符结果，避免逐步查询历史数量。完整结果仍存数据库；取消仍不伪造完成。

核心 45 项、模拟器普通 18 项与独立进程恢复 1 项通过；持久化测试新增事件顺序、预览截断而原结果完整断言。debug/release/lint 通过，仍有既有资源警告。实际页面对比与后续缺口见 UI_AUDIT_2026-10-16.md。当前仅连接模拟器，0.1.4 真机、真实模型和缓存基准待验收。

2026-10-15。目标见 DESIGN.md，未完成范围见 PROGRESS.md。没有性能领先或最高缓存命中率的实测结论。

## 0.1.3 当前状态（优先于下面的历史记录）

已从手动浏览器接入 Agent：BrowserToolExecutor 使用 BrowserAutomation 在 Main dispatcher 运行真实会话 WebView；ToolRouter 固定每轮定义。提供 8 个浏览器工具，包括批量开页/读页、点击/填写/滚动和同步 JS JSON 返回。页面 ID 限定会话所有权，修改/脚本要求最新 URL 和导航 revision。文本/元素列表、脚本长度和返回值都有预算与显式截断；没有新增浏览器依赖或原生 JS Bridge。网页属于不可信观察数据。

用户可打开同一组页面观看；Agent lease 防止关闭观看页时中止正在执行的操作，操作结束且无人观看时暂停页面。全局 8 页、批量并发 2；取消会停止导航，超时返回结果待核实提示，不自动重放。同步 JS 运行超时不能保证终止已经进入 renderer 的 JavaScript，因此不宣称硬 CPU 限额或任意脚本可控；异步 Promise 明确拒绝。

工具账本查询拒绝已有 call_id；核心循环也拒绝同一轮跨响应重用 ID。运行记录在对话内折叠查看，只读最近 50 项和每项最多 8000 字预览。完成账本不等于业务成功，错误单独显示。多轮 usage 总计缺失字段保持未知；速度用整轮累计输出除以整轮耗时，缓存/上下文仍对应最近一次请求。

新界面按已确认方向采用单层 24dp 输入框、轻量三指标、输入框上方定位的小面板、无助手头像气泡、无抽屉新建按钮；右上保留新会话/文件/浏览器。主题完整映射浅深角色，预设/系统/自定义、HSV 与六位颜色值、柔和/鲜明/单色、纯黑和即时预览；无壁纸。旧偏好保留，默认青岚且无彩色功能图标。方案沿用 Compose，不复制 GPL 项目代码。

验证：核心 45 项；模拟器与 realme Android 16 ARM64 各 18 项普通设备测试，另进程恢复 1 项。Agent 浏览器为本地夹具上的确定性模型循环，不是付费供应商实测；真实 API、缓存/费用/网站成功率和后台持久执行未验收。Root 仍限 0.1.2 已验证的只读握手；没有新增 Root 执行工具。上传下载、异步脚本、跨站 iframe、完整 renderer 崩溃矩阵仍未完成。

BrowserRecoveryTest 必须独立进程执行；新建 Profile 的测试会留下待下一进程清理的空元数据，不能在同一批测试末尾把它们判成启动恢复失败。verify-emulator.ps1 显式分组并检查真实测试数量。

来源：[WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback))、[ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore)。

## 历史 0.1.2 实现说明


0.1.2 按新确认的 A 方案改为胶囊式输入框：灯泡思考按钮、模型家族图标与三项指标位于同一外框，单行胶囊、多行自然增高。右上菜单进入新会话、本会话文件与浏览器。抽屉底部为紧凑彩色横排入口；模型选择支持全部/官方/中转过滤，每行保留家族图标。外观支持五套预设、自定义强调色、浅色/深色/系统模式、彩色图标开关与系统动态配色；配置本地保存。字体大小跟随系统。当前会话文件入口不混入共享目录；会话文件浏览和预览也归属会话任务，删除时取消并等待。抽屉返回键先收起抽屉，不直接离开主界面。

会话搜索查询数据库而非仅过滤已加载的一页；百分号与下划线按文字匹配。初次导入附件创建会话时保留草稿，草稿支持配置变化恢复；主动切换会话时清空当前草稿。

支持官方/中转 HTTPS 地址、模型 ID、三种协议和认证方式，保存连接与加密密钥。抽屉保留打开、长按/菜单、重命名、置顶、多选、批量删除与分页；消息支持分页。共享空间浏览最多列举 200 项，文本预览有 64 KiB 上限。Skills 页脚明确打开只读共享资源浏览，安装和运行器尚未实现。

同一运行轮次固定连接与凭据，变更用于下一轮，不重新执行当前工具。配置切换按新范围、已知失效、支持追加更新和未知中转分别解释。token/s 是最后请求输出 tokens 除以整轮耗时的端到端估算，包含工具时间，不是纯生成速度。十家官方预设与稳定型号已按 2026-10-12 官方资料核查；账户实际权限与动态能力校验未完成。型号来源见 PROVIDERS.md，图标来源见 ASSETS.md。

## 手动浏览器

系统 WebView / AndroidX WebKit 1.17.1；当前是手动浏览，不是完整浏览器 Agent。HTTPS 导航、最多同时八页、页签切换、关闭/前进/后退/刷新已接入。按会话绑定 Profile；不支持 MULTI_PROFILE 或完整数据清理的 WebView 明确拒绝创建，要求更新系统 WebView，不混用默认空间。不提供特权 JS Bridge；文件/内容 URI 和混合明文内容禁用。浏览器退出暂停 WebView 计时器并停止加载，页面和 Profile 可在本进程保留；不宣称零内存开销。

代理入口位于浏览器内，默认跟随系统；支持直连和无认证 HTTP/HTTPS/SOCKS 代理。更改前需关闭所有页面。覆盖作用于 Aurora 的全部 WebView，不作用于模型 API 和其他 App。等待系统回调后才允许加载；超时/失败阻止继续打开页面，避免默默退回其他网络路径。系统 WebView 对本地地址的默认代理绕过规则仍存在。设备测试在本机开启 IPv4 回环代理并确认收到实际 HTTPS CONNECT 请求；没有把回调成功等同于路由成功。

删除先写入 SQLite deleting 标记，再停止所属页面。实际测试证明 Chromium 可在 WebView.destroy 后仍保留加载过的 Profile：直接 deleteProfile 会抛 in-use。实现先等待 WebStorageCompat.deleteBrowsingData 清理缓存/Cookie/JS 存储，再删除会话文件；空 Profile 元数据在下次进程启动删除。清理中断/超时保留删除标记和文件，不报告成功。普通测试验证两会话 Cookie/localStorage 隔离、仅清理所属数据、禁止重开删除中的空间，并通过独立进程验证孤立元数据恢复。

上传/下载、模型端 JS/DOM、批量页面工具、完整 renderer 退出及代理网络故障矩阵尚未完成。已处理 onRenderProcessGone 时销毁对应页并提示重开，但尚无真实 renderer 崩溃注入验收。浏览器代理不等于全机抓包；抓包按最新确认只做 Root 原生后端，逆向工具候选和待办见 REVERSE_ENGINEERING.md。

来源：[Profile 删除限制](https://developer.android.com/reference/androidx/webkit/ProfileStore)、[浏览数据清理](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)、[代理范围与回调](https://developer.android.com/reference/androidx/webkit/ProxyController)。

## Root 连接边界

已实现 libsu 6.0.0 RootService/AIDL 的只读握手、手动连接与断开、超时和服务退出状态。入口不自动请求 Root；服务校验调用者 UID，只返回自身 UID/PID、SDK、内核和 SELinux 状态。客户端只接受 UID 0 的握手。使用普通绑定而非常驻 daemon，断开后关闭缓存 shell。系统操作工具、执行器和 Root 真机连接尚未验收，不记为已完成。

专用 RootConnectionTest 被 RootRequired 标记，默认普通测试排除；必须在已授权设备显式执行且传 auroraRootTest=true。普通存储测试通过不代表 Root 测试通过。[libsu 原始 API 与生命周期](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java)。

## 网络与协议

共享 OkHttp 连接池；HTTPS；禁止 URL 内凭据/查询参数；禁用重定向与连接失败自动重试。取消关闭 Call，阻塞读和编码在 IO 线程。错误不回显响应正文、密钥或原始异常。SSE 单行与累计轮次有内存预算。内部明文回环接口仅供测试夹具。

三种协议保留完整原生输出、Chat reasoning_content、Claude 签名/内容块和 Responses opaque 项。只执行完整终态；重复调用 ID 拒绝；Anthropic 多个结果合并为一个 user turn。稳定系统/工具前缀指纹、连接、模型、协议、地址和密钥修订约束历史复用。工具开始/完成账本持久化；取消传播，不自动重放动作。

官方 OpenAI GPT-6 Astra/6.1 Sol 的 Chat 工具请求提前拒绝并提示 Responses；GPT-6 Luna 的 Chat 工具需要 none。未知中转不被假定与官方路由完全相同。思考等级按核实的具体型号与协议开放，未知名称只提供 auto。官方核实的 GPT-6 Responses 模型变更显式等级时追加 configuration_update，顶层 effort 保持最初值，历史更新保留原位置；切回 auto、认证/密钥/前缀变化或中断时重建范围，保留完整事实和工具账本。网关能力探测尚未完成。

来源：[追加更新与手动历史限制](https://developers.openai.com/api/docs/guides/reasoning)。本地测试验证不出现相邻更新、数据库重开后保留原始/有效 effort，以及两轮工具调用只执行一次；没有付费服务缓存命中实测。

来源：[OpenAI 当前协议限制](https://developers.openai.com/api/docs/guides/latest-model)、[Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[OkHttp Call](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt)。

## 密钥、数据库与中断

Keystore 管理 AES-256-GCM 密钥。随机 IV、连接/修订认证附加数据，密文在 noBackupFilesDir 用 AtomicFile 发布；相同密钥保持修订。Credential 字符串脱敏，配置数据库不存密钥。云备份与 Android 设备迁移规则排除私有数据。

SQLite WAL 与外键；IO 线程操作，串行写事务。消息/协议记录分表追加，期望序号冲突拒绝。中断的 started 工具变 uncertain，已完成结果保留；切换/恢复时把结果作为执行事实供模型继续判断。突然进程终止仍可能丢失尚未保存的流式片段；后台持久服务未完成。

来源：[Keystore](https://developer.android.com/privacy-and-security/keystore)、[SQLite](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)、[AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)、[备份规则](https://developer.android.com/identity/data/autobackup)。

## 文件与空间

会话空间、只读共享资源、保留工作区；共享命名空间复用文件核心实例，保持同一写锁。导入副本每文件最多 64 MiB，不删除原件；不等同于原生图片/PDF 理解。

读取每批 64 项、4 并发、每片 64 KiB，保留顺序与错误。编辑每文件 2 MiB、每组 16 MiB，SHA-256 预检；禁止重复/父子目标、逃逸和链接。备份、日志、逐文件原子替换，未提交组恢复回滚，外部版本冲突停止并保留日志。目录元数据断电持久性需真机验证。

删除先停止并等待任务/导入，封闭新任务，再标记 deleting。遍历不跟随链接，检测当前命名空间子挂载并拒绝；删除中断可继续。测试验证共享链接不会删除共享文件。这不是任意 Root 程序的安全隔离；Root 挂载、浏览器和子进程尚未接入清理协调。

## 验收证据

- 核心 42 项测试，零失败：协议、取消、认证/路由、重定向、脱敏、GCM 篡改/身份替换、缓存范围/追加更新、文件冲突/恢复和任务清理。
- Android 16/API 36 ARM64 realme GT6 SE 真机、Android 17/API 37 模拟器各 8 项测试，零失败：数据库重开/分页、原生追加冲突、真实 Keystore、删除链接边界、删除恢复、工具中断账本、配置更新持久化和文件日志恢复/列举。
- 0.1.2 模拟器普通测试 14 项通过，另起进程的浏览器恢复检查 1 项通过；新 UI 尚未完成最终真机验收。历史真实手机结果仍为 8 项，不能用模拟器结果替代。
- debug 与 R8/resource-shrunk release 构建通过；release 未正式签名。
- lint 无错误；保留 MonochromeLauncherIcon 和一项 MiMo 复杂矢量路径警告。品牌路径未为消除警告而删减形状，也没有据此宣称渲染性能已优化。两次单色衍生图不合格，未采用。
- 界面已实际启动与检查。无真实 API 密钥，未验证真实服务计费、缓存命中或模型效果。
- Android 16 ARM64 真机开发 APK 安装、启动成功。未证明 Root、手机 APK/NDK 编译或性能领先。
- [GitHub Linux CI](https://github.com/Asagnc/Aurora/actions/runs/38269980475) 对 f70ac51 通过核心测试、debug/release 构建和 lint，并保存开发构建文件。
- 模拟器界面操作已验证创建、重命名、置顶、多选和两个会话批量删除；验收用会话随后清理。

## 构建与已处理陷阱

2026-10-15：0.1.1 在 API 37 模拟器检查实际主界面、会话抽屉和供应商面板；确认 Gemini 弧线路径恢复正确形状，键盘弹出后输入框、操作按钮与三项指标仍位于键盘上方。输入测试未发送模型请求。当前本地核心 42 项、模拟器 9 项通过，debug/release/lint 构建成功；lint 仍有单色启动图标和 MiMo 长路径两项警告。新版真机检查按用户要求放到最后。

固定 Gradle 9.8.1、AGP 9.4.1、Kotlin 2.4.21、JDK 25、SDK platforms;android-37.0、Build Tools 36.0.0。最低 SDK 36。

同一仓库避免同时运行两个配置缓存构建。Android 平台 AtomicFile 无 exists；用 openRead 支持备份恢复。测试进程启动失败曾产生零测试 XML 而构建返回成功；默认 verify-emulator.ps1 只操作明确指定的实际模拟器、只覆盖安装、不卸载，且强制至少九项通过。Gradle connectedDebugAndroidTest 曾在收尾卸载手机应用，不再作为连接日常手机时的默认工作流。资源目录移动曾导致 AGP 增量遗漏 launcher；清理 app 构建输出后完整构建通过。

真机暴露 Files.readString/writeString 在基础 Android 16 不存在，它们在 36.1 才加入；文件恢复与设备测试使用基础版本可用的字节 API。JVM 模块中的 API 调用不能仅靠新平台模拟器和 app lint 判定可用性，必须在最低支持平台执行核心路径。[官方 API 版本](https://developer.android.com/reference/java/nio/file/Files)。

其他精确依赖版本在构建文件内，来自 Google Maven/Maven Central/Gradle 官方接口/libsu Releases，排除 alpha、beta、RC、preview。构建组合验收不等于服务能力或真机性能验收。

2026-10-15 final device verification: Android 16 ARM64 realme GT6 SE passed 14 ordinary tests, one separate-process browser recovery test, and one explicitly authorized RootConnectionTest (read-only connect/disconnect/reconnect). FolkPatch authorization is configured inside its app. Packet capture, privileged operations and revocation remain unverified.

## 0.1.7 Root command interface (2026-10-17)

Supersedes the earlier read-only implementation status: RootService now exposes asynchronous start, completion callback, cancel-and-wait and close. Only an explicitly connected, UID-0/protocol-verified endpoint is included as command_execute_root at agent-run construction. Existing ordinary command execution remains separate; no retry/fallback after an uncertain result. UID checks cover each production method. Controlled supervisor artifacts use descriptor-based ownership restoration; arbitrary Root files/mounts remain outside automatic cleanup.

See ROOT_COMMANDS.md for lifecycle, wire budgets and limits. Seven ordinary-UID cross-process contract tests added; emulator 33 ordinary tests + 1 isolated browser recovery and core 45 pass. Debug and R8 release compile for ARM64/x86_64; lint has no errors and retains the existing MiMo VectorPath warning. Two RootRequired command tests compile but have not run. Android 16 Root execution/ownership/manager revocation still require hardware evidence; historical read-only FolkPatch evidence does not verify them.

## 0.1.8 capsule B and hardware validation (2026-10-18)

Supersedes pending hardware statements above: on Android16 ARM64 realme GT6SE, 33 ordinary tests, 1 isolated browser recovery and 4 explicitly authorized RootRequired tests pass. Existing FolkPatch grant remained available after install-r over0.1.3; app was not uninstalled/cleared. Root UID0, controlled artifact ownership/readability, direct cancellation/timeout, disconnect cleanup/reconnect are verified. Strong-killing the actual Root service, external manager revocation and arbitrary Root files/mounts remain unverified/unfinished.

The approved capsule B is implemented with thinking bulb, concrete model, inline context ring, external statistics and bounded in-place details. Execution history defaults collapsed, active titles align with actual tools and completion count is explicitly recent history. Existing themes preserved. Actual device/keyboard/narrow long-name views are separate from clearly labelled debug examples. Details and raw test reports: COMPOSER_B_2026-10-18.md and docs/evidence/0.1.8. Core45, emulator33+1 and debug/release/lint pass; no paid-provider or comparative performance evidence.

2026-10-20最终验证：core45、模拟器普通39、独立浏览器恢复1、Agent进程恢复prepare1/verify1通过；debug/AndroidTest/R8 release构建通过，lint0错误、1项既有MiMo矢量警告。序列化1.12.0升级后已复跑。证据：docs/evidence/0.1.9；新版真机后台/Root联合验收未做。
