# 本地命令的有界 CPU 唤醒范围 · 0.1.10

2026-10-21，用户确认后实现。真实 Android16 ARM64 测试发现，原0.1.9的前台服务不能单独证明熄屏下持续完成命令。新增方案不改变命令权限、后台服务类型或协议。

## 选型与范围

比较继续只用前台服务、整轮 Agent 持锁、命令阶段持锁、WorkManager/额外守护进程。采用 Android `PARTIAL_WAKE_LOCK`，仅覆盖一次本地命令，兼顾立即执行、既有取消/进程监督和无常驻额外调度器。等待供应商响应、浏览器空闲、Agent 空闲均不通过本模块持锁。命令内部可能自行等待网络或子进程，Aurora 不能从任意 argv 推断其中每段 CPU 使用；范围是整个已提交命令，而非 CPU 指令采样。

- 普通原生命令：校验请求、取得并发槽后，在实际阻塞执行范围 acquire；完成、错误、超时和取消清理后通过 `use` 释放。
- Root 命令：应用侧 RootCommandClient 在提交请求前 acquire，等待结果及取消/清理后 `finally` 释放。Root 服务 UID0 的 NativeCommandRunner 不重复取得锁，避免把正常消耗归到 UID0。
- 每条命令各自拥有非引用计数 lease；并发命令不能互相释放。tag 固定为 `Aurora:NativeCommand` / `Aurora:RootCommand`，不含会话、命令正文、凭据或随机 ID。
- 普通命令预算为请求超时加1秒；Root为超时加15秒（含既有响应/清理余量）。系统兜底上限315秒，最小1秒；不循环续期、不无限持有。正常结束总会主动释放，不等待兜底。
- `WAKE_LOCK` 是 manifest 普通权限，不新增用户授权弹窗、Root要求、屏幕常亮或电池白名单自动修改。

唤醒锁防止 CPU 自动挂起，不是防杀/防冻结机制；不能绕过 Thanox、厂商冻结、强制停止、深度Doze等所有策略，也不能保证任意 Root 守护化进程存活。任务停止后的锁释放与原有工具不重放约束继续成立。

## 证据与限制

初次0.1.9熄屏用例有一轮测试夹具漏建目录，已纠正并单独保留失败记录。随后正式熄屏用例等待40秒超时；0.1.10有界锁接入后，在当时策略下也超时，不能把失败简单归因于缺少唤醒锁。用户再次调整 Aurora 的后台冻结豁免后，同一Root熄屏用例17.562秒通过。最终组合包含唤醒锁和调整后的设备策略，不是单因素A/B结论。

用例先确认真实Root命令已写出首个tick，再退到主页、执行睡眠按键并确认 Asleep/Dozing，等待15个tick、UID0、exitCode0与任务收尾。它只证明短时熄屏条件，不证明拔线、长期深度Doze、小时级耗电或任意ROM。设备在USB连接下，结果不能扩展成完整省电模式验收。

新增普通设备测试使用真实 PowerManager 检查两个命令lease独立释放，以及系统超时回收；不是只测试布尔变量。现有原生命令/Root测试继续验证取消、超时、结果与所有权；具体最终数量与结果见 [本轮真机记录](DEVICE_ACCEPTANCE_2026-10-21.md)。

## 一手依据

- [Android：选择正确的唤醒机制](https://developer.android.com/develop/background-work/background-tasks/awake)。
- [设置带超时的唤醒锁](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set)。
- [PowerManager.WakeLock](https://developer.android.com/reference/android/os/PowerManager.WakeLock)。
- [Android官方电量质量指导](https://android-developers.googleblog.com/2026/03/battery-technical-quality-enforcement.html)：前台服务与CPU唤醒分别处理，限制持有范围。

没有“零性能损耗”或电量改善的基准结论；取得/释放锁的系统交互与对象开销需在后续真实任务性能基准中计量。
