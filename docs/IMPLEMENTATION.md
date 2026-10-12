# 实现证据

更新日期：2026-10-12。这里记录实际行为，不代替完整设计或性能测量。

## 协议核心

`core` 是独立 Kotlin/JVM 模块。实现 Chat Completions、Responses、Anthropic Messages 的请求编码、SSE 分片组装、原生回复状态和工具循环。只有完整终态才接受工具调用；取消向上传播；没有自动重放工具。原生 Responses 输出项、Claude 思考签名、Chat reasoning_content 保留在连接和模型作用域内。

缓存实现包括稳定工具顺序、Anthropic 系统和最新内容断点、供应商实际用量解析及配置影响分类。GPT-6 append-only configuration_update、完整模型能力目录、网关探测和界面切换确认尚未完成。不能据此宣称最高缓存命中率。

## 文件核心

`OwnedFiles` 提供每批最多 64 项、最多 4 个并发读取，单次读取最多 64 KiB，保留输入顺序和逐项错误。UTF-8 片段不能完整解码时返回原始字节，不把二进制伪装成文本。超过 2 MiB 的文件可按范围读取，但当前不提供完整内容版本摘要或编辑。

批量编辑以完整内容 SHA-256 校验版本；创建要求目标不存在。每文件最多 2 MiB，每批最多 16 MiB。所有目标预检后，写入原文件备份、新文件和恢复日志，再逐文件原子替换。普通文件系统只保证单文件原子替换；组操作通过日志恢复。中断后未提交组回滚，已提交组清理日志，恢复碰到其他修改时停止并保留证据。

所有路径必须属于指定私有根目录；禁止逃逸、链接、内部日志路径、规范化后的重复目标和父子目标冲突。共享资源模式拒绝编辑。这不是任意 Root 程序的安全隔离。文件内容使用 force 同步；目录元数据的断电持久性及 Android 文件系统行为仍需设备验证。当前尚未接入会话删除和挂载清理。

## 已执行验证

- Gradle 9.8.1 + AGP 9.4.1 + Kotlin 2.4.21：`:core:compileKotlin` 通过。
- `:core:test`：16 项测试通过，覆盖 SSE 边界、断流拒绝、参数分片、签名/原生状态保留、取消传播、缓存分类、文件冲突、路径边界及中断恢复。
- 本机未连接 ADB 手机，没有 Root、ARM64 工具链或性能实测结果。

Android 应用当前是构建配置；还没有可运行界面或可交付 APK。完整待办见 [PROGRESS.md](PROGRESS.md)。

## 构建版本依据

版本固定在构建文件中，排除了 alpha、beta、RC 和 preview。版本来源为 Google Maven、Maven Central、Gradle 官方当前版本接口及 libsu 官方 Releases，检索于 2026-10-12。

| 项目 | 稳定版本 |
| --- | --- |
| Gradle | 9.8.1 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Material 3 | 1.4.0 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Coroutines / Serialization | 1.11.0 / 1.11.0 |
| OkHttp | 5.5.0 |
| AndroidX WebKit | 1.17.1 |
| libsu | 6.0.0 |

整个 Android 组合的 debug/release/lint 尚未通过，因此上表不是已验证的 Android 发布组合。Java 25 字节码与 Android 工具链兼容性也属于下一项构建验收，不能由 JVM 核心编译结果推断。
