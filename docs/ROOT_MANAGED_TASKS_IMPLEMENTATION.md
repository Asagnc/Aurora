# Root受管任务接入进展（2026-10-24）

用户已确认ROOT_MANAGED_TASKS_PROPOSAL.md推荐组合。本页区分基础实现和可执行产品入口；当前Frida仍未打包、未注册Agent工具，没有新增手机执行记录。

## 已实现的基础

- 原生SQLite schema8：新root_managed_runs活动/未确认意图表，owner外键不级联删除；owner/id索引只查询待处理记录。可选tool_run_id登记时在同一事务验证归属，但不设置历史级联外键；删除显示历史不会删除执行事实。没有Linux镜像或租约依赖，原linux_runs/linux_leases不变。
- 不可变RootManagedRunTicket：完整Root身份、目标PID/startTicks、App构建、worker摘要、脚本/桥版本摘要大小及观察预算。当前只允许attach；spawn必须另有状态机，不能通过任意action字符串伪装已支持。观察最多250秒，总预算留20秒清理空间。原生CLI也同步收紧到250秒，并补250000接受/250001拒绝等边界；NDK ARM64重新交叉链接通过，独立原型仍未打包。
- ticket摘要定义为protocol1的WireJson.encodeToString(ticket) UTF-8字节SHA-256；不是源文件摘要，也不对任意另一种JSON编码计算相同摘要。后续Root通道须核对同一不可变票据字节/摘要，不能改写意图生成新证明。
- 正常退出证明必须匹配runId、Root完整身份及ticket摘要，并分别明确scriptUnloaded/sessionDetached/managerClosed/workerExited/domainRemoved。任何缺失/false都不能释放记录；worker死亡或cgroup空不能替代目标注入清理。这里只定义可信Root通道的数据契约，尚未从stdout接受证明。
- SpaceStore删除/恢复先检查受管记录，未确认时不删除文件；数据库外键再次阻止直接删除owner。错误显示Root注入清理未确认。相同精确证明可幂等完成，其他owner/改写票据不受影响。未增加自动重放或自动杀目标。
- RootScriptMemory通过公开Os.memfd_create复制所选本地输入，限制<=1MiB、精确大小/摘要、64KiB缓冲；WRITE/GROW/SHRINK/SEAL全部封印后返回CLOEXEC PFD。接收端检查regular/大小/封印，以pread验证摘要而不改共享offset。输入消费后关闭；失败/取消（包括withContext交付窗口）关闭已创建描述符，无磁盘回退。已通过RootScriptChannel接worker开发通路，Root Binder仍未连接。
- 修复迁移索引重复创建；旧迁移用例仍断言schema6/7的部分同步为8。新建或历史迁移不删除消息、Linux版本/租约。

封印禁止修改字节，但原始memfd打开模式仍是O_RDWR，不能宣称access mode变成O_RDONLY；不可变性依赖已验证的内核seals。Linux SCM_RIGHTS已有真实主机证据；Android实际SCM_RIGHTS/SELinux资格尚未测试。

## 正在接入的FD通道

新增原生AF_UNIX stream接收库：SO_PEERCRED+pidfd+开始时间核实发送方；固定72字节AFD1帧（role/count/reserved、32字节nonce、32字节票据摘要），hello→单次文件帧→EOF→接收ACK。描述符只允许出现在文件帧首个分片，明确校验MSG_CTRUNC、数量、重复inode、regular/大小、四项seals和CLOEXEC。失败关闭所有已接收FD；cancel/peer pidfd与绝对截止使用poll事件等待，不定时轮询。

1e5baf1完整CI39989586948已成功，8项真实Linux socket测试实际通过（含多项子场景）；证据ci-39989586948-fd-tests-passed.txt。随后第9项对端进程死亡事件用例也在dbea45a完整CI39991608445中实际通过，见对应fd-tests-passed.txt。

Android RootScriptChannel与原生attach-channel开发入口现已接入同一协议：发送方复制并独立校验脚本/桥PFD，核实worker UID/PID/startTicks和hello后只发送一次，清空outbound FD队列、半关闭写端并核对ACK/EOF。5秒绝对握手期限和取消主动关闭监听/连接/快照副本，不依赖线程中断来停止阻塞accept。调用者原PFD保留。nonce不应打印到日志。

worker独立解析有界执行字段，核实Root对端身份、封印/大小/摘要/UTF8/NUL；读取后关闭FD和socket，桥可选，目标仍由原pidfd/startTicks流程复核。Frida统一由worker初始化/销毁一次，解析不再在初始化前使用随附JSON库；初始环境/握手时间消耗操作预算，保留清理窗口。原有8会话测试与新增3项真实Frida通路（单脚本、桥+脚本、错误摘要/大小拒绝）已在39991608445实际11项通过，且整体CI成功。FD ACK只表示传输验证，不是脚本执行/卸载证明。关闭通道不能撤回已交付FD；交付后的取消还须受管任务进程域配合，当前不能用socket关闭冒充完整Frida取消。

