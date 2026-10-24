# Root受管执行器（2026-10-24）

这是已批准受管任务方案内的执行/FD接入，不新增依赖、常驻进程或持久化格式。Binder派发/恢复与账本收尾已接，见ROOT_MANAGED_BINDER.md；Agent已有status/recover，尚无注入工具、打包Frida Core worker或Android运行资格。

## 可执行文件固定

RootManagedExecutable从已资格化不可变bundle的预期SHA-256检查实际inode，按64KiB块pread；检查前后dev/inode、大小、属主/组、模式、链接数及纳秒mtime/ctime一致。仅接受Root或系统安装UID持有、不可被组/其他UID写、无setuid/setgid的单硬链接普通可执行文件；拒绝security.capability属性，只有ENODATA/文件系统不支持xattr可作为无能力属性。权限拒绝不能当作“没有属性”。

入口O_NOFOLLOW/CLOEXEC；每次执行独立复制并显式CLOEXEC，通过/proc/服务PID/fd/N固定已检查inode，启动到退出屏障期间保持。共享版本的两个任务可以同时进入，各自关闭不影响对方；关闭原pin仅阻止新执行。预期摘要不能从用户选定文件临时生成当成资格；bundle租约仍须由调用者持有。其他Root直接修改同一inode不在沙箱保证内。

## 直接启动与取消

RootManagedProcessRunner在同一个阻塞IO线程创建并等待原生coordinator，传递cooperative-cancel模式，不套普通命令执行器。启动核对当前Root身份、ticket/worker摘要及脚本通道的同一序列化票据；恢复只传runId/摘要，不重放脚本。

stdin定向/dev/null；合并输出通过提前创建的Root私有日志FD重定向，不保留stdout/stderr管道。这样Java Process.destroy发送TERM时关闭自己的流，不会提前切断worker输出。日志不是最终证明，当前worker自身有结构化输出预算；此执行器没有新增通用磁盘配额功能。

RootManagedProcessWait采用带绝对截止的Process.waitFor事件等待。中断仅第一次发送TERM并等待，保留最多26秒取消/原生恢复窗口且不超原总预算；创建线程不会在协作收尾期间返回线程池。正常native总预算外保留5秒传输余量。绝对截止后强停coordinator，最多等1秒退出确认；没有确认或曾强停都要求后续native recover。循环在每次中断后仍检查绝对截止，不靠反复中断延长预算。返回后恢复线程中断标记。

code/cancelled/forced只是进程传输结果，不是脚本卸载证明，也不能直接删除活动账本。只有后续RootManagedRecords及精确typed proof可以收尾；缺失事实保留未确认。调用者必须在finally/NonCancellable内协调恢复和租约释放。

## READY交付不轮询

RootScriptChannel新增锚定任务目录FD的handoff。先等待worker的AF_UNIX连接，再读取并验证Root持久READY与未退役状态，最后匹配SO_PEERCRED UID/PID、startTicks、nonce/票据并交付封印FD。合格worker仅在guard完成READY与RELEASED、实际GO之后才exec并连接，因此不用反复读文件或额外FileObserver，也不把创建中的READY当成已发布记录。显式worker身份重载只保留给独立通道资格测试。

五秒握手、取消关闭accept/连接、一次交付、ACK/EOF语义沿用已有通道。ACK只表示FD验证，不表示注入或卸载完成。

## 验收范围

- 三项核心实际Java子进程用例：正常退出码、取消后等协作退出并恢复线程中断、绝对截止强停明确标记。Linux CI还要求TERM shutdown hook已写出清理标记；Windows不据此宣称Unix信号行为。
- 新增五项Android文件固定用例及一项普通UID持久handoff拒绝用例，仅编译，设备运行集中到最终验收。
- 仍待最终设备验证：/proc FD路径执行/SELinux、APK安装属主、Root实际cgroup/Frida/ART、服务断连/冻结/并发删除与版本卸载屏障。主机测试不替代这些资格。

依据：[Android Os公开API](https://developer.android.com/reference/android/system/Os)；本机SDK37 API历史确认getxattr since26、StructStat纳秒字段since27；[PR_SET_PDEATHSIG线程及特殊执行属性语义](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)、[文件能力属性](https://man7.org/linux/man-pages/man7/capabilities.7.html)。文件固定方式是对上述语义及现有Root FD通道的实现判断。
