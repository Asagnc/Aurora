# Frida spawn：已确认的持久恢复增量

2026-10-25。用户在具体比较后回复“最优就行”，按推荐的现有 worker／schema8 活动表＋protocol2 两阶段捕获方案实施。Android 运行资格仍待集中验收。

固定 Frida17.24.3 的 Device.spawn 返回新进程 PID，并保持暂停直至 resume。Android 包名走 RoboLauncher，区别于绝对路径原生程序；不能把普通 shell 启动后 attach 称为早期注入。官方 spawn gating 捕获范围更广，首版不启用全局 gating。

| 选择 | 结果 |
| --- | --- |
| shell/am 启动后按包名寻找并 attach | 可能错过早期代码；包名不能证明本次创建身份，不选 |
| 独立 server/Python 或全局 spawn gating | 增加进程/协议或扩大拦截范围，不选 |
| 现有独立 worker 的显式 Device.spawn，加两阶段持久事实 | 延续已批准引擎和监督，补齐暂停进程归属与恢复，推荐 |

推荐增加 protocol2 的不可变 spawn 意图：选定包名/脚本/桥/worker/Root 身份、期限，目标身份在调用前未知，不伪造 PID。保留 protocol1 attach 解码与旧账本恢复。继续使用 schema8 活动表的 JSON 票据，不新增数据库框架；是否需要索引/迁移以实际实现检查为准。

Root journal 必须先持久化 spawn_requested，再调用 API；取得 PID 后核实 boot/PID/start，持久化 spawn_captured，固定目标后方可 attach/load/resume。卸载证明必须同时绑定意图摘要与捕获目标事实，不能用后来的 PID 覆盖原始票据来伪造旧证明。

正常成功在脚本载入后 resume，结束卸载/detach，保留已 resume 的应用。失败/取消时只允许终止本任务明确捕获且仍未 resume 的精确目标，并核实退出；不按包名终止用户已有应用。调用发生而捕获记录缺失的崩溃窗口保留 unknown，禁止自动重放，也不猜 PID 清理。这个限制需要在工具结果与删除屏障中可见。

验收包括自有原生 spawn 的暂停/载入/resume 顺序、暂停阶段取消、捕获前后崩溃、迟到回调、身份更换、恢复拒绝重放、旧 attach 兼容与两会话隔离。主机通过后再集中执行自有 Android 测试包；不对用户其他应用默认操作。

依据：[固定 Device API 实现](https://github.com/frida/frida-core/blob/17.24.3/src/frida.vala)、[固定 Android/Linux 后端](https://github.com/frida/frida-core/blob/17.24.3/src/linux/linux-host-session.vala)。以上是工程方案，未验证 Android16 ROM 上的 spawn 行为。

实现阶段文件为 spawn.1 请求、spawn.2 精确捕获、spawn.3 resume 意图、spawn.4 精确终止、spawn.5 resume 成功，均64字节、票据摘要绑定、独占创建/fsync。resume 意图不能代替成功；只有意图时保留未知且不自动终止。Root 恢复在 worker/域屏障后，仅同 boot 的已捕获且无 resume 意图目标可由 pidfd 精确终止。