当前ARM64完整worker交叉链接通过（stripped89141216字节，未打包），Android发送端/核心及Debug/Release R8/testAPK/lint本地通过；3项新增核心已执行，6项Android发送端用例仅编译（含进入握手前已取消）。累计受管基础/发送端15项Android仅编译，仍不改变ready状态。

## 正在接入的专用退出事实通道

开发worker新增原生父进程通过fork/exec明确继承的私有AF_UNIX stream。它不同于一次性脚本FD socket：父进程只发单字节到期/取消指令，worker在独立事件上下文监听；结束后只发一次52字节AEX1最终事实。没有新线程、守护进程、依赖或每行Binder回调，也不假设Java ProcessBuilder会保留FD。

bootstrap要求FD>=3、stream、SO_PEERCRED UID0且peer PID是当前父进程，恢复CLOEXEC/非阻塞后清除两个内部环境项，避免传给helper。受管模式只允许封印脚本通道，校验环境票据摘要与实际输入摘要一致。控制通道取消/断开也能中断脚本FD握手；交付之后须由进程监督配合，不能仅关闭脚本socket。

AEX1固定magic/version/reserved、final/attachAttempted/loaded/unloaded/detached/managerClosed/success位、退出码、票据摘要、worker PID/startTicks；解析器拒绝跨票据/进程/启动、保留位、无效成功组合。final只代表最终报告，不等于任务已退出或清理成功。worker只在Frida deinit完成后发送，报告失败返回74；强杀/未关闭manager没有最终报告，不能回退从stdout猜证明。业务success仍仅指请求的有界观察完成，不是所有异步Java业务都已结束。

修复已有原型的一个事实边界：attach API调用过却没有session对象，不再自动标记detached；保留未确认事实。未调用attach的预检失败有单独的never-attempted事实（57/64），也须结合真实worker退出、域回收和Root退役屏障后才能清理SQLite意图。

ARM64完整worker交叉链接/外层16KiB检查通过，stripped89143968字节，未打包。新增7项纯主机控制/解码用例，以及8项真实Frida监督用例（自然完成、取消、到期、通道丢失、非法控制、预检拒绝、强杀无证明、已有调试器占用时不谎报detach），真实Frida总计19项，已在51a5658完整CI39997696909实际通过；7控制/解码也实际通过，见对应证据文件。

此阶段仍没有接Root Binder、独立原生cgroup guard/持久阶段/退役处理或Agent；接收worker事实之后仍必须验证worker已退出和本任务域已移除，不能据此显示Frida ready。后续guard须由自己创建control socketpair并明确交给自己的子进程，不能拿Java创建socket的SO_PEERCRED当原生guard身份。

## 监督端严格接收原语

managed_receive独立于Frida运行库，以MSG_DONTWAIT/recvmsg从事件循环增量接收。52字节完整帧不提前公开receipt，必须读取EOF并核对票据/worker身份/事实组合。任意尾字节、重复帧、短EOF及所有附带控制消息均拒绝；SCM_RIGHTS收到即关闭，MSG_CTRUNC也拒绝，内核关闭未交付的截断描述符。失败状态保留，不能被之后的数据修复。调用者持有socket并独立负责凭证核实、绝对截止、worker退出与域回收；pending才继续poll，HUP仍须排空已有字节，不能直接视作空报告。

每次最多读53字节，不增加线程、定时轮询或按对端流长度分配。9项Linux测试覆盖51个切分点、52个截断长度、完整但未关闭、尾部/重复/大尾、附带及截断FD、错票据/进程和部分清理事实；尚待最新CI。NDK30/API36 ARM64编译检查通过；Windows本机9项平台跳过，不计入已执行。尚未被Root guard调用或APK打包。

核实依据：[recvmsg](https://man7.org/linux/man-pages/man2/recvmsg.2.html)、[poll](https://man7.org/linux/man-pages/man2/poll.2.html)，包括短读、非阻塞返回与HUP仍有未读数据。

## 验证

本地核心242项：232通过、10平台跳过；票据/预算/证明/错配4项与帧协议3项实际执行。Debug、Release R8、testAPK、lint通过；随后扩展Android用例又编译/lint通过，见evidence/0.1.40/local-root-managed-foundation-passed.txt及local-root-script-channel-passed.txt。

基础Android9项仅编译：账本5项（重开/删除/精确完成、归属/回收保留、损坏/重复票据、schema7保留真实Linux票据/租约、1..7迁移与索引选择），内存4项（dup/不可写不可截断、源变化、取消/close异常回收、接收端拒绝未封印或不同摘要）。没有连接手机，不能把这些计入已执行用例。

上一提交16a32b5完整CI39971396204已成功，包含8项真实Frida主机生命周期和Android构建；它不包含本页随后新增基础代码。dbea45a完整CI39991608445已成功；随后新增的握手前取消修复已通过本地Debug/Release/testAPK/lint，推送后另查CI。

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
