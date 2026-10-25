# Frida Agent 最终集中验收

2026-10-25：生产 Agent 已连接固定 APK worker/Java 桥与 schema8 账本。当前本地核心 263 项（251 通过、12 跳过），Debug/Release/testAPK/lint 通过；Android 用例仅编译。没有执行以下设备步骤，没有调用模型。

最终设备空闲、没有活动 Agent 时，使用已有 Android SDK 和已授权 Root，保留 Aurora 数据。仅在用户安排的集中验收阶段安装对应 APK/testAPK；不提前连接手机。先核实当前 ARM64、Android 16、Root 服务身份与安装包摘要。

1. 直接运行 `FridaManagedDeviceTest`，同时显式设置 `auroraRootTest=true` 与 `auroraFridaTest=true`；这是直接工具/账本链测试，无需模型。先 native probe，再自有 Root 服务目标的固定脚本 attach，再 Java probe。检查真实事件、workerSucceeded/probeSucceeded、会话日志与活动账本清零。
2. 重连 Root 后资格必须归零；旧身份/脚本摘要/桥版本不能沿用。未通过对应模式 probe 的用户 attach 必须拒绝。
3. 集中执行取消、服务死亡、目标退出、GO 前后故障及删除会话矩阵。只有精确清理/未注入退役/目标退出证明能清账；未知结果保留阻断，不重放脚本。检查日志失败不吞掉清理证明。
4. Java unavailable 或固定自测缺失实际调用事件时不得授予资格。桥收到消息和异步 Java.perform 返回不代表用户任务完成。
5. 验收后记录实际设备、构建摘要与结果；清理本次自有测试会话和辅助包，保留 Aurora 数据。正式签名发布与完整上游 SDK 源码重建均不由本地主机编译代替。

源码、许可、离线重链接范围见 FRIDA_SOURCE_MATERIALS.md。当前源码材料是固定输入超集，不能声称所有条目都是实际运行依赖。

spawn 增量：先用自有测试包验证载入脚本后 resume、语法错误/暂停阶段取消、spawn调用后捕获前崩溃、capture后worker死亡、resume意图与成功之间崩溃、精确目标身份替换、boot变化。只回收本任务已捕获且无resume意图的精确进程；未知捕获/未知resume不能因worker已退出而清账。主机用例不替代Android包启动与ART早期注入资格。
