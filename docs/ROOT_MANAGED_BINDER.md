# Root受管Binder与活动账本（2026-10-24）

沿用已批准的schema8、封印memfd、一次性AF_UNIX、独立guard与精确退出证明；没有新增底层依赖或持久化格式。已经连接派发/恢复代码与Agent恢复入口，但Frida worker/Java桥尚未打包，注入入口明确不可用，不是Android执行资格。

## 派发与所有权

IRootControl新增startManagedRun/recoverManagedRun；先验证调用UID、规范runId、票据归属和8KiB元数据上限，拒绝多余路径字段。App和Root分别持有独立PFD副本，Binder收到的句柄在调用返回前关闭；异步副本随RootCommandSession最终完成关闭，覆盖回调Binder死亡、尚未开始的取消和拒绝入队。工作线程再次核实封印/字节摘要，再走已有READY/peer身份握手。

RootManagedDispatch固定当前Root身份、App versionCode、编译可信worker摘要和安装inode，两个进程/FD分支通过结构化并发一起退出。失败或取消先等待原生进程屏障，再在NonCancellable范围执行仅该票据的恢复；没有重放脚本、终止原目标或套普通250ms命令回收。当前不存在worker摘要资产，启动在放行前拒绝；Java桥也未开放。

RootManagedFiles在共享资源下保留Root私有root-managed-runtime/runId，创建后fsync父目录，坏权限/链接拒绝。原生锁、票据SHA及退役记录仍负责迟到start互斥，不因App回调或内存任务表为空推断从未执行。

## 结果与账本

RootManagedResult是受信Root Binder的小结果，分别携带正常清理、派发前退役或原目标退出证明，至多一种。App再次核对原票据、证明和错误白名单，正常退出码不能代替证明；失败执行若有完整清理证明可以清账，但不报告执行成功。

RootManagedRunManager先登记活动意图再派发，调用失败/取消尝试精确恢复；未知状态保留。恢复只遍历当前会话的活动索引，不扫历史，不自动Root重连。Agent开始及用户删除前接入恢复；删除仍由原事务屏障拒绝未确认项。

Agent工具root_managed_tasks提供status/recover，报告pending与injectionReady=false；这不是Frida attach/spawn分析工具。Root不可用、记录损坏、进程退出未确认或原目标仍活且无卸载证明均保留活动项。没有调用真实模型。

## 实际APK输入

Gradle在stripDebugSymbols后为双ABI生成guard SHA256资产，Root从安装APK资产取得期望摘要，不接受请求提供可执行路径/自算摘要。verify-linux-native.py同时检查实际APK ELF与资产摘要一致；Debug/Release均通过。固定inode仍保留到进程退出屏障。worker与bridge发布材料闭包尚未完成，不生成其可信发布资产。

## 验证

本地core共255项：243通过、12跳过（10项平台限定，2项需要未提供的官方归档样本），新增3项结果证明/错票据/失败执行回归通过。Debug、Release R8、testAPK、lint全部成功；双ABI实际APK摘要/ELF审计与Android36 Files门禁成功。

新增6项Android用例（4项FD/取消所有权、1项安装摘要、1项账本结果），并扩展普通UID Binder拒绝断言，均仅编译。Root/SELinux、真正FD派发、服务断连与恢复仍待最终手机集中验收。没有操作手机、模拟器或付费模型，没有重装工具链；复用已有JDK25/Gradle缓存/NDK30。

依据：[Android AIDL并发与本地/远程调用](https://developer.android.com/develop/background-work/services/aidl)、[ParcelFileDescriptor复制与关闭](https://developer.android.com/reference/android/os/ParcelFileDescriptor)。
