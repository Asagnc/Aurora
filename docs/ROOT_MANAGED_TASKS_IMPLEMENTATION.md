# Root受管任务接入进展（2026-10-24）

用户已确认ROOT_MANAGED_TASKS_PROPOSAL.md推荐组合。本页区分基础实现和可执行产品入口；当前Frida仍未打包、未注册Agent工具，没有新增手机执行记录。

## 已实现的基础

- 原生SQLite schema8：新root_managed_runs活动/未确认意图表，owner外键不级联删除；owner/id索引只查询待处理记录。可选tool_run_id登记时在同一事务验证归属，但不设置历史级联外键；删除显示历史不会删除执行事实。没有Linux镜像或租约依赖，原linux_runs/linux_leases不变。
- 不可变RootManagedRunTicket：完整Root身份、目标PID/startTicks、App构建、worker摘要、脚本/桥版本摘要大小及观察预算。当前只允许attach；spawn必须另有状态机，不能通过任意action字符串伪装已支持。观察最多250秒，总预算留20秒清理空间。原生CLI也同步收紧到250秒，并补250000接受/250001拒绝等边界；NDK ARM64重新交叉链接通过，独立原型仍未打包。
- ticket摘要定义为protocol1的WireJson.encodeToString(ticket) UTF-8字节SHA-256；不是源文件摘要，也不对任意另一种JSON编码计算相同摘要。后续Root通道须核对同一不可变票据字节/摘要，不能改写意图生成新证明。
- 正常退出证明必须匹配runId、Root完整身份及ticket摘要，并分别明确scriptUnloaded/sessionDetached/managerClosed/workerExited/domainRemoved。任何缺失/false都不能释放记录；worker死亡或cgroup空不能替代目标注入清理。这里只定义可信Root通道的数据契约，尚未从stdout接受证明。
- SpaceStore删除/恢复先检查受管记录，未确认时不删除文件；数据库外键再次阻止直接删除owner。错误显示Root注入清理未确认。相同精确证明可幂等完成，其他owner/改写票据不受影响。未增加自动重放或自动杀目标。
- RootScriptMemory通过公开Os.memfd_create复制所选本地输入，限制<=1MiB、精确大小/摘要、64KiB缓冲；WRITE/GROW/SHRINK/SEAL全部封印后返回CLOEXEC PFD。接收端检查regular/大小/封印，以pread验证摘要而不改共享offset。输入消费后关闭；失败/取消（包括withContext交付窗口）关闭已创建描述符，无磁盘回退。它是基础帮助类，未接Binder或worker。
- 修复迁移索引重复创建；旧迁移用例仍断言schema6/7的部分同步为8。新建或历史迁移不删除消息、Linux版本/租约。

封印禁止修改字节，但原始memfd打开模式仍是O_RDWR，不能宣称access mode变成O_RDONLY；不可变性依赖已验证的内核seals。SCM_RIGHTS与SELinux资格尚未测试。

## 正在接入的FD通道

新增原生AF_UNIX stream接收库：SO_PEERCRED+pidfd+开始时间核实发送方；固定72字节AFD1帧（role/count/reserved、32字节nonce、32字节票据摘要），hello→单次文件帧→EOF→接收ACK。描述符只允许出现在文件帧首个分片，明确校验MSG_CTRUNC、数量、重复inode、regular/大小、四项seals和CLOEXEC。失败关闭所有已接收FD；cancel/peer pidfd与绝对截止使用poll事件等待，不定时轮询。

1e5baf1完整CI39989586948已成功，8项真实Linux socket测试实际通过（含多项子场景）；证据ci-39989586948-fd-tests-passed.txt。随后新增第9项对端进程死亡事件用例待最新CI执行。

Android RootScriptChannel与原生attach-channel开发入口现已接入同一协议：发送方复制并独立校验脚本/桥PFD，核实worker UID/PID/startTicks和hello后只发送一次，清空outbound FD队列、半关闭写端并核对ACK/EOF。5秒绝对握手期限和取消主动关闭监听/连接/快照副本，不依赖线程中断来停止阻塞accept。调用者原PFD保留。nonce不应打印到日志。

