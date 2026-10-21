# 交互式终端选型提案

日期：2026-10-21。状态：用户已确认并进入0.1.14实现；本文件保留选型依据。实际行为与验收见INTERACTIVE_TERMINALS.md。

## 推荐

普通命令保留现有管道执行器。交互式程序按需建立原生 PTY，复用 Termux 独立 terminal-emulator / terminal-view 的稳定版本代码与许可证声明，嵌入 Aurora，不依赖安装 Termux、不引入整套 Termux 应用或软件仓库。先验证组件接入与线程适配，再发布完整能力；不能直接移植其进程管理作为 Aurora Root 后端。

GitHub releases/latest 实查最新非预发布为 v0.118.3（2025-05-22），不是从模块 build.gradle 的 0.118.0 发布字段推断。实施时固定该 tag 的完整提交与来源校验，逐文件检查许可证，保留归属及修改记录；后续更新经过兼容测试再升级，不追 master。

## 比较

| 候选 | 价值 | 本轮取舍 |
| --- | --- | --- |
| Termux 原生终端组件 | Android 输入、选择、终端模拟和原生绘制已有实现，两个独立模块有 Apache 2.0 例外说明 | 推荐按稳定 tag 复用，仍需逐文件审计与适配 |
| xterm.js + WebView | 完整浏览器终端生态，有编码、流控、序列文档 | 可行，但增加终端 WebView 与 JS 通道；Aurora 已采用原生主界面，本轮不选；没有实测证明它一定更慢 |
| libghostty-vt + JNI + Android 绘制 | 现代终端核心，C/Zig 可嵌入 | 官方当前明确 API 仍变化、尚无独立版本标签；另需 Android 渲染/输入集成，与只采用稳定版本的要求不符 |
| 自写 ANSI/VT 模拟器 | 可控制代码 | Unicode 宽字符、重排、备用屏、键盘协议和兼容性负担大，不推荐 |

## Aurora 接入边界

- Agent 得到 start/read/write/resize/stop/list 工具；会话 ID 与所属空间绑定，读支持序号游标与有界等待，返回退出/中断/截断状态。不是每次读取重传整个屏幕，也不由 UI 驱动执行。
- 普通与 Root 使用同一会话契约、不同进程启动权限；Root 复用现有非 daemon RootService，通过 Binder 控制和文件描述符传输数据，不逐字符 RPC。
- PTY 将标准输出和标准错误合并，不能伪造为现有命令的两路捕获。原始终端字节日志与 Agent 可读屏幕/文本分别提供；屏幕重绘不等于追加文本。
- 会话隶属会话空间，可跨页面和单次模型回复继续；有活动 PTY 时复用前台服务通知与停止入口。关闭页面不杀任务；删除空间先取消进程和关闭 FD，再删除文件。
- 进程/Root 服务死亡标记中断，不自动重放输入或宣称恢复原 shell。不引入常驻 Root 守护进程。后台继续不等于可绕过系统冻结。
- 输出事件驱动排空，有界队列/回滚区/磁盘预算，满额必须报告丢弃，不能因 UI 隐藏停止排空而卡死子进程。空闲提示符不常持 CPU 锁；活跃工作沿用已批准的有界 lease。
- 上游 TerminalSession 当前将模拟及回调放主线程，因此不能直接原样套用。接入先分离 I/O、进程管理与解析；昂贵解析在专用执行上下文，渲染读取一致状态，避免跨线程修改终端缓冲。终端 View 适配与线程所有权是需要验证的工作，不是已有保证。
- 自动剪贴板写入、URL 启动等终端副作用由 Aurora 显式处理，不把控制序列当用户操作。粘贴/发送 Ctrl-C、缩放、IME、中文和 emoji 都需专项。

初始预算建议：每空间最多 4 个活动终端、每个回滚 5000 行并设字节上限、原始日志 16 MiB；作为可见资源策略明确返回，不能静默丢数据。预算最终随设备压力测试调整，不当成性能极限数字。

## 验收门槛

独立测试真实 PTY 属性、中文 UTF-8 分片与宽字符、ANSI/备用屏、窗口变化、Ctrl-C/EOF、输入背压、大量输出与隐藏页面、退出码、超时/停止/删除、服务死亡与无自动重放。检查前台服务在 Agent 回复结束后的存续和最后一个终端关闭后的退出。

真机普通/Root 两条路径测试 FD/进程清理、亮屏/熄屏、内存及输出吞吐；对比现有管道执行，确保普通任务没有引入 PTY 开销。性能结论以实际测量为准。当前没有连接设备，不把历史 0.1.10 验收当终端证据。

## 主来源

- https://github.com/termux/termux-app/releases/tag/v0.118.3
- https://github.com/termux/termux-app/blob/master/LICENSE.md
- https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java
- https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/jni/termux.c
- https://github.com/ghostty-org/ghostty#cross-platform-libghostty-for-embeddable-terminals
- https://xtermjs.org/docs/

master 链接用于研究当前行为，实施依赖必须切换到固定稳定 tag/提交，不能据此声称稳定 tag 具备所有 master 功能。
