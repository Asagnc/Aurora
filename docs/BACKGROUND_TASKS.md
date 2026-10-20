# 后台 Agent 生命周期 · 0.1.9

更新：2026-10-20。方案已经用户确认，见 BACKGROUND_TASKS_PROPOSAL.md。

## 实现

- Android 原生 specialUse 前台服务，只为用户主动发起的即时 Agent 任务启动；通知包含返回对应会话和停止，不显示 API 密钥或命令正文。没有 WorkManager、开机自启、轮询保活或常驻 Root daemon。
- ChatViewModel 现在是应用内 ChatController 的界面代理；清理 ViewModel 不会取消运行。AgentExecution 持有独立的会话投影，切换会话不会停止任务，也不向另一个会话写入流式消息。
- 首阶段全应用一个活跃 Agent 任务；重复提交拒绝。可以查看其他会话，通知可回到运行会话。每个 PendingIntent 用运行 UUID 隔离，旧停止指令不会停止新任务。
- 服务成功进入前台后才执行模型和工具；启动失败、超时、服务退出会结束本次任务，不自动重启或重放。取消先等待既有原生监督器清理，再释放运行入口。
- SQLite WAL 从 schema1 非破坏升级到 schema2，增加 agent_runs 的 UUID、owner、queued/running/终态、起止时间；外键归属于会话。协议检查点与工具账本继续使用原有追加存储，不复制大环境。
- 启动恢复把遗留 queued/running 任务记为 interrupted，未确认工具记 uncertain。会话内可展开任务 ID 和状态，具体工具结果仍在原位执行记录查看。
- 删除运行会话先取消并等待执行与收尾，再删除空间、浏览器和数据库记录。普通命令与 Root 命令的权限和现有限时不改变。

2026-10-20 复核官方发布记录后，将 kotlinx.serialization 从1.11.0固定升级到1.12.0（基于Kotlin2.4.10，与本项目2.4.21一起验证）；来源：https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0 。不把上游性能改进直接当作 Aurora 实测提升。

## 验收与边界

最终模拟器报告见 docs/evidence/0.1.9；真实模型不在本轮计费调用范围内。此前0.1.8真机结果只证明其原有能力，不能当作新版后台行为的真机证据。

不承诺厂商省电/系统强制停止下无限运行；没有默认唤醒锁。通知被拒绝时系统仍可能允许前台服务，但通知栏入口不可依赖，用户可在 Aurora 内停止。前台服务不等于可恢复任意进程内存、终端或未持久化的流式片段。

PTY、独立持久命令、终端会话、自动规划与安全续跑、长期日志轮转/按块读取仍未完成。现有命令输出已经保存为有上限的会话产物，但并非实时无限日志平台。

Google Play 发布需要按真实 specialUse 用途申报审核；没有借用媒体等无关类型。正式签名、Android16 ARM64后台/锁屏/厂商策略和 Root 断连联合测试仍待统一真机验收。

存储异常收尾也释放内存中的运行入口；终态后的会话重新读取异常会作为可见错误处理。此异常分支按控制流复核，未进行磁盘满/数据库损坏故障注入。

## 源码审阅

ChatController 控制观看状态与提交；AgentExecution 控制固定模型/工具/协议检查点；AgentTaskHost 串行处理 admission、服务就绪与取消；AgentTaskService 处理 Android 通知及服务生命周期。均低于300行，无新增依赖。AgentExecution.execute 保留约50行连续的历史准备和检查点编排，已按写入先后及取消边界复核；声明式 UI 和串行集成测试按职责保留。

官方约束来源：
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

2026-10-20最终验证：core45、模拟器普通39、独立浏览器恢复1、Agent进程恢复prepare1/verify1通过；debug/AndroidTest/R8 release构建通过，lint0错误、1项既有MiMo矢量警告。序列化1.12.0升级后已复跑。证据：docs/evidence/0.1.9；新版真机后台/Root联合验收未做。