worker独立解析有界执行字段，核实Root对端身份、封印/大小/摘要/UTF8/NUL；读取后关闭FD和socket，桥可选，目标仍由原pidfd/startTicks流程复核。Frida统一由worker初始化/销毁一次，解析不再在初始化前使用随附JSON库；初始环境/握手时间消耗操作预算，保留清理窗口。原有8会话测试与新增3项真实Frida通路（单脚本、桥+脚本、错误摘要/大小拒绝）待最新CI执行。FD ACK只表示传输验证，不是脚本执行/卸载证明。

当前ARM64完整worker交叉链接通过（stripped89141216字节，未打包），Android发送端/核心及Debug/Release R8/testAPK/lint本地通过；3项新增核心已执行，5项Android发送端用例仅编译。累计受管基础/发送端14项Android仅编译，仍不改变ready状态。

## 验证

本地核心242项：232通过、10平台跳过；票据/预算/证明/错配4项与帧协议3项实际执行。Debug、Release R8、testAPK、lint通过；随后扩展Android用例又编译/lint通过，见evidence/0.1.40/local-root-managed-foundation-passed.txt。

基础Android9项仅编译：账本5项（重开/删除/精确完成、归属/回收保留、损坏/重复票据、schema7保留真实Linux票据/租约、1..7迁移与索引选择），内存4项（dup/不可写不可截断、源变化、取消/close异常回收、接收端拒绝未封印或不同摘要）。没有连接手机，不能把这些计入已执行用例。

上一提交16a32b5完整CI39971396204已成功，包含8项真实Frida主机生命周期和Android构建；它不包含本页随后新增基础代码。当前提交CI推送后另查。

## 尚未完成，继续顺序

1. 已接AF_UNIX脚本FD发送/接收开发通路；继续Root Binder PFD所有权、实际worker PID捕获与受管派发连接，最终Android16/SELinux资格。补最终退出事实小通道，不能从截断stdout猜证明。
2. Android-native受管cgroup入口、Root阶段日志/任务锁/退役标记。迟到start不能越过取消；不得使用普通250ms升级杀进程窗口代替Frida清理期。
3. 精确恢复与删除协调：原目标已退出/重启、正常unload、派发前退役及未知注入是不同事实。当前finish仅支持正常完整清理证明，尚无target-gone/never-started恢复入口；不能在缺少证明时清账。
4. Root资格、可观察日志、Agent/UI接入、spawn状态机、Android ART、自有目标故障验收。
5. 完成许可证闭包/对应源码、APK体积与最终集中真机测试后才能发布Frida ready。

## 核实依据

- [Android公开Os接口](https://developer.android.com/reference/android/system/Os)：本机android-37.0 SDK javap再次确认memfd_create/fcntlInt/pread/普通文件API；最小SDK36无需旧版回退。
- [Linux memfd文档](https://man7.org/linux/man-pages/man2/memfd_create.2.html)：匿名内存FD、CLOEXEC、seals与引用关闭释放语义。
- NDK30 sysroot linux/memfd.h和linux/fcntl.h：MFD_CLOEXEC=1、ALLOW_SEALING=2、ADD_SEALS=1033、GET_SEALS=1034与四项seal位。只使用公开API和命名UAPI常量，不用隐藏反射。

FD实现核实：[Linux unix/SCM_RIGHTS](https://man7.org/linux/man-pages/man7/unix.7.html)、[recvmsg](https://man7.org/linux/man-pages/man2/recvmsg.2.html)、[公开LocalSocket](https://developer.android.com/reference/android/net/LocalSocket)、[AOSP Java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/net/LocalSocketImpl.java)和[JNI实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_net_LocalSocketImpl.cpp)。JNI只在一次写的首段发送rights，Java outbound队列需显式清空；每次读后即提取并关闭异常rights，避免覆盖引用。上游Frida17.24.3 frida-glue.c核实随附GLib初始化/销毁次序。
