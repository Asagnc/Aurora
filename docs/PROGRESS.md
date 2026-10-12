# Aurora 接续记录

更新时间：2026-10-12。新对话先读本文件、AGENTS.md、docs/DESIGN.md、docs/IMPLEMENTATION.md，然后检查源码和 Git 状态。

## 已确认

Android 16+、Kotlin/Compose、RootService/Binder、系统 WebView、三类空间、缓存切换合同、批量文件合同、PRoot/chroot、手机 APK/JNI 编译、界面和图标均获确认。本轮新增确认：Android 原生 SQLite + WAL，消息和协议记录追加写入。

## 已实现

- [x] 三种协议网络请求、流式解析、原生状态保留、完整终态检查和工具循环。
- [x] HTTPS 地址与认证配置、连接池复用、取消、重定向阻止、错误脱敏。
- [x] Keystore AES-GCM 密钥保险库；连接与修订绑定；相同密钥保留修订。
- [x] SQLite WAL 会话、消息、原生历史、工具账本；分页、置顶、重命名、批量删除。
- [x] 三类私有空间、持久删除标记、恢复、链接不跟随、所属任务清理后删除。
- [x] 批量读取、SHA-256 校验、单文件原子替换与组恢复。
- [x] 真实 Compose 界面、底部输入框、文件导入、多选图标、指标详情和切换确认。
- [x] 彩色 adaptive launcher 图标、本地 Material Symbols 及来源记录。
- [x] 十家供应商官方预设、2026-10-12 核查的稳定型号、独立认证与地域提示；自定义中转保留。
- [x] 官方已核实 GPT-6 Responses 路由的 configuration_update；原始 effort 不改，更新追加保存，不重放工具。
- [x] debug/release 构建、lint 无错误、42 项核心测试；Android 16 ARM64 真机和 Android 17 模拟器各 8 项持久化测试通过。

## 尚未完成

- [ ] 型号目录动态刷新、账户可用性/网关能力校验、完整思考控制、真实品牌图标。当前目录有明确核查日期，不宣称永远最新。
- [ ] 网关能力探测及完整模型缓存策略。未知中转仍确认风险；显式等级切回 auto 开新范围。
- [ ] 精确输出速度、首字延迟、跨工具轮次统计。当前 token/s 是有说明的端到端估算。
- [ ] 图片/PDF 原生多模态、文件导出、大型二进制编辑。目前导入副本并供文件工具读取。
- [ ] Skills 管理/安装、版本校验和回滚。未放置假按钮。
- [ ] 多页浏览器、Profile、代理、JS、上传下载及 renderer 恢复。
- [ ] RootService、系统 API、Root 操作、撤权及子进程取消。
- [ ] PRoot/chroot、挂载隔离、清理和 Linux 插件。
- [ ] ARM64 原生 APK/NDK/JNI 工具链及手机构建验收。
- [ ] 无障碍/视觉操作及后台任务服务。
- [ ] 单色主题图标：两次衍生图未达到质量要求，未采用；lint 保留一项主题图标警告。
- [ ] 正式签名、真机完整运行/Root 验证、性能/耗电/内存/任务成功率和 Eta 对比。

## 环境与验收

- 本机 JDK 25、Gradle 9.8.1、AGP 9.4.1、Kotlin 2.4.21、SDK platforms;android-37.0、Build Tools 36.0.0。
- 已连接 realme GT6 SE，实际 SDK 36/arm64-v8a；安装、启动和 8 项存储/恢复测试通过。Android 17/API 37 模拟器同样通过。Root 尚未授权/调用。
- 手机目标：realme GT6 SE，7+ Gen 3，16/256，Android 16/realme UI 7.0，FolkPatch 115356/KernelPatch 0.13.9，LSPosed 2.2.1。
- 无真实模型 API 密钥；协议通过本地服务夹具验证，没有供应商计费调用或缓存性能结果。
- 验证命令：./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug :app:verifyDevicePersistence。
- 最后一项要求已连接设备，强制每份设备报告至少八项测试；零测试不会被接受。
- GitHub Actions 保存开发构建；release 未正式签名，debug APK 只是开发预览。
- f70ac51 的 Linux CI 已通过核心测试、Android debug/release/lint：[验证与构建文件](https://github.com/Asagnc/Aurora/actions/runs/38269980475)。

## 下一步

继续完成型号目录动态能力校验、真实模型运行验收，再按已批准方案接入 RootService、浏览器和执行后端。重大新选型继续通过当前主流一手来源确认；更新真实验收记录。真机发现 Files.readString/writeString 依赖 36.1，小版本风险已消除并补测文件恢复。

不要自动显示或上传密钥。原先聊天中暴露的 PAT 未使用；GitHub 身份为本机登录的 Asagnc。
