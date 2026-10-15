# 实现与验证证据

2026-10-15。目标见 DESIGN.md，未完成范围见 PROGRESS.md。没有性能领先或最高缓存命中率的实测结论。

## 当前应用

0.1.1 原生界面按确认预览重做：居中标题、白色双层圆角无下划线输入框、上传/思考/模型/发送控件和独立底部指标；抽屉搜索、置顶/最近分组、右上多选与三栏页脚；模型与供应商使用底部面板，包含有来源的本地品牌矢量。搜索查询数据库而非仅过滤已加载的一页；百分号与下划线按文字匹配。初次导入附件创建会话时保留草稿，草稿支持配置变化恢复；主动切换会话时清空当前草稿。

支持官方/中转 HTTPS 地址、模型 ID、三种协议和认证方式，保存连接与加密密钥。抽屉保留打开、长按/菜单、重命名、置顶、多选、批量删除与分页；消息支持分页。共享空间浏览最多列举 200 项，文本预览有 64 KiB 上限。Skills 页脚明确打开只读共享资源浏览，安装和运行器尚未实现。

同一运行轮次固定连接与凭据，变更用于下一轮，不重新执行当前工具。配置切换按新范围、已知失效、支持追加更新和未知中转分别解释。token/s 是最后请求输出 tokens 除以整轮耗时的端到端估算，包含工具时间，不是纯生成速度。十家官方预设与稳定型号已按 2026-10-12 官方资料核查；账户实际权限与动态能力校验未完成。型号来源见 PROVIDERS.md，图标来源见 ASSETS.md。

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
- 0.1.1 新增搜索回归后，模拟器普通测试 9 项通过；新 UI 尚未完成最终真机验收，按最新要求暂不执行真机测试。
- debug 与 R8/resource-shrunk release 构建通过；release 未正式签名。
- lint 无错误；保留 MonochromeLauncherIcon 和一项 MiMo 复杂矢量路径警告。品牌路径未为消除警告而删减形状，也没有据此宣称渲染性能已优化。两次单色衍生图不合格，未采用。
- 界面已实际启动与检查。无真实 API 密钥，未验证真实服务计费、缓存命中或模型效果。
- Android 16 ARM64 真机开发 APK 安装、启动成功。未证明 Root、手机 APK/NDK 编译或性能领先。
- [GitHub Linux CI](https://github.com/Asagnc/Aurora/actions/runs/38269980475) 对 f70ac51 通过核心测试、debug/release 构建和 lint，并保存开发构建文件。
- 模拟器界面操作已验证创建、重命名、置顶、多选和两个会话批量删除；验收用会话随后清理。

## 构建与已处理陷阱

固定 Gradle 9.8.1、AGP 9.4.1、Kotlin 2.4.21、JDK 25、SDK platforms;android-37.0、Build Tools 36.0.0。最低 SDK 36。

同一仓库避免同时运行两个配置缓存构建。Android 平台 AtomicFile 无 exists；用 openRead 支持备份恢复。测试进程启动失败曾产生零测试 XML 而构建返回成功；默认 verify-emulator.ps1 只操作明确指定的实际模拟器、只覆盖安装、不卸载，且强制至少九项通过。Gradle connectedDebugAndroidTest 曾在收尾卸载手机应用，不再作为连接日常手机时的默认工作流。资源目录移动曾导致 AGP 增量遗漏 launcher；清理 app 构建输出后完整构建通过。

真机暴露 Files.readString/writeString 在基础 Android 16 不存在，它们在 36.1 才加入；文件恢复与设备测试使用基础版本可用的字节 API。JVM 模块中的 API 调用不能仅靠新平台模拟器和 app lint 判定可用性，必须在最低支持平台执行核心路径。[官方 API 版本](https://developer.android.com/reference/java/nio/file/Files)。

其他精确依赖版本在构建文件内，来自 Google Maven/Maven Central/Gradle 官方接口/libsu Releases，排除 alpha、beta、RC、preview。构建组合验收不等于服务能力或真机性能验收。
