# 原生交互式终端

2026-10-21，用户确认 TERMINAL_PROPOSAL.md 后实现。版本 0.1.14。实现、编译与设备验收分开记录。

## 执行与 Agent 接口

普通批处理仍使用 command_execute / command_execute_root，不经过 PTY。新增 terminal_start/read/write/resize/stop/list 六个工具；按会话归属验证 ID。默认 /system/bin/sh，argv 按字面传递；Android 原生 shell 不等于 PRoot/chroot/Linux 工具链。

NDK 打包第二个 PIE 可执行文件 libaurora_pty.so，独立监督进程使用 posix_openpt、setsid、TIOCSCTTY 和终端窗口 ioctl。输入三字节帧头（操作码+小端16位长度），负载最多4096字节；输出为未修改的合并 PTY 字节。信号通过 signalfd，poll 驱动排空与背压；CPU 不忙等轮询界面。监督器的100ms poll超时用于处理执行截止和子进程退出，不是日志/页面定时读取。CLOCK_BOOTTIME 截止含设备休眠时间，恢复调度后检查。

关闭处理同一终端 session 的 shell/作业控制组；通过 /proc 查询同 SID 进程，只在结束时扫描。主动 setsid/独立守护化不属于完整 containment。父死亡信号依赖创建线程，因此 Kotlin 专用工作线程必须等监督器退出才归还线程池。不能把它迁到短生命周期 launch 后直接返回线程。

每空间最多4个活动终端，全局8个；默认5分钟截止，最长1小时。输入超过4096 UTF-8字节拒绝，Agent明确分批；输入取消/超时停止监督端，防止阻塞写永久卡住，不自动重发部分/不确定输入。窗口2..300列、2..200行。停止等待退出与FD清理。

terminal_read 默认只返回增量文本，避免每次重复发送大屏幕和 Base64。view=screen 用于 TUI，both 仅按需；includeBase64 可取得跨 UTF-8 分片的原始字节。next 是字节游标；live-ring 只保留末尾64 KiB，cursorDropped 明示丢失。文本预览可能含跨块替换字符，不能用它替代字节校验。screen 是当前 VT 屏幕，不是追加日志。当前读取序号和屏幕投影来自邻近时刻，不提供事务一致的跨流快照。

## Root 与生命周期

RootService 新增 openTerminal/activateTerminal/stopTerminal，身份协议仍1、命令协议仍2，另加 terminalProtocol=1；旧服务没有该版本则拒绝连接，避免误发新请求。Binder只传请求、状态和FD，Unix socket传帧及原始字节，不按行/字符Binder调用。客户端接收FD后激活，避免快速退出在Binder封送前关闭FD。回调Binder死亡/服务关闭/主动断开会停止对应终端；服务死亡产生不确定/中断结果。

应用持有 OwnedTasks 会话任务，终端跨单次模型回复和页面继续。复用 AgentTaskService 前台通知；最后活动任务退出才停止。refresh 使用 stopSelfResult(startId) 保留较新启动请求。通知停止全部任务，终端页可单独停止。删除空间先 quiesce、等待进程/FD清理，再清除驻留模拟器和文件。进程被杀后记录中断，不重启旧 shell 或重放输入。

默认不为闲置 shell 持CPU锁。Agent已知会运行持续计算时可在启动请求传 awakeMs，最多300000且不超过timeoutMs；应用侧申请有界 lease，Root UID不重复持锁，结束/取消释放，超时自然失效。不承诺熄屏持续计算默认就有CPU保证，也不绕过Thanox等主动冻结。

## 解析、显示与资源

Termux v0.118.3 固定提交，导入模拟器/缓冲/按键与 TerminalRenderer，未导入上游 TerminalSession、JNI、整个应用或软件仓库。终端组件无需第三方App。来源/修改/许可证见 terminal/README.md 和 UPSTREAM.json，CI离线校验SHA256；Apache/MIT声明随APK打包。上游文件超过300行作为可审计vendor例外。

读取每批4096字节；IO后台排空并保存，Default后台解析，短锁保护解析与Canvas一致读取，不套用上游主线程解析会话。VT反馈队列16项，溢出停止并明示失败；UI重绘随数据变化合并，页面状态最高约12.5次/秒且只在STARTED收集。支持颜色、备用屏、中文/宽字符、硬件按键和IME组合输入。原生Canvas内滚动、双指字号、回到最新、接手输入，输出阅读页按小块LazyColumn展示并复制。

回滚上限5000行，同时主缓冲限制为约65536列×行单元（随列宽减少历史），最多12个驻留模拟器，包括最多8个活动终端。保留上游每列组合字符数量限制。该策略是资源预算，不是实测内存峰值；完整 emoji/grapheme 最新覆盖不保证，上游宽字符表为Unicode15。尚无终端单词选择手柄、鼠标跟踪手势或GPU专用渲染器；已有完整输出复制和终端键盘输入。

OSC剪贴板写入、铃声等不触发系统副作用，不把终端内容写入Android日志。用户通过明确复制操作取得输出。终端页遵循原生主题框架，终端画布保留标准深色配色；未宣称视觉真机验收通过。

原始日志保存前16 MiB，之后继续排空、只留实时环形尾部，明确logTruncated；未实现轮转/无限输出存档。每个终端有state.json，开始和结束写入force后的临时文件再原子替换。日志与状态不是联合事务；崩溃恢复从实际日志大小恢复已保存字节，总接收字节未知时bytesExact=false。没有假造精确总量。停止后的驻留状态可读屏幕；被逐出或进程重启后只读保存日志，不从任意ANSI尾部伪造VT恢复。每空间列举最近100个状态，未提供更旧终端分页。

## 验证

- 核心新增5项游标/环绕/截断/UTF-8字节精确性/预算拒绝测试。
- Linux主机脚本8项：真实TTY/合并输出/退出码、输入+Unicode+窗口、Ctrl-C、截止、2MiB无页面排空、输出背压下停止、坏帧拒绝、shell后台作业清理。Linux主机通过不能替代Android SELinux/Root测试。
- Android新增9项终端专项：IME组合、原生PTY输入窗口与合并输出、截止、背压停止、VT屏幕、跨调用读取/权限归属、删除、崩溃记录、四会话预算。另新增1项普通UID跨进程Binder FD传输测试，不冒充UID0授权测试。
- 另新增2项显式RootRequired用例，检查真实UID0 PTY输入/退出及Root断开/重连不重放；只编译、未执行，不计入普通61项门槛。
- 新版设备测试目前仅编译。旧0.1.10真机48项不扩展到PTY。最终需普通/Root真实PTY、服务死亡、通知接续、前后台切换、熄屏、手势/中文输入法、FD/内存/吞吐对照和电量专项。

当前没有真实模型调用或性能领先证据；不承诺全程序兼容或不受系统后台策略影响。

工程复查：普通源文件均少于300行。RootTerminalSessions.open 超过60行，保留在同一资源作用域使FD、Binder死亡、激活门、子进程和协程清理可以一并审查；TerminalManager.start 同样集中准入和已注册任务移交。两处长生命周期函数作为已复查例外，后续修改必须再次核对取消/启动竞争；UI声明块与vendor文件不为追行数拆碎。
