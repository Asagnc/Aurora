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

每次最多读53字节，不增加线程、定时轮询或按对端流长度分配。9项Linux测试覆盖51个切分点、52个截断长度、完整但未关闭、尾部/重复/大尾、附带及截断FD、错票据/进程和部分清理事实；已在b7c3882完整CI40000038379实际通过。NDK30/API36 ARM64编译检查通过；Windows本机9项平台跳过，不计入已执行。尚未被Root guard调用或APK打包。

核实依据：[recvmsg](https://man7.org/linux/man-pages/man2/recvmsg.2.html)、[poll](https://man7.org/linux/man-pages/man2/poll.2.html)，包括短读、非阻塞返回与HUP仍有未读数据。

## 监督观察循环

managed_observe通过一次poll等待owner/worker pidfd、专用报告socket及单调绝对截止；无新线程/轮询扫描。自然到期发送duration，owner死亡/流损坏发送cancel；只发送一次。合作清理最长15秒，force_deadline由未来guard设置为总预算扣除强制kill/reap/remove预留后的截止，观察器不能使用该预留。流损坏不恢复事实；pidfd通知前即使收到完整报告仍等待，超时返回给强制回收路径。worker pidfd可读只叫worker_signalled，调用者仍须waitpid及独立域清理。若worker退出但helper持有报告FD，返回pending而不猜EOF；后续域回收后可再排空。任务成功还须核对实际waitpid状态，不能只用报告中的success位。

这是独立监督原语，尚未连接Root guard/持久记录或Agent。10项主机夹具用自有原生子进程验证；没有Frida注入，不当作新增真实Frida用例。覆盖正常/到期/owner退出、流损坏、无报告/截短、完整报告但不退出、合作清理超时、无效FD/预算及实际15秒取消上限；已在f53d824的CI40001467909独立managed-supervision job实际通过，9项报告流接收也同job通过，完整workflow另查。NDK30/API36 ARM64夹具完整链接、LOAD均0x4000对齐，报告managed-observer-arm64-build.json，不是APK体积或Android执行资格。

## 一次性派发与持久退役原语

managed/journal不依赖Linux镜像或Frida库。调用者提供已固定的Root私有目录FD；open复制CLOEXEC目录，验证Root属主/私有权限，在Root-owned单硬链接.run.lock上非阻塞独占flock。关闭只close引用，不LOCK_UN或unlink，继承子副本关闭不能提前释放父锁。未来guard须持锁覆盖派发到清理/退役全过程，worker在admission前关闭自己的副本。

固定文件managed.intent/ready/released/retired按严格阶段发布。intent/released/retired为107字节ARI1+规范runId+票据SHA；ready为固定164字节ARW1，含runId、票据SHA、LE32/64 guard/worker PID/startTicks及bootId。Root权限是信任边界，不声称能抵抗其他恶意Root程序。仅retired也是有效屏障；无intent却有ready/released、错身份、未知格式或残缺均失败，不转成“未找到”。

每次新文件O_EXCL，先fsync文件再fsync目录才返回成功；部分/失败记录保留以阻止新派发。begin/ready/release不能重放；retire幂等重试也重做文件和目录fsync，避免把上次仅可见字节当已持久化。未来guard在写GO前先发布released；这个间隙崩溃必须记为可能已放行，不能推断“未注入”。retired只阻止未来派发，绝不证明原目标脚本已卸载或任务域已空。

17项Linux Root故障测试已在aeebb08完整CI40003765109实际通过，NDK30/API36 ARM64独立夹具链接/16KiB通过。尚未接实际fork/GO、cgroup guard、最终事实持久发布、Root Binder/Agent/设备资格，Frida仍不ready。核实依据：[open](https://man7.org/linux/man-pages/man2/open.2.html)、[fsync](https://man7.org/linux/man-pages/man2/fsync.2.html)、[flock](https://man7.org/linux/man-pages/man2/flock.2.html)。

## 原生admission与单次GO

launch_prepare只接受已持有Root journal锁/INTENT及已记录的新建task_scope。专用单线程guard fork，子进程PDEATHSIG+getppid关闭准入前父死竞态，关闭自己的锁副本，复用task_scope_enter，给父进程准入事件后阻塞GO。父进程通过真实pidfd/startTicks、guard身份/bootId形成READY并fsync，owner/worker已退出则拒绝；此时worker二进制尚未执行。

release核对同一任务的精确READY，持久RELEASED、再次核实owner/worker后仅发一次GO；句柄在首次尝试就消费，失败不能重试。持久RELEASED与实际GO之间崩溃仍未知，不从marker缺失推断没有注入。launch_close只关闭FD，不替代kill/reap/cgroup移除；准备失败也可能返回仍需收尾的自有子进程。

guard必须保持默认SIGCHLD且无SA_NOCLDWAIT，防止未回收子PID被复用后误杀其他进程；自身不得并发waitpid。子进程清除继承的信号屏蔽，close_range CLOEXEC（复用既有guest执行的同一内核能力）后仅让私有control跨exec，worker bootstrap恢复CLOEXEC并清内部env。普通版本/可执行文件属性由调用者固定验证，不宣称目前已完成worker二进制发布/租约与Android资格。exec前失败只报告NO_SESSION/125，不编造加载；worker进程与域的真实退出仍由guard独立核实。

10项Root真实cgroup/自有原生worker夹具已在c6fce52的CI40005725398独立managed-supervision job实际通过；loaded/unloaded位在此夹具中是协议模拟，不算真实Frida附加。正常用例同时核实非CLOEXEC私有FD不进入worker、control和内部env不进入后续helper exec；错误场景核实未执行、实际等待/回收/移除。ARM64完整夹具已链接；还没有独立guard主程序/最终事实持久发布/Root Binder/Agent/ART。

依据：[close_range](https://man7.org/linux/man-pages/man2/close_range.2.html)、[PDEATHSIG](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)及既有scope/cgroup算法；不使用PID命名空间或Linux镜像。

## 持久worker事实与资源收尾

managed.worker只接收state=1的严格EOF接收器，重新验证票据及READY PID/startTicks，再以Root私有单链接文件写入/fsync。managed.stopped固定123字节，含107字节任务key、精确阶段、worker/domain资源状态、真实退出码（未知为UINT32_MAX）、结束原因和errno；所有保留位/范围/阶段矛盾拒绝。资源CLEAR不等于目标注入清理，NOT_CREATED与UNKNOWN不能转成workerExited。没有worker报告就保留缺失，不从停止记录合成unload/detach。

收尾记录须已经持久退役，精确重试才重做fsync；部分/矛盾文件保留并拒绝覆盖。无意图却遗留worker/stopped文件不允许重新begin。新增8项Root测试，journal累计25，待CI；ARM64夹具链接通过。尚未接完整guard/恢复/Root Binder/Agent，不宣称已可释放App未确认意图。

## mkdir前创建计划

为独立guard接入补齐“mkdir后、完整scope.state落盘前”的崩溃窗口：仅受管路径先reserve随机name和当前parent，再将任务key、guard PID/start/boot、parent dev/inode及变长name/path写入managed.scope-plan（ASP1、FNV校验），fsync后materialize，最后原scope_state_save。旧task_scope_create仍是reserve+materialize，无新I/O。计划常规仅数百字节；Root ownership提供信任，FNV仅检错。共享record上限调为16KiB，但各固定格式读取仍按自身精确大小拒绝。

普通完整记录先完成带inode核实的kill/空组回收，计划恢复再验证旧guard已死/boot/parent dev-inode，仅打开该精确name：缺失或空组可收尾，已占用而无完整inode记录只busy不kill。仅清理计划对应的Root-owned单链接.tmp，保留其他文件；旧boot不触碰当前boot同名内核资源。损坏/错key计划保留，未确认资源不转换为成功。

9项Root cgroup故障用例覆盖mkdir前/后、完整记录、部分tmp、活guard无锁、未知已占用组、坏计划、错票据、重放；待CI，ARM64夹具链接通过。依旧未打包guard/注册Agent或执行Android设备。

## 独立guard开发入口

已接guard_main/runtime/recover/result及专用reaper：coordinator持同任务锁、以自己的pidfd作为owner，fork出的guard独立session/subreaper；先持久意图和cgroup计划/完整记录，再fork阻塞worker、写READY和RELEASED后GO。operation包含启动时间，观察<=250秒，合作收尾<=15秒，总预算留5秒强制收尾；coordinator用guard pidfd与绝对截止，避免guard冻结后永久waitpid。文件系统/内核不可中断I/O不是硬实时保证。

worker的精确EOF报告先落盘，再kill/空组/回收自有后代/移除域和记录。reaper只读取真实procfs里本进程的children，拒绝截断/重复条目，并用waitid(P_PIDFD, WNOWAIT)重新验证可等待子身份后才pidfd_send_signal；不是枚举或杀已有target。Linux源码确认ptrace使用独立ptraced列表而children按real_parent/sibling；仍须真实Frida/Android资格。仅在退出/接管事件之后有界重读后代列表，无后台定时扫描。

recover先持久退役，核实旧guard已退出、boot与worker身份；worker有合作窗口再按pidfd强制停止，完整scope记录回收后处理精确空组计划，最后回收本恢复协调器拥有的后代并发布独立资源记录。不可把资源记录当注入卸载；缺失worker报告的原目标仍活时，App意图保持未确认。既有最终记录不重写，迟到start被退役/一次性意图拒绝。

开发CLI仅Root，worker版本/path/普通exec属性仍由将来的Root facade固定核实；未APK打包/注册Agent，未启用spawn。14项自有模拟worker/实际cgroup测试待CI，包含冻结、父死、孤儿/迁移helper、exec失败/坏报告和无响应；不计为Frida注入用例。ARM64 guard完整链接通过。

核实：[pidfd_open](https://man7.org/linux/man-pages/man2/pidfd_open.2.html)、[内核ptrace.c](https://raw.githubusercontent.com/torvalds/linux/master/kernel/ptrace.c)、[proc children实现](https://github.com/torvalds/linux/blob/master/fs/proc/array.c)。

## 验证

Core新增RootManagedNativeState严格读取164字节READY、52字节worker报告及123字节stopped：匹配票据/boot/进程、合法阶段及资源状态，不从stdout或资源清理推断注入卸载。只有实际worker清理位和独立资源证明齐全才产生现有正常清理证明；缺失报告/未detach/派发前退役仍无正常证明，不误造workerExited。它只接受可信Root读取器返回的原始记录，Root文件路径、权限与锁核实仍由待接facade负责。4项Core故障/成功回归本地通过，核心累计246项、236通过/10平台跳过。新增ARM64 guard自动构建审计脚本，43,152字节、仅libc/libdl、外层16KiB通过，未APK打包/设备执行。证据local-managed-native-state-passed.txt。

再新增真实worker强杀用例：必须保留worker报告缺失、原目标仍活，回收后不能变成成功；真实Frida用例现共24项，新增此项推送后核实。CI增加guard的ARM64构建/源码摘要/ELF审计证据，不把Linux成功代替Android运行资格。

336deb8完整CI40013810311三job成功：独立guard的14项Root主机生命周期与原19项真实Frida用例分别通过，见evidence/0.1.40/ci-40013810311-passed.txt。新增实际Frida穿过独立guard的4项端到端用例：真实hook/持久报告、owner死亡后真实unload/detach、封印快照预检失败、官方Java桥仅执行一次。发送方先匹配READY的PID/startTicks及Root SO_PEERCRED，再交付封印FD；完成后核实报告票据、域记录清除、原目标仍活。新增用例待CI，不是Android ART资格。

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
