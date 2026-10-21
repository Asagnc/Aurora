# 0.1.14 本地与 CI 证据

- 最终实现：`0be35fc23d6fa8de2f2bf47e477b535517c29b19`。
- 完整CI：[39597157476](https://github.com/Asagnc/Aurora/actions/runs/39597157476)，success。
- 先前39596437466由新提交的concurrency策略取消，不是测试失败；其中核心/主机步骤已通过，最终提交又完整重跑。
- 本地核心：72测试，0失败、0错误。
- Linux主机：8项原生PTY测试通过，12个固定Java源码SHA256校验通过。
- 本地及CI：Debug、R8 Release、AndroidTest APK、lint通过；lint0错误、既有MiMo路径警告1。
- NDK ARM64/x86_64两个PTY PIE程序的LOAD段对齐均0x4000（16KiB），栈无执行标记；不等于16KiB设备实际运行验收。
- APK内确认两个ABI的libaurora_pty.so与Termux/Apache/MIT声明已打包。

本机产物：`outputs/Aurora-0.1.14-development.apk`（工作区外outputs目录）。SHA256：
`c7b9247a7dd7694aa3a22f3e205ec288f345a30fc2b4058da5f1228330de2f7a`。
APK不提交仓库；Actions另有构建产物。

新增Android终端专项9项、普通UID跨进程FD测试1项、显式RootRequired专项2项均只编译，未执行。普通设备门槛61用于未来验收，不代表已跑61项。

没有连接设备，没有升级手机现有0.1.10，没有真实供应商/计费/缓存调用，没有新Android视觉、输入法、Root、吞吐、内存峰值或省电实测。旧0.1.10的48项硬件结果不扩展到0.1.14。最终集中真机验收应覆盖新增图文/日志/PTY、Root协议及后台联合行为。

后续入口：docs/PROGRESS.md、docs/INTERACTIVE_TERMINALS.md。Skills版本化安装、Linux环境、逆向/抓包/编译链仍未实现。
