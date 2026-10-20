# 后台 Agent 任务方案（已确认）

方案研究日期：2026-10-18。用户已确认采用；0.1.9 实现和验收见 BACKGROUND_TASKS.md。

## 已发现的问题

ChatViewModel 当前持有运行 Job，执行依赖 viewModelScope；切换会话会取消当前任务。没有前台服务，UI 生命周期和执行生命周期尚未分离。已有 SQLite 原生历史、工具账本、OwnedTasks 和原生进程监督继续复用。

## 建议

使用 Android 原生 started 前台服务承载用户主动发起的 Agent 运行，UI 订阅任务状态；不引入 WorkManager，不另起常驻 Root 守护进程。服务按任务启动，全部结束后退出；通知提供回到会话和停止动作，不放密钥/命令正文。

服务类型拟用 specialUse，声明实际用途：用户主动启动、可观看且可停止的交互式多步骤 Agent 执行。它不是无限保活保证；若发行 Google Play，需要按实际用途申报并接受审核。不能伪装成媒体播放。

SQLite 保存任务标识、所属会话、运行状态、检查点与终态；输出文件留在所属会话。开始新副作用前先记账，进程死亡后未确认动作标为 uncertain，绝不自动重放。恢复会话可以显示已有事实，真正继续前由 Agent 检查结果；不宣称可以恢复任意进程的内存或终端。

首阶段只保持一个活跃 Agent 运行，可浏览其他会话；运行中的会话返回后继续显示状态。删除其会话先取消并等待清理。Root 工具继续走现有显式连接，不自动提权。

PTY 是后续独立步骤，本阶段不把一次性命令包装成交互终端。不默认加轮询、开机自启、唤醒锁或 Root 保活修改。

## 方案比较

- WorkManager：适合可延后调度；长任务仍可能使用前台服务，Android16 长时间 worker 还受任务配额影响，不作为即时 Agent 主循环。
- dataSync 前台服务：适合传输/文件处理，但后台有每日累计时限，不将完整交互式 Agent 简化为同步任务。
- Root daemon：能承载特权执行，但增加独立生命周期、撤权与清理责任；当前无需用它承载模型网络与全部 UI 状态。
- 原生 specialUse 前台服务：建议，任务期间保持可见通知与明确停止入口；仍须验证系统终止和厂商策略。

## 验收标准

1. 确定性模型执行时退到后台、重建 Activity、切换会话，任务不因 UI 消失而取消。
2. 通知停止与会话删除等待原生命令取消清理；结束移除运行通知。
3. 进程被杀后重启显示 interrupted/uncertain，已完成工具不重复执行。
4. 重复启动不能创建双运行；启动失败不留下虚假的 running 状态。
5. 普通和 Root 工具权限不变；模拟器先测，再覆盖更新真机并保留数据。

## 官方来源

- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

用户已明确回复“认可，按推荐方案实现”。实际使用非导出的 started service；UI 通过应用内控制器观察，不增加 Binder 或独立进程。
