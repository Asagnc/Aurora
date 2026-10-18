# 原生命令执行底座 · 0.1.6

**0.1.8当前验收更新（2026-10-18）：Android16 ARM64 真机普通33项、独立恢复1项、Root专项4项通过，包括真实原生程序与UID0执行。** 下文0.1.6/0.1.7“ARM64仅编译/Root待测”段落为历史状态，不再表示当前后端状态。详见 COMPOSER_B_2026-10-18.md 和 ROOT_COMMANDS.md；外部撤权、任意Root文件/挂载、Root服务强杀仍未完整验证。

2026-10-16。沿用已确认 Kotlin/NDK、会话所有权和普通/Root 分离的架构。此版本新增普通 Android UID 的前台命令，不等于 Root 执行、完整 Linux、终端或插件系统。

0.1.7 补充：独立 Root 命令接口现已接入，详见 [ROOT_COMMANDS.md](ROOT_COMMANDS.md)，实际 UID 0 和归属恢复尚待真机测试。创建/等待监督程序现固定在同一阻塞 OS 线程，取消收尾后才回到线程池；环境总字节上限为 64 KiB。下文截图与 26 项测试数量为 0.1.6 历史证据，当前普通模拟器用例 33 项加独立恢复 1 项。

## 选型与实现

比较普通 ProcessBuilder、系统 setsid 包装和 APK 内原生监督程序。ProcessBuilder 用于启动，不独自承担进程树清理；系统 setsid 能建立会话，但不足以单独实现父线程死亡处理、等待/回收和输出预算。使用独立小型 C 程序监督一次命令，避免在 ART fork 子进程中长期保留运行时堆映射，也不在普通命令路径加 PRoot。

NDK 固定 30.0.16248370（r30 LTS），SDK 中的稳定 CMake 固定 4.1.2。构建 arm64-v8a 与 x86_64，16 KiB ELF 段对齐。可执行 ELF 通过 Gradle Variant API 作为生成的 native-library 资源打包，安装到不可写的 nativeLibraryDir；不从 writable app home 执行下载文件。只有编译打包，不向手机安装整个 NDK。

Kotlin 按原值传递 argv，不拼接 shell。需要 shell 语法时由 Agent 显式传 `/system/bin/sh -c ...`。环境默认不继承宿主变量，明确设置 PATH/HOME/TMPDIR；用户任务提供的环境单独传入。工作目录为会话内现有、无链接的相对目录；这不把任意命令变成文件系统沙箱。

监督程序创建工作进程组，通过 signalfd + poll 等待信号与两路输出，无持续定时轮询。取消与 parent-death 信号先向同组发送 TERM，最多留 250 ms 协作退出，然后 KILL 残留；正常结束也清理同组后台子进程。保持工作进程未回收直到组清理，避免 PID/group 复用窗口。随后 waitpid 回收，标准输出/错误分别落盘。默认两项并发，不为每路输出创建 Java 读取线程。

每路输出保存前 8 MiB，超出后继续排空并计数，防止死锁；预览默认 8192 字节，可配置 1024–65536。二进制不伪装为文字；截断跨 UTF-8 字符时保留完整字符。结果包含退出码、超时、捕获完整性、总读取字节、保存字节、预览/产物截断标记与相对路径。元数据先写临时文件并同步，再原子发布。监督程序/元数据异常返回 command_outcome_uncertain，界面显示结果待核实。

命令通过既有 Agent 工具循环、工具账本和 OwnedTasks 会话租约执行。会话取消/删除等待监督进程结束；进程或会话中断不会自动重放命令。新增工具定义会改变前缀指纹，旧范围不会被错误当成完全相同缓存前缀。

## 验收与实际限制

新增 8 个 Android 设备用例：参数与环境保真、非零退出；两路各 9 MiB 输出与 8 MiB 产物上限；超时同组清理（含忽略 TERM）；会话 quiesce 取消并等待（含 TERM trap 写入产物）；正常退出清理后台子进程；缺失程序/目录穿越/NUL/监督程序死亡；启动父进程退出后的清理；确定性模型通过 AgentLoop 调用并收到真实文件产物。补充 UTF-8 边界和二进制预览断言。不消耗真实 API。

[实际执行环境状态页](assets/native-016/execution-status.png)来自模拟器主应用设置，无示例数据。

模拟器 x86_64 上运行；ARM64 仅编译，尚未在本轮真机执行。没有性能领先、零开销、所有内核/ROM 等效或完整任意进程树隔离的结论。

PR_SET_PDEATHSIG 跟随创建者线程的死亡语义，不能描述为任意跨进程持久服务。当前前台命令的调用与等待保持在 IO 执行路径，应用死亡时监督程序也会停止；系统 freezer/Doze、Root 撤权与服务死亡还需后续专项验收。

独立 setsid/守护化、改变 UID、不可中断内核 IO 或主动终止监督程序不能由普通 UID 进程组完整约束。直接工作进程的 parent-death 设置和元数据完整性提供故障处理，不是任意逃逸程序的安全容器。Root/cgroup/持久后台服务另行设计与测试；当前不默认开启。

## 一手依据

- [Android Process](https://developer.android.com/reference/java/lang/Process)：输出管道不及时读取可阻塞；对象失去引用不代表进程结束。
- [ProcessBuilder](https://developer.android.com/reference/java/lang/ProcessBuilder)：参数、环境和重定向。
- [Android app home 执行限制](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)：使用 APK 打包代码。
- [NDK r30](https://developer.android.com/ndk/downloads)、[Google SDK 包元数据](https://dl.google.com/android/repository/repository2-3.xml)：核对并固定稳定版本。
- [AOSP Toybox](https://android.googlesource.com/platform/external/toybox/+/refs/heads/main/Android.bp)、[Termux 原生终端](https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/jni/termux.c)：对比系统进程包装和 PTY 场景；没有复制其实现。
- [signalfd](https://man7.org/linux/man-pages/man2/signalfd.2.html)、[PR_SET_PDEATHSIG](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)：Linux 接口及父线程语义。
