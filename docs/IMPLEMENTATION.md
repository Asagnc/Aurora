## 2026-10-23 0.1.37 异步浏览器完整CI通过

9517ea9完整CI39909014575成功，证据evidence/0.1.37/ci-39909014575-passed.txt；含新生命周期/JSON合同/生产JS测试及原有全部主机和Android构建门槛。随后补mjs测试任务输入追踪（脚本变化不能被误判UP-TO-DATE）与跨域导航Android夹具，core/testAPK/lint本地再通过。最新后续提交CI另查。

当前功能/证据/缺失项统一见NEXT_STEPS.md，避免把历史章节中的旧待办重新当作未实现。浏览器异步六项、Root抓包七项仍仅编译待最终手机；没有连接/更新手机或调用真实供应商。接续优先浏览器文件传输研究与实现，新通道/持久化选择仍先确认。

## 2026-10-23 0.1.37 浏览器事件等待与异步JS

用户已确认每次调用独立WebMessagePort，见BROWSER_ASYNC_PROPOSAL。实现awaitPromise显式启用/有界deadline、精确origin/运行ID/文档revision、单次结果与关闭/取消收尾；结果不提供原生权限、网页数据仍不可信。加载等待从50ms轮询改为WebView回调StateFlow，并修复工具/批处理吞掉外层超时取消；同步/异步JSON预算与Unicode截断、字段/深度校验统一，解析移出Main并再次核对文档。

本地核心187项（180通过/7 POSIX跳过）；生产JS在现有Node实际跑14场景；Debug/Release R8/testAPK/lint通过。BrowserAsyncTest六项仅编译，实际WebView消息转移/origin/取消/刷新/renderer与手机性能仍待最终集中验收。详情BROWSER_ASYNC_IMPLEMENTATION.md，证据evidence/0.1.37。手机未连接/更新，仍0.1.32，无真实供应商调用。新增代码CI待查；此前84bcb4d完整CI39838321745、提案3fe2c2f CI39840197721均已成功。

## 2026-10-23 0.1.36 抓包主机资格完整通过，最终真机用例就绪

9ba73c9完整CI39837102395成功：原生组件/离线外层12项，私有netns内部10项全部通过，包含any标准cooked格式、截短统计和空闲CPU预算。证据evidence/0.1.36/packet-snapshot-host-ten-passed.txt。Android Debug/Release/testAPK/lint、核心/既有Linux验证和ELF/对应源码全部通过。

另补Root真机专项至7项，新增取消、断连重连和删除会话；仅编译通过，未连接手机。BPF命名主机/服务可能在编译期解析，已修正Agent工具措辞并建议数字地址/端口；这版Debug/Release/testAPK/lint也本地通过。最终验收步骤已写ROOT_CAPTURE_ACCEPTANCE.md，不卸载Aurora、不清用户数据，手机仍0.1.32。未完成项是Android实际Root发布/生命周期/熄屏与性能对照，不要将主机结果代替手机证据。

## 2026-10-23 0.1.36 主机采集生命周期已通过

ec6a727的完整CI39836086203成功：组件/离线外层12项、私有netns内部9项（空闲截止、严格预算、包数、取消、SIGKILL、非法过滤、接口down、符号链接）、既有Linux/PRoot/核心及Android构建全部通过，证据evidence/0.1.36/packet-root-host-lifecycle-passed.txt。随后补齐上游标准262144抓取长度、snapshotTruncated显式统计、字节预算完成原因一致性，新增any接口与空闲CPU预算检查；本地核心179项（172通过/7跳过）、Debug/Release/testAPK/lint及双ABI ELF/源码包通过，这些新增主机项等下一CI。

手机仍0.1.32，未连接/更新；Android Root、实际文件发布/取消/断连/删除/熄屏和性能资格仍在最后集中验收。不要把主机netns结果写成Android实测。详细实现/边界见ROOT_CAPTURE_IMPLEMENTATION.md。

## 2026-10-23 0.1.36 Root 抓包与 Agent 接入

已实现 packet_capture_root 的严格双端合同、追加Binder协议与原子端点能力、现有Root会话派发/监督/有界唤醒锁、固定运行cwd、worker文件身份/完成状态、选定PCAP属主发布与状态文件持久化、工具账本/日志/中文标题。离线保持普通UID，不扫描或递归chown会话。采集文件完整与任务成功分别检查，取消、硬超时、未知死亡、发布失败不当成功。

本地核心179项（172通过/7个POSIX跳过），Debug/Release/testAPK/lint、双ABI ELF/16KiB与源码包通过。新增6核心项已实跑；新增私有网络空间9项控制场景待CI，Android普通与Root专项4项仅编译，最终集中真机不变。先前76eb16a CI39833896207完整成功，主机离线/worker共11项证据已归档。详细合同与尚未验收项见ROOT_CAPTURE_IMPLEMENTATION.md，手机仍0.1.32。

## 2026-10-23 Root 采集 worker 第一阶段

用户已确认 ROOT_CAPTURE_RUNTIME_PROPOSAL 分工。新增独立 libpcap worker：Root UID 限制、固定 capture.pcap 排他且不跟随链接创建、整包前置字节预算、包数预算、CLOCK_BOOTTIME 截止、signalfd 取消、非阻塞 pcap/poll、有界分批、最终 flush/ferror/fsync 与大小检查、结构化保存量和未知可区分的内核统计。非混杂、4MiB 内核缓冲和100ms缓冲交付是待性能资格验证的初始值，不宣称最优。

Android arm64/x86_64 Debug、Release、测试APK和lint已编译通过，双ABI ELF/16KiB检查通过，对应源码包已生成。新增主机整包边界和缓冲失败测试待 CI 实跑。当前仅原生组件，尚未接入 Root 专用派发、选定产物属主发布和 Agent live 工具，不属于已可用实时抓包。取消退出2、I/O或采集失败1、预期预算或时长完成0；外层硬截止和未知死亡由现有监督器判定。真机受控流量、熄屏、丢包与CPU对照仍留最后集中验收。

## 2026-10-23 0.1.35 写盘资格与下一步控制提案

493cc86 CI39831312910最终完整成功：实际组件/离线分析主机10项、既有核心/签名镜像/POSIX/PTY/namespace/scope/恢复/挂载/GC/PRoot及Android Debug/testAPK/Release/lint、ELF/16KiB和对应源码包通过。主机对照实测未修改上游向/dev/full输出返回0；Aurora派生版拒绝成功，非普通/dev/null正常，两段轮转完整保留20000包。证据evidence/0.1.35/packet-host-ten-write-boundary-passed.txt。这是主机I/O及组件资格，不是Android Root实时抓包验收。

现有组合已接固定组件、Agent packet_inspect、日志/产物、中文任务标签；新增Android3项与日志映射仍仅编译，手机未更新/连接，保持0.1.32，最终集中验收。实时抓包尚未接，不能拿上述10项当作全功能完成。

下一步控制方式已整理ROOT_CAPTURE_RUNTIME_PROPOSAL.md（待确认）：libpcap+小型原生采集worker直接处理整包字节/时长预算、signalfd/timerfd/poll与结构化状态，tcpdump继续成熟离线分析，复用现有Root任务/日志/属主发布，不自写mmap或另建守护进程。此项细化并调整先前“tcpdump实时写盘”的控制职责，属于新增原生worker选择，按用户最初要求先确认，不提前实现。

## 2026-10-23 0.1.35 写盘结束/轮转边界补强

Agent离线提交6e8500b CI39829765714最终完整成功，含新增的字面过滤/选项隔离，主机离线共7项成功。继续核查真实采集前的写盘边界：派生tcpdump在退出/轮转核实flush/ferror，普通文件fsync后关闭；轮转更新当前dumper，关闭前清空信号处理使用的全局指针，避免复用已关闭dumper。回调仅检查stdio已有错误，不逐包刷盘。原上游缓存不改，替换次数严格验证；非普通输出不要求fsync。工具中文标签“分析网络数据包”已接。

本地Debug/Release/testAPK/lint通过；新主机三项（/dev/full不能成功、/dev/null可写、两段轮转保留20000包）及未修改上游/dev/full对照待CI，新合计10项不能提前记通过。强杀仍不承诺完整；Root实时抓包与任务发布/属主/异常、最终手机和性能资格继续，不把源码组件/离线分析标成实时抓包完成。手机未操作，仍0.1.32。

当前Debug SHA256 16ffa7525f0a1da1e2c45e1fc925eb3f73af52729ba39338653d2186e6ae0aa4。

## 2026-10-23 0.1.35 Agent 离线包分析接入

Agent 已注册 packet_inspect，普通UID独立原生进程解析会话PCAP/PCAPNG：字面BPF以--隔离选项，默认100/最多5000个匹配包，512MiB输入和30秒预算，无DNS/服务名查询。复用app.commands全局限流、现有前台任务/取消、工具账本产物关联及简洁详情。输入拒绝越界/符号链接/非普通文件；包数不等于扫描字节，超时及解析错误保留真实状态。没有把实时抓包能力标为已实现，也不为离线解析要求Root。

本地Debug/Release/testAPK/lint再次通过，双ABI ELF/16KiB及对应源码包通过。PacketNativePackageTest当前3项（版本、真实过滤/Agent字面过滤/原包保留、派发前越界拒绝）与CommandLogDeviceTest第五类产物映射仅编译，手机仍0.1.32、最后集中验收。e04bbba CI39828476867的主机五项离线步骤已成功，该CI最终完整成功，证据evidence/0.1.35；新增两项主机Agent参数风格/过滤选项隔离待下一CI。Root实时采集/预算/文件属主/取消/异常落盘/受控流量与性能仍继续。

## 2026-10-23 0.1.35 组合抓包组件接入（资格分阶段）

用户确认“我觉得可以组合都取最优”：libpcap底层采集/过滤、tcpdump成熟写盘/离线读取、Aurora Agent/任务/产物适配；不并行运行多个采集引擎，不据此引入AF_XDP或常驻服务。ROOT_CAPTURE_PROPOSAL已标组合确认。先接官方stable tcpdump4.99.7/libpcap1.10.7源码与SHA锁、NDK静态链接独立PIE、双ABI nativeLibraryDir分发、许可/源码头告知与对应源码包。Android版本0.1.35/code36。

ExternalProject隔离上游配置；派生FindPCAP严格使用当前目标ABI库，避免宿主pkg-config/pcap-config。Windows生成器按宿主选择NUL，私有TEMP/TMP修复win_flex并行ABI固定临时名冲突。无上游缓存修改。未增加可选OpenSSL/libsmi或蓝牙/USBmon/RDMA等采集后端依赖。二进制含TPACKET_V3支持字符串，只证明编译支持，设备实际模式待测。

本地Debug/Release/testAPK/lint及双ABI ELF/16KiB与源码包通过；新增主机离线五项接入CI（版本、匹配原包、不匹配、非法过滤、截断输入），尚未运行；新增Android普通UID两项仅编译。详见PACKET_NATIVE_BUILD.md。当前只是组件包装，不宣称Root实际抓包可用：Agent结构化抓包、受控Root运行/预算/取消/异常、产物归属、详情及吞吐/丢包/功耗仍继续。手机仍0.1.32，按用户要求最后集中验收，未连接或安装更新；无真实供应商调用。

本地当前Debug SHA256 1fcd62b5f0b4f01c7f4d246f1db053827308158268abb3de161cf337b8c75037。

## 2026-10-23 0.1.34 本地与主机资格完成，手机留最终集中验收

07adcd8 的 CI39825123468 最终完整成功，包括固定签名镜像/POSIX提取、现有 PTY/namespace/scope/恢复/挂载/GC、5项 ptrace 内核先决条件、4项实际派生 PRoot 生命周期、Android Debug/testAPK/Release/lint、双 ABI ELF/16KiB和对应源码包。实际 PRoot 四项覆盖启用/禁用 seccomp 的 tracer SIGKILL，以及 guest 正常退出37后两层 setsid 后代停止；使用 pidfd 验证原进程退出，不靠 PID 字符串轮询猜测。证据 evidence/0.1.34/proot-host-lifecycle-passed.txt；首次 host memset_explicit 构建失败保留。主机用 glibc、非 Android shmem、独立 fixture，不等于 Android 实际 Ubuntu/PRoot 或持久恢复已通过；PRoot executionReady仍 false。

本地173核心：166通过/7 Windows POSIX跳过，已带固定实际 Ubuntu 镜像重跑；Debug/Release/testAPK/lint通过。当前 Debug SHA256 88960c0af5f93fd200ef6bdc0896f739162e370dc7534784d0794e7eddb4d74c。Root 连接不可变快照修复、命令输出非法尾字节分类修复与新 Android 断言均仅编译，未更新手机；手机仍已验收0.1.32。最终补 RootMetadataDeviceTest4、RootConnectionTest和NativeCommandTest及既有Agent/Linux/PTY/恢复回归。无真实供应商调用。

用户对抓包提案要求“多处对比给我最好的”，尚非组件审批。已补 Linux/libpcap/tcpdump/PCAPdroid/Termux/Eta/DSHA 的来源与取舍，仍推荐 tcpdump4.99.7+libpcap1.10.7；1.11.0官网为testing。详见 ROOT_CAPTURE_PROPOSAL.md，等待对补充比较的确认，不先接入依赖或抓包二进制。五分钟是复用现有命令的第一阶段边界，不能写成最终长期抓包已完成。

## 2026-10-23 命令输出预览 UTF-8 修正（0.1.34）

NativeCommandRunner 旧逻辑在截断预览时反复移除末尾 1–3 字节并重试解码，可能把 0xff 等损坏字节隐藏成正常文本。改为复用 core 的 RFC3629 尾部边界检查，仅省略合法但未完整的 UTF-8 尾部；二进制/非法序列继续返回 text=null，原始产物不改变。去掉重复 decoder 与异常重试。新增 core 三项覆盖各宽度标量、非法尾部和 ANSI/空白；新增 Android 原生命令一项保留非法尾字节与精确产物，已编译待最终手机验收。

本地 core 173项，失败0、错误0、跳过7（Windows POSIX项），已以固定实际 Ubuntu 镜像输入重跑；Debug/Release/testAPK/lint通过。当前 Debug SHA256 88960c0af5f93fd200ef6bdc0896f739162e370dc7534784d0794e7eddb4d74c。CI39823929519 的实际 PRoot 主机测试在构建阶段失败：runner glibc 缺 Bionic 的 memset_explicit，0 项运行，原失败保留。补仅主机测试用 volatile 清零适配与禁止未解析符号检查；将实际 tracer 测试提前至 Android 全量构建前。四项运行结果仍待新 CI，不提前记通过。没有手机或真实供应商调用。

## 2026-10-23 0.1.34：PRoot 启动边界与 Root 连接一致性

PRoot 派生源码现在同时要求 EXITKILL 与初始子进程 PDEATHSIG/父 PID 复核，覆盖追踪选项安装前后 tracer 死亡；不修改上游缓存。新增 Linux 主机独立 tracer 五项验证，包含两层 setsid 后代、SIGKILL 的两种选项路径、SIGTERM，以及启动保护前/后死亡。1b43db0 的 CI39822710274 已观察这五项步骤成功，原生 PTY、namespace、scope/recovery/mount/guest-PTY/GC 步骤亦成功；该 CI 最终完整成功。它们是内核先决条件证据，不是实际 PRoot/Android 全链路验收，PRoot executionReady 仍 false。

修复 RootController 多个独立 volatile 字段在断开/重连间可能组合旧 Binder 与新身份/能力的竞态：身份、协议与 Binder 以单个不可变快照发布/清空。Linux 命令和 PTY 一次捕获同一快照的身份、执行与恢复客户端，票据不再二次读取连接。沿用原 Binder/SQLite/恢复协议，无新依赖或持久化格式。RootConnectionTest 补断开后的客户端清空及已捕获身份保留断言；仅编译，留最后集中真机运行。

本地启动保护版 Debug/Release/lint、双 ABI ELF/16KiB与对应源码包通过；Root 连接修改版 Debug/Release/testAPK/lint通过，0.1.34/code35 最终重建亦通过；Debug SHA256 434c717b2cfeda92841bc327ed790d301d6942e1afaf1928fd5619d15fd22066，双 ABI ELF/16KiB与对应源码包通过。新增实际派生 PRoot 的 Linux/glibc 主机编译与四项生命周期测试已接入 CI，当前仅 Python 编译检查，不提前记运行成功。手机仍 0.1.32，未连接、安装或控制手机，无真实供应商调用。下一步继续 PRoot 实际生命周期与普通 UID 能力边界、工具包资格、最终手机与长时性能/真实协议验收；不能把内核测试替代完整产品完成。

## 2026-10-23 PRoot tracer death prerequisite

Previous head 7c60f65 CI39820489556 completed successfully. PRoot now builds a checked derived tracee/event.c with mandatory PTRACE_O_EXITKILL in both default and seccomp ptrace option paths. Unsupported kernels reject startup instead of dropping this requirement. Upstream source remains unchanged; corresponding source includes the adapter. Local Debug/Release/lint and dual-ABI ELF/16KiB verification passed. This is a prerequisite only: no SIGKILL descendant runtime qualification, startup-race proof or Android PRoot acceptance is claimed; executionReady remains false. No phone actions; final combined device acceptance remains pending.

## 2026-10-23 0.1.33元数据回收主机资格完成，手机最后集中验收

23dd228完整CI39818966808成功：原scope/recovery/mount/guest-PTY31与新增GC11项通过；普通PTY8/namespace4、核心/实际签名镜像、Android Debug/testAPK/Release/lint及双ABI源码打包同样通过。原缺.recovery.lock导致4项拒绝回收的失败记录保留。见LINUX_METADATA_GC.md和evidence/0.1.33。Native已验证不等于Android Root/SQLite完整链路验收。

后续应用侧修正：扫描异常立刻关闭/清除游标，过期/关闭先移除登记，临时输出清理错误不掩盖原始失败；每个候选在扫描和删除前各读取解析一次，减少同阶段重复读取但仍重新校验hash/epoch。普通Binder拒绝新接口及Root部分暂存续清测试已补，Root专用共4项，全部只编译待真机；本地170核心（163通过/7POSIX跳过）、Debug/testAPK/Release R8/lint通过。Debug SHA256 84c690bcb6088917fdf474a56bec00c540b922aa6e713f487d7e891666925e3a，双ABI ELF/16KiB与对应源码包通过。应用侧后续修改的新CI另查。

用户最新要求手机最后集中验收，覆盖前面请求连接的安排；不要再为GC/小改逐轮索要连接，不操作或更新手机。目前设备仍上次已验收0.1.32。最终集中跑RootMetadataDeviceTest4、普通Binder/SQL、既有Agent/Linux/PTY/恢复回归；测试仅指定fixture记录、无付费API。继续PRoot完整生命周期/外部Root撤权/手机联网镜像/长时功耗压力/供应商缓存等剩余项；不能把GC主机通过写成所有功能完成。
## 2026-10-23 回收主机首次失败修正，真机留最后

用户最新决定：真机最后集中验收，不再为本轮GC请求连接或更新手机。d05d5f4的CI39804584434：原31项scope/recovery/mount/PTY通过；新GC11项中4项正常回收失败，原因是scope_state_recover会创建.recovery.lock，GC平面白名单漏列。补齐已知原生锁文件并让主机夹具显式包含它；原失败保留evidence/0.1.33。没有把拒绝回收记为成功，也没有降低未知/链接/挂载保护。

修正后本地core/Debug/testAPK/Release R8/lint成功，新CI待查；GC完整主机资格与真机仍不得提前标通过。Linux设置补Root重连提示和操作错误说明。当前手机仍上次已验收0.1.32，未进行任何新手机动作。
## 0.1.33 Root元数据回收实现，待主机/真机资格（2026-10-22，优先接续）

用户已确认LINUX_METADATA_GC_PROPOSAL推荐策略。接入AIDL末尾可选linuxMetadataProtocol1、Root单游标16项分页/30秒被动过期/显式finally关闭、App SQLite按候选ID筛除未确认run、Linux设置显式清理按钮。只选择已证明死亡的旧epoch，保留当前/未知记录。原生--gc持run flock验证scope恢复及stopped，原子移入Root私有linux-runtime-gc暂存后平面白名单删除；--gc-trash续做部分删除。稳定GC父目录锁不删除，防止child锁unlink/rmdir窗口让新旧收集器分锁；statx挂载ID拒绝目录/文件绑定挂载，不遍历未知树、链接或多硬链接。owner退役标记及workspace锁inode不删，不逐命令全局扫，不新增依赖/schema/常驻进程。

本地core、Debug/testAPK、Release R8和lint通过；新增原生主机11项（完整/部分删除、锁占用、未知/链接/硬链接/目录、模式、缺证明、文件/目录挂载、缺失路径等）及Root真机3项（当前epoch保留、旧已停记录删除且迟到请求不重建、SQL待确认保护后恢复再删），仅编译未运行，不能记GC已验收。已异步请求连接手机，当前ADB无设备；等待用户回复，手机仍已验收0.1.32。真机用选定fixture IDs，不扫用户历史；测试中只强杀已验证的Aurora自身Root服务PID以建立旧epoch，不动外部进程。运行前确认没有其他pending Linux任务。

上一内存锁提交2d3135d的CI39796653039完整成功，证据提交a63e109的CI39795463257成功。当前新增GC需自己的CI/native资格；Linux host脚本含mount/unshare测试，Windows本地不能冒充已运行。下一步先查新CI，再手机install-r运行RootMetadataDeviceTest/普通Binder与SQL相关回归；资格通过后再称可用。RootMetadataMaintenance取消时临时输出目录NonCancellable清理，App finally关闭游标；未知/不确定情况保留。PRoot/外部撤权/网络镜像/长期性能与真实供应商缓存仍未完。
## 2026-10-22 Root会话锁内存清理与磁盘GC提案（优先接续）

修复RootWorkspaceLocks按owner永久保留Mutex的内存增长：复用新core KeyedMutex，注册时统计持有者及等待者，正常/异常/取消后引用归零才移除；不删除磁盘FileChannel锁文件，也不把等待者留在旧锁、后续调用放到新锁。3项JVM测试含已持有时取消等待者、其他key不阻塞、1000个结束key释放、取消/异常收尾及8线程4000次跨key交错。核心170：163通过/7POSIX跳过，Debug/testAPK/Release R8/lint通过。无新依赖/schema，无新真机结果；手机仍上次已验收0.1.32，不安装当前未专项验证APK。

磁盘回收属于新的持久化清理策略，比较方案已写LINUX_METADATA_GC_PROPOSAL.md：建议仅回收已证明死亡的旧Root epoch、无SQLite待处理意图并通过native清理证明的运行目录，保留当前epoch/不明记录、owner退役屏障和workspace锁inode；有界显式维护，不逐命令全盘扫，不引入新常驻进程/新tombstone索引。清理尚未实现或执行，按最初要求提案推送后异步请求确认；等待期间不擅自删已有Root元数据。接续从用户对该方案的答复继续，另有PRoot生命周期/Root撤权/联网镜像/长期性能功耗/供应商缓存等既有待办。
## 0.1.32 真机并发恢复专项完成（2026-10-22，优先接续）

用户连接GT6SE后，保留数据覆盖0.1.32/code33；AgentLinuxDeviceTest5、LinuxRunRegistryTest5、CommandLogDeviceTest5共15项全部通过。实际验证：恢复本会话不停止另一会话的Linux PTY；显式恢复与终端完成回调共享同一证明不误报失败；Root探测输出关联、owner筛选/计数、SQLite重复完成与四类日志映射持久化通过，原批命令/PTY/超时/Root图片Agent场景同时回归。详见DEVICE_LINUX_RECOVERY_2026-10-22.md和evidence/0.1.32/device-scoped-recovery.txt。

Debug APK SHA256：4086f9f306620b4930441c2f160a6eaf1490e196be36ecc133e8036caa06f554。手机留主应用0.1.32，测试辅助包已卸载并打开MainActivity；未清数据/卸载主应用，未使用付费模型或修改Root授权。上一提交60fee70完整CI39793142166成功；f3f4e51的CI39794753012最后仍queued，证据提交不改变应用代码。下一步仍为PRoot完整生命周期、Root元数据GC、外部Root撤权、手机官方镜像联网下载、长时性能功耗和真实供应商缓存等，不把15项专项当全部功能已完成。
## 0.1.32 会话恢复范围与重复完成确认（2026-10-22，优先接续）

修复Agent linux_environment recover_runs原先遍历全局待处理项、可能停止其他会话终端的问题：SQLite按owner通过现有唯一索引选取/计数，Agent只恢复和显示本会话任务；设置页用户显式全局恢复入口保持原语义。修复probe_root/recover_root的任务输出关联被ToolRunStore白名单拒绝，加入linux_environment并仍禁止非命令类图片工具关联。修复显式恢复与PTY退出回调同时收到同一stopped证明时第二次完成误报失败：有活动记录仍严格匹配完整ticket；已完成记录幂等返回，不触碰其他租约。

0.1.32/code33本地core、Debug/testAPK、Release R8和lint通过；双ABI ELF/16KiB通过。新增SQLite双owner筛选和并发完成测试，扩展日志映射持久化覆盖四种工具/重复写拒绝，新增两项真实Agent/PTY的跨会话保护与同会话显式恢复测试。仅编译，尚未运行新增设备项；ADB无设备，已异步请求连接，待用户回复，不重复索要授权。手机仍为已验收0.1.31，未安装新APK。证据evidence/0.1.32。

上一提交60fee70的CI39793142166：已观察所有步骤成功，包括官方源码下载/缓存保存、Android构建/lint、原生资格与源码包；服务聚合状态最后仍in_progress，不能提前记完整成功。新提交CI另查。下一步手机连接后只install-r并执行AgentLinuxDeviceTest、LinuxRunRegistryTest、CommandLogDeviceTest；不使用付费模型，不卸载主应用/清数据。其后PRoot生命周期、Root元数据GC等既有待办仍未完成。
## 0.1.31 Agent Linux 真机接通与同轮环境固定（2026-10-22，优先接续）

已在GT6SE/FolkPatch保留数据覆盖0.1.31/code32。生产AgentExecution +脚本ModelTransport（无网络、无真实密钥）实际调用Root Linux批命令、交互PTY读写、图片发布、SQLite工具记录/协议历史：3项通过；环境固定/取消释放/启动清理保留未确认native租约：3项；最终构建再跑以上6+日志映射5=11项通过。普通91项在本轮环境固定实现后通过，最终状态展示/异常保留修正后针对性11项复验；不冒充最新构建重复跑全套。初始2项失败仅为fixture错把AgentExecution的UI临时投影当成已刷新消息；改查实际SQLite已持久消息后通过，原失败保留。见DEVICE_AGENT_LINUX_2026-10-22.md和evidence/0.1.31。

实现：首个Linux工具调用时懒固定共享Root代，用现有lease保护到整轮结束；每个命令/PTY另有原持久票据/租约，PTY可超出Agent轮次继续，未确认native run不因进程重启被释放。启动ready前仅回收没有linux_runs引用的进程内pin。命令任务卡片采用guest退出状态及有界stdout/stderr投影，超时/中断/未知不误标成功，原始JSON仍保留。Root图片选定导出/Bitmap解码/再次导出通过，传输临时副本已删，guest原图仍UID0。无依赖或schema变更。

核心167：160通过/7POSIX跳过；Debug/testAPK/Release R8/lint通过；双ABI ELF/16KiB与对应源码包通过。CI39790590613失败在Samba源码超时/503，非测试失败；新增官方actions/cache6.1.0精确SHA键的restore→原SHA校验→save，缓存命中和损坏拒绝本机已测，缓存未命中仍依赖有界官方网络。新提交CI另查，不宣称已修复全部外网问题。

下一步：PRoot完整生命周期、Root元数据GC、管理器外部撤权、手机官方镜像下载成功、长期压力功耗/真实性能与供应商缓存验证；不重复询问已批准架构。最终手机留主应用0.1.31，测试辅助包验收后移除；不清用户数据。用户要求常规进度少汇报，持续推进；仅新底层确认/阻碍/验收联系，工具进行中仍必要简短更新。
## 2026-10-22 Agent Linux 交互终端入口

新增稳定 terminal_start_linux 工具，默认/bin/sh、固定guest目录/环境、期限最多270秒、awakeMs默认0且不得超过期限。复用TerminalManager已验证的Root Linux PTY/SQLite租约/前台服务/VT/持久日志；后续由terminal_read/write/resize/stop/list操作，用户在现有终端页观看。与Android terminal_start分别命名，不偷偷切换后端。不新增依赖/服务/数据库schema。修正terminal_stop描述，区分Android进程组与Linux任务cgroup的清理边界。

新增2项核心测试覆盖默认值、参数字面保留、单次启动、参数/尺寸/期限/唤醒预算和禁止自定义环境；本地core、Debug、Release/R8、lint通过。未新增真机/真实模型验收；Linux PTY底层沿用0.1.30既有设备证据，不等同新Agent入口已验收。上一提交CI39790087660推送前仍运行。下一步仍需Agent端到端本地fixture/真机专项、整轮Linux代固定、PRoot完整生命周期、Root元数据GC；手机不覆盖未经本轮专项的构建。
## 2026-10-22 Agent Root Linux 批量工具接入

新增稳定 command_execute_linux 定义，复用已批准/真机验证的 LinuxRunManager、Root task scope、SQLite意图/租约和文件通道。工具在每轮固定列表中提供，运行时重新检查Root及已安装代；缺能力明确失败，无PRoot替代。argv绝对guest路径、最多128参数/64KiB、期限1..270000ms；固定cwd/HOME/tmp；派发前用同一run UUID关联任务输出目录。停止证明与命令成功分别返回，guest非零exit保留，超时/中断/无completion不伪造成功，不自动重放。当前按每条命令预留代及租约，尚未实现整轮Agent固定Linux代。

本地新增3项核心测试：退出与清理语义、参数拒绝、关联先于单次派发；core全套及Debug/Release R8/lint通过。上一轮CI39788741945完整成功。此次未跑真机或真实模型，不把已验证底层等同于新增Agent端到端验收。UI交互Linux终端入口、整轮版本固定、PRoot全生命周期、Root元数据GC仍待做；当前手机仍保留0.1.30已验收版。
## 0.1.30 Android16实际Linux与PTY资格（2026-10-22）

GT6SE实际Ubuntu命令3/PTY3/Root服务强杀恢复不重放1、Root原生命令终端5、连接+原属主镜像复核2、限定owner恢复1、普通88和浏览器恢复1通过。修复Context根目录canonical路径及Android ICU UTF8续读；测试禁用不可用JDK Files文本便利方法，清理不掩盖原错。手机联网镜像下载超时，改用同SHA官方离线归档的内部验收输入，保留失败记录。核心161：154通过/7POSIX跳过，Android构建/lint/ELF通过。Agent/UI Linux仍internal，PRoot/GC/外部撤权等未完。见DEVICE_LINUX_2026-10-22_FINAL.md和evidence/0.1.30。

## 2026-10-22 CI源码传输修复

39782148102的核心及Linux主机阶段成功，Android因Samba HTTP2 REFUSED_STREAM源码下载失败。CI预下载talloc一次，以HTTP1.1有界重试两个官方端点，原SHA在脚本和CMake均保留；绝对本地归档输入不改变依赖版本。本机实际下载/校验/全Android构建通过，新CI另查。ADB无手机，已请求连接后开展用户授权的专项，无新设备通过结论。

## 0.1.29 Linux PTY应用链路（2026-10-22）

已接Root Binder可选终端协议1、激活FD传输、SQLite run票据与租约、确认guest完成后才释放、取消join恢复、现有终端记录与Linux标签。原生timedOut明确区分guest期限。无新依赖或schema迁移，内部startLinux尚不暴露UI新建/Agent后端。核心159：152通过/7POSIX跳过；Debug/testAPK/R8Release/lint通过，Root实际PTY3项只编译，普通88门槛未实跑。上一版完整CI39780230312成功，本轮CI另查。见LINUX_PTY_APP.md。executionReady=false；无新手机结果。

## 2026-10-22 Linux PTY原生组合

新增--run-pty控制终端移交与前台guest组，复用已有PTY传输与独立scope监督。新增Root主机六项及原有25项在CI39779614901通过（完整Android当时仍运行），普通PTY8/namespace4也通过。旧CI被新提交取消。Android本地双ABI Debug/testAPK/R8Release/lint、ELF/16KiB及对应源码打包通过。应用Linux终端/Binder票据与租约尚未接通，executionReady=false，无新真机。详见LINUX_PTY_NATIVE.md。

## 2026-10-22 Root Linux运行验收准备

真实镜像专项新增三项，覆盖guest退出/文件/导出/版本编辑/删除、超时及取消后台子进程与租约。Android测试APK本地编译通过，尚未真机执行；见LINUX_RUN_QUALIFICATION.md。83aa2c1完整CI39755532891成功。无产品行为变更，不递增App版本号。

## 0.1.28 Root批量文件通道（2026-10-22，优先接续）

用户已确认LINUX_WORKSPACE_ACCESS_PROPOSAL.md推荐方案，不再询问此选择。已删除每命令递归归还属主，收尾只处理固定元数据。新增Root列目录/批量读/版本编辑/选定导出/删除、跨Root进程文件锁及持久retirement防迟到复活。Agent原文件工具按.aurora-linux路径分派，读保持顺序、混合域编辑写前拒绝；文件浏览和图片发布已接。Root丢失时待删记录保留、其他启动恢复继续。详见LINUX_ROOT_FILES.md。

核心155：Windows148通过/7POSIX跳过；新增Android普通4只编译（切片/FIFO链接/待删保留/无关删除恢复），普通门槛88；旧Binder普通fixture拒绝新Root文件接口。Debug/testAPK/R8Release/lint、双ABI ELF检查通过，证据evidence/0.1.28。d841962完整CI39750999177成功。无新真机；任意Agent Linux命令仍未开放、Linux PTY未接，executionReady=false。Run能力2/File能力1需新Root服务。

已补UTF-8切片续读：普通/Root文件共用单次CharsetDecoder，截断中文/emoji时只推进已返回字符，真正非法字节与EOF损坏仍为非文本；新增核心3用例。用户从设置重新连接Root后自动重试待删会话，Root仍不可用则继续保留。

下一步Root派发/真实Ubuntu/批量文件/选定图片/撤权/删除专项、Linux PTY与PRoot生命周期；当前Root运行与文件tombstone/锁元数据保留，GC需旧epoch死亡和无待处理记录资格，不能删除活锁或误放迟到请求。批量文本/路径32KiB预算，图片选定导出32MiB；大文本FD传输尚未实现，不宣称性能领先。

## 0.1.27 应用Root派发与运行账本（2026-10-22，优先接续）

22fad47完整CI39747188832已成功。新增SQLite v6原子运行意图+租约、Root Binder start/recover、服务epoch与取消join/旧进程死亡屏障、单次Root票据/迟到请求拒绝、原生scope恢复后确认、会话可写目录和显式恢复入口。App启动保留有租约的待删会话而不整段启动失败。见LINUX_APP_RUNS.md。

核心147：Windows固定sample重跑140通过/7POSIX跳过；Android普通新增4只编译、门槛84，普通Binder拒绝新Root入口用例已扩展。本地Debug/testAPK/R8Release/lint及ELF/源码打包通过。无新真机或Ubuntu guest运行证据，Agent任意Linux执行仍未开放，PTY未接。

**新选择待确认**：当前内部验证路径在scope退出后递归归还工作区属主，存在大目录收尾成本；不把它当最优方案。已研究并建议保留Root属主、由内置Root批量文件通道按需读写/导出/删除，见LINUX_WORKSPACE_ACCESS_PROPOSAL.md。这是会话可写文件访问的新选择，不重复审批共享镜像属主。确认后替换验证路径，再做Root真机资格/PTY。Root运行tombstone暂保留，后续按旧epoch死亡与无租约资格GC。此状态不可标记完整Linux就绪。

## 2026-10-22 原生启动与恢复互斥

运行协调进程在fork前取得.run.lock，监督继承，worker在入组前关闭。恢复也必须取得同锁，堵住scope记录出现前空目录被误当作已退出的窗口；两层监督死亡后worker不持锁，仍可恢复。新增主机1项“持启动锁、无scope记录也拒绝恢复”，挂载/命令组12，加既有cgroup6/恢复7共25。最新本地双ABI Debug/Release/lint通过。5a5b381的Linux主机阶段（含宿主FD/环境不继承、超时子进程PID实查）已通过；本次新增锁测试和完整CI状态待查。无新真机或Ubuntu26.10 guest证据，应用业务租约/PTY未接，executionReady=false。

## 2026-10-22 会话删除租约屏障

SpaceStore删除与崩溃删除恢复均拒绝仍有Linux租约的会话，防止ON DELETE CASCADE提前抹掉未确认进程的环境保护记录。新增Android2项只编译，普通门槛80；本地Debug/testAPK/R8Release/lint通过。命令主机测试追加验证显式宿主FD和环境变量不会继承；不增加常驻进程或轮询。业务派发/租约创建与恢复闭环、Root文件清理、PTY仍未连接；尚未约真机。f3d1fb3 CI的Linux命令/挂载11、cgroup6和恢复7已通过，完整Android阶段当时仍在运行。

## 2026-10-22 原生guest业务命令入口

618c843完整CI39745288718成功。新增原生--run：真实guest argv/输出/退出码、显式环境/cwd/FD关闭、有界超时及cgroup收尾；Root专属单次目录持久generation/runId，确认scope空且退出后才写completed.json，双监督死亡恢复不伪造完成。见LINUX_GUEST_COMMANDS.md。新增主机4用例，挂载/命令组11；本地Debug/Release/lint通过，CI另记。尚未接应用Binder业务派发/SQL运行租约/PTY/Root会话文件清理，Agent executionReady=false，无新真机结果。下一步必须先闭合这些路径，不将原生入口称为完整Linux功能。版本号仍0.1.26。

# 2026-10-22 guest ELF资格补充

固定chroot shell fixture验证动态ELF加载、干净环境、cwd、产物和退出码37；缺失加载器失败回收，新增主机2项（挂载组7）。本地Debug/Release/lint通过；CI及设备结果另记。不是Ubuntu镜像或业务命令执行，生产租约/PTY尚待接入，executionReady=false。上一版4a783c2完整CI39741811061通过。

# 0.1.26 后端环境代发布（2026-10-22）

Root/普通独立代、原始属主恢复、事务发布和受租约保护的Root清理已接，Root文件操作票据/取消屏障复用原协议，新增Linux能力协议2。见LINUX_ROOT_GENERATIONS.md。核心143；Android普通新增5仅编译，普通门槛78。没有新真机或真实guest证据，Root命令/PTY的运行租约与会话回收仍待接，executionReady=false。

# 0.1.25 chroot挂载资格（2026-10-22）

Root/PRoot后端共享属主代方案已获确认。新增固定C chroot/只读根/可写会话绑定/FD挂载与cgroup收尾测试，主机新增5项，旧13项继续；见LINUX_CHROOT_MOUNTS.md。未运行真实Ubuntu ELF，未接属主代发布与业务租约/命令/PTY，executionReady=false，无新真机证据。

# 0.1.24 scope恢复（2026-10-22）

新增Root专属持久记录和身份复核、协调兜底/两层死亡后独立恢复、入组前父死亡保护及Agent recover_root。见LINUX_SCOPE_RECOVERY.md。新增主机7故障+既有6生命周期；Android正常恢复用例仅编译，普通门槛73。业务chroot/PTY/租约及Android撤权仍待验证，不标记完整执行就绪。

# 0.1.23 cgroup监督资格（2026-10-22）

新增固定任务scope原生程序与Agent probe_root，包含私有mount/pidfd/subreaper/脱离后代/kill空事件及独立监督收尾。新增主机6项和Android普通1+Root1仅编译，普通门槛73。见LINUX_SCOPE_IMPLEMENTATION.md。未开放任意Linux执行，监督强杀持久恢复及chroot/PTY仍待继续。

# 0.1.22 Root 辅助安装（2026-10-22）

普通文件由App创建保留标签，Root单次批量补硬链接；固定镜像独立复核、身份票据、取消确认后清理、失联保留与恢复已接。见 ROOT_LINUX_INSTALL.md。核心136，Android新增普通5+Root镜像专项1，仅编译；普通门槛72。本轮无新真机/guest/chroot性能结论。完整Linux执行及cgroup原生监督仍待继续。

# 0.1.21 真机Linux资格（2026-10-22）

普通3+Root3共6项真机通过，失败记录保留。Android空间查询改StatFs，硬链接能力改下载前检查并明确Root安装尚未接；Root硬链接/cgroup基本操作/私有挂载实测成功。PID空间本机缺失，用户批准任务专属cgroup方案替代必需PID空间。Root probe只查挂载，完整执行仍未就绪。见DEVICE_LINUX_2026-10-22.md及LINUX_CGROUP_PROPOSAL.md。完整本地Android构建/R8/testAPK/lint通过，不扩展为guest/chroot/事件恢复或性能验收。

# 0.1.20 Root 命名空间资格（2026-10-22）

已确认PID命名空间生命周期设计；新增固定原生Root探测、Agent probe_root和既有日志产物，PRoot探测接环境版本租约。没有任意Linux业务命令/chroot。新增Linux Root主机4项，Android普通/Root/显式镜像专项各1，设备未运行前不写通过。详见LINUX_ROOT_LIFECYCLE.md与LINUX_IMPLEMENTATION.md；构建和CI结果以对应证据为准。

# 0.1.19 PRoot 原生构建（2026-10-22）

批准的三组件固定源码摘要，NDK30/API36双ABI构建，PRoot/loader/talloc随APK打包。原生页与Agent固定离线probe入口已接；执行就绪仍false。双ABI Debug/Release ELF、依赖及16KiB校验与独立ARM64适配器构建通过；完整Android构建/R8/testAPK/lint通过。新增Android冒烟仅编译，主机编译不能替代Android实际执行。见LINUX_IMPLEMENTATION.md、LINUX_NATIVE_BUILD.md及evidence/0.1.19。

# 0.1.18 Linux 基础环境（2026-10-22）

Linux方案已获用户认可。本阶段接入官方固定摘要/签名验证、tar.gz链接与权限规则、ELF/解释器检查、SQLite v5版本/恢复/租约元数据、原生安装页和Agent环境管理。详见LINUX_IMPLEMENTATION.md。基础环境安装不等于Linux可执行；PRoot打包、Root挂载/监督、命令与PTY租约仍待继续实施，executionReady保持false。核心126（Windows125通过/1POSIX跳过）；新增Android2项仅编译，设备集中验收不变。

# 0.1.17 Skills 来源与文件浏览（2026-10-21）

显式GitHub ref和子目录接入UI/Agent，斜杠分支与URL路径可明确区分；旧来源JSON/ID兼容，不新增SQLite迁移。修复archive/releases路径误判，来源标明正式发布/快照。skills_read支持省略path搜索/分页全部声明资源；用户有文件浏览与分段阅读页。详情订阅目录版本事件，更新/回滚后刷新真实状态。核心115；完整构建证据与边界见evidence/0.1.17。新增界面仅编译，未冒充实机审美/交互验收。

# 0.1.16 Skills 稳定性（2026-10-21）

核心实际发布/恢复逻辑增加7项失败与取消用例，归档导入4项关闭/取消用例，UTF-8分块3项，共101项核心测试通过。复用SQLite恢复记录，不增加持久化类型；发布中取消完成短提交单元、已提交目录变化保证通知，返回实际启停状态。见 SKILLS_IMPLEMENTATION.md 和 evidence/0.1.16。本地完整Android构建/R8/lint通过，设备证据保持历史范围。

# 0.1.15 Skills（2026-10-21）

已按用户确认实现，详见 [SKILLS_IMPLEMENTATION.md](SKILLS_IMPLEMENTATION.md)。标准解析、版本化共享资源/恢复日志、SQLite v4、按需 Agent 工具、原生管理与显式最新来源更新。核心 87 项；Android 构建与 R8/lint 验证，新增两项设备测试仅编译。没有新增 Linux/逆向/抓包运行时，也没有真实模型缓存或性能结论。

# 实现与验证证据

## 设置状态文案与Skills研究（2026-10-21）

Skills新架构已研究，详见SKILLS_PROPOSAL.md，确认卡待答复，未接依赖/安装器/数据库迁移。独立修复设置版本从包信息后台读取，后台Agent/图文/日志/交互终端文案同步实际能力；Debug/R8Release/lint通过，0错误/既有1警告。此修订不扩展运行能力或硬件证据。

## 0.1.14 交互终端（2026-10-21）

最终实现提交0be35fc完整CI通过：[39597157476](https://github.com/Asagnc/Aurora/actions/runs/39597157476)。核心72、Linux主机PTY8、源码校验12及全部Android构建/lint通过；证据范围见evidence/0.1.14/README.md。设备12项新增用例仍仅编译。

已确认并实现普通/Root PTY、六个Agent工具、会话所有权/记录/中断恢复、原生终端查看与输入、前台服务跨模型回复生命周期、Root控制与FD数据通道，详见INTERACTIVE_TERMINALS.md。核心72通过，本地debug/R8release/testAPK/lint通过（0错误、既有MiMo警告1）。新增10项普通Android及2项Root用例仅编译，Linux主机8项已在CI步骤通过；未连接设备，不宣称Root/UI/性能验收。日志16MiB、尾部64KiB、4/8活动终端及65536单元主缓冲预算明确，不伪装无限日志或强杀后原shell恢复。

## 0.1.13 命令事件日志（2026-10-21）

FileObserver按观看生命周期收集，真实事件驱动增量读取、每流64KiB尾部预算；复用执行ID固定目录、账本持久映射。SQLite3非破坏迁移、Root命令协议2；完整输出结果之外存有效有界presentation，避免截断JSON隐藏错误。详见LIVE_COMMAND_LOGS.md。核心67项通过；新增5项Android专项只编译，Binder路径和v1迁移夹具已更新；新版设备/Root/迁移/手势/耗电未验收。

## 0.1.12 图文消息（2026-10-21）

用户已确认原生Compose Markdown+Coil3+会话图片产物组合。正文/代码复制、流式后台增量、完整阅读、Agent图片发布、本地快照和远程下载、SHA校验、查看/保存/分享已接入；见RICH_CONTENT_PLAN.md，详细边界同列。核心59项通过；debug/R8release/AndroidTest构建与lint通过，0错误/既有1警告。新增5项设备用例已编译但未执行，本版没有真机UI/实际远程站点/真实模型/性能结论。保持B输入区。

## 0.1.11 阅读/详情基础（2026-10-21）

实现消息复制、流式行稳定标识、按会话自动跟随/用户拖动暂停/回到最新；工具预览按JSON字段解析错误与退出码，解析/格式化移至Default线程；命令输出分stdout/stderr、复制预览、到产物目录，原始结果可展开。新增五项核心投影测试。Markdown、图片管线、持续日志与PTY未实现，图文选型待用户确认，见RICH_CONTENT_PLAN.md。本版设备未连接，视觉/手势行为未验收；历史0.1.10设备结果不扩展到新版。

## 0.1.10（2026-10-21）

按用户确认实现本地命令有界CPU唤醒范围；普通执行use收尾，Root在应用侧持有至结果/取消清理后finally释放，UID0服务不重复取得。没有新增依赖、整轮Agent持锁或设备策略修改。见[COMMAND_POWER.md](COMMAND_POWER.md)。真实ARM64普通41、浏览器恢复1、Root4、亮/熄屏2通过，核心45重跑；构建/lint通过，0错误/既有1警告。失败探索记录与策略调整条件保留，不能单独归因于锁；模拟器未完成。证据见[DEVICE_ACCEPTANCE_2026-10-21.md](DEVICE_ACCEPTANCE_2026-10-21.md)。

## 页面审计与状态文案（2026-10-21）

实际点击对比与边界见 [UI_AUDIT_2026-10-21.md](UI_AUDIT_2026-10-21.md)。纠正 ExecutionStatusPage 将已实现后台Agent误列为未接入的旧文案；后台前台服务和未实现交互式终端分别显示，未新增执行逻辑。该小范围显示修正构建与模拟器实际页面核对记录见 evidence/ui-audit-20261021/README.md，不重复扩大为底座回归通过结论。用户同意最后补齐 DSHA 受阻 ARM64 页面。

## 0.1.9（2026-10-20）

已确认的后台Agent架构落地：原生specialUse前台服务、通知停止/返回会话、独立运行投影、SQLite非破坏升级及任务记录。切换会话不取消、不串流，进程死亡只标记中断/未知，不重放工具。实现、验收边界见 BACKGROUND_TASKS.md；本轮新版真机后台/Root联合测试仍待统一验收。

## 0.1.6（2026-10-16）

普通原生命令接入真实 Agent 工具循环。独立原生监督程序负责信号、子进程组、两路输出预算和原子结果元数据；Kotlin 传递 argv、协调取消、返回退出码/产物与不确定状态。无新增运行库，不给普通 UID 自动提权，不默认经过 PRoot。详见 NATIVE_COMMANDS.md。

核心 45 项；模拟器普通测试扩为 26 项，另进程恢复 1 项通过。新增 8 项覆盖真实 native ELF 调用、双路各 9 MiB 输出预算、取消/超时/父退出和异常监督程序、参数/环境/UTF-8、AgentLoop 实际文件产物。ARM64 与 x86_64 均编译；ARM64 运行、Root 执行、实际供应商与性能基准未验收。FEATURE_STATUS.md 为当前能力/缺口索引。

## 0.1.5（2026-10-16）

认可的交互预览已落地，唯一指定图标修订为灯泡。输入区不再嵌套模型边框，指标改为原位展开，主菜单统一底部面板；空白/消息/执行步骤复用同一套正式组件。debug 专用 ChatReviewActivity 提供明确标注的内存示例，release manifest 不含该 Activity。没有新增执行后端或伪造任务结果。

扩展主题对比度用例覆盖输入区表面；极端颜色失败已修正，模拟器 18 项和独立进程恢复 1 项通过，核心 45 项通过。debug/release/lint 构建通过。真机和真实模型待后续，用户本轮明确要求先做好模拟器。证据与截图见 CHAT_REFINEMENT_2026-10-16.md。

## 0.1.4（2026-10-16）

用户确认的外置指标布局已实现；模型/思考控制压缩，无连接模型页显示添加入口。供应商编辑支持返回列表。执行记录使用动作名与准确状态，可展开调用 ID 和有界格式化预览。ToolRunStore 持久化后发送开始/完成事件，界面保留最近 50 项及最多 8001 字符结果，避免逐步查询历史数量。完整结果仍存数据库；取消仍不伪造完成。

核心 45 项、模拟器普通 18 项与独立进程恢复 1 项通过；持久化测试新增事件顺序、预览截断而原结果完整断言。debug/release/lint 通过，仍有既有资源警告。实际页面对比与后续缺口见 UI_AUDIT_2026-10-16.md。当前仅连接模拟器，0.1.4 真机、真实模型和缓存基准待验收。

2026-10-15。目标见 DESIGN.md，未完成范围见 PROGRESS.md。没有性能领先或最高缓存命中率的实测结论。

## 0.1.3 当前状态（优先于下面的历史记录）

已从手动浏览器接入 Agent：BrowserToolExecutor 使用 BrowserAutomation 在 Main dispatcher 运行真实会话 WebView；ToolRouter 固定每轮定义。提供 8 个浏览器工具，包括批量开页/读页、点击/填写/滚动和同步 JS JSON 返回。页面 ID 限定会话所有权，修改/脚本要求最新 URL 和导航 revision。文本/元素列表、脚本长度和返回值都有预算与显式截断；没有新增浏览器依赖或原生 JS Bridge。网页属于不可信观察数据。

用户可打开同一组页面观看；Agent lease 防止关闭观看页时中止正在执行的操作，操作结束且无人观看时暂停页面。全局 8 页、批量并发 2；取消会停止导航，超时返回结果待核实提示，不自动重放。同步 JS 运行超时不能保证终止已经进入 renderer 的 JavaScript，因此不宣称硬 CPU 限额或任意脚本可控；异步 Promise 明确拒绝。

工具账本查询拒绝已有 call_id；核心循环也拒绝同一轮跨响应重用 ID。运行记录在对话内折叠查看，只读最近 50 项和每项最多 8000 字预览。完成账本不等于业务成功，错误单独显示。多轮 usage 总计缺失字段保持未知；速度用整轮累计输出除以整轮耗时，缓存/上下文仍对应最近一次请求。

新界面按已确认方向采用单层 24dp 输入框、轻量三指标、输入框上方定位的小面板、无助手头像气泡、无抽屉新建按钮；右上保留新会话/文件/浏览器。主题完整映射浅深角色，预设/系统/自定义、HSV 与六位颜色值、柔和/鲜明/单色、纯黑和即时预览；无壁纸。旧偏好保留，默认青岚且无彩色功能图标。方案沿用 Compose，不复制 GPL 项目代码。

验证：核心 45 项；模拟器与 realme Android 16 ARM64 各 18 项普通设备测试，另进程恢复 1 项。Agent 浏览器为本地夹具上的确定性模型循环，不是付费供应商实测；真实 API、缓存/费用/网站成功率和后台持久执行未验收。Root 仍限 0.1.2 已验证的只读握手；没有新增 Root 执行工具。上传下载、异步脚本、跨站 iframe、完整 renderer 崩溃矩阵仍未完成。

BrowserRecoveryTest 必须独立进程执行；新建 Profile 的测试会留下待下一进程清理的空元数据，不能在同一批测试末尾把它们判成启动恢复失败。verify-emulator.ps1 显式分组并检查真实测试数量。

来源：[WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback))、[ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore)。

## 历史 0.1.2 实现说明


0.1.2 按新确认的 A 方案改为胶囊式输入框：灯泡思考按钮、模型家族图标与三项指标位于同一外框，单行胶囊、多行自然增高。右上菜单进入新会话、本会话文件与浏览器。抽屉底部为紧凑彩色横排入口；模型选择支持全部/官方/中转过滤，每行保留家族图标。外观支持五套预设、自定义强调色、浅色/深色/系统模式、彩色图标开关与系统动态配色；配置本地保存。字体大小跟随系统。当前会话文件入口不混入共享目录；会话文件浏览和预览也归属会话任务，删除时取消并等待。抽屉返回键先收起抽屉，不直接离开主界面。

会话搜索查询数据库而非仅过滤已加载的一页；百分号与下划线按文字匹配。初次导入附件创建会话时保留草稿，草稿支持配置变化恢复；主动切换会话时清空当前草稿。

支持官方/中转 HTTPS 地址、模型 ID、三种协议和认证方式，保存连接与加密密钥。抽屉保留打开、长按/菜单、重命名、置顶、多选、批量删除与分页；消息支持分页。共享空间浏览最多列举 200 项，文本预览有 64 KiB 上限。Skills 页脚明确打开只读共享资源浏览，安装和运行器尚未实现。

同一运行轮次固定连接与凭据，变更用于下一轮，不重新执行当前工具。配置切换按新范围、已知失效、支持追加更新和未知中转分别解释。token/s 是最后请求输出 tokens 除以整轮耗时的端到端估算，包含工具时间，不是纯生成速度。十家官方预设与稳定型号已按 2026-10-12 官方资料核查；账户实际权限与动态能力校验未完成。型号来源见 PROVIDERS.md，图标来源见 ASSETS.md。

## 手动浏览器

系统 WebView / AndroidX WebKit 1.17.1；当前是手动浏览，不是完整浏览器 Agent。HTTPS 导航、最多同时八页、页签切换、关闭/前进/后退/刷新已接入。按会话绑定 Profile；不支持 MULTI_PROFILE 或完整数据清理的 WebView 明确拒绝创建，要求更新系统 WebView，不混用默认空间。不提供特权 JS Bridge；文件/内容 URI 和混合明文内容禁用。浏览器退出暂停 WebView 计时器并停止加载，页面和 Profile 可在本进程保留；不宣称零内存开销。

代理入口位于浏览器内，默认跟随系统；支持直连和无认证 HTTP/HTTPS/SOCKS 代理。更改前需关闭所有页面。覆盖作用于 Aurora 的全部 WebView，不作用于模型 API 和其他 App。等待系统回调后才允许加载；超时/失败阻止继续打开页面，避免默默退回其他网络路径。系统 WebView 对本地地址的默认代理绕过规则仍存在。设备测试在本机开启 IPv4 回环代理并确认收到实际 HTTPS CONNECT 请求；没有把回调成功等同于路由成功。

删除先写入 SQLite deleting 标记，再停止所属页面。实际测试证明 Chromium 可在 WebView.destroy 后仍保留加载过的 Profile：直接 deleteProfile 会抛 in-use。实现先等待 WebStorageCompat.deleteBrowsingData 清理缓存/Cookie/JS 存储，再删除会话文件；空 Profile 元数据在下次进程启动删除。清理中断/超时保留删除标记和文件，不报告成功。普通测试验证两会话 Cookie/localStorage 隔离、仅清理所属数据、禁止重开删除中的空间，并通过独立进程验证孤立元数据恢复。

上传/下载、模型端 JS/DOM、批量页面工具、完整 renderer 退出及代理网络故障矩阵尚未完成。已处理 onRenderProcessGone 时销毁对应页并提示重开，但尚无真实 renderer 崩溃注入验收。浏览器代理不等于全机抓包；抓包按最新确认只做 Root 原生后端，逆向工具候选和待办见 REVERSE_ENGINEERING.md。

来源：[Profile 删除限制](https://developer.android.com/reference/androidx/webkit/ProfileStore)、[浏览数据清理](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)、[代理范围与回调](https://developer.android.com/reference/androidx/webkit/ProxyController)。

## Root 连接边界

已实现 libsu 6.0.0 RootService/AIDL 的只读握手、手动连接与断开、超时和服务退出状态。入口不自动请求 Root；服务校验调用者 UID，只返回自身 UID/PID、SDK、内核和 SELinux 状态。客户端只接受 UID 0 的握手。使用普通绑定而非常驻 daemon，断开后关闭缓存 shell。系统操作工具、执行器和 Root 真机连接尚未验收，不记为已完成。

专用 RootConnectionTest 被 RootRequired 标记，默认普通测试排除；必须在已授权设备显式执行且传 auroraRootTest=true。普通存储测试通过不代表 Root 测试通过。[libsu 原始 API 与生命周期](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java)。

## 网络与协议

共享 OkHttp 连接池；HTTPS；禁止 URL 内凭据/查询参数；禁用重定向与连接失败自动重试。取消关闭 Call，阻塞读和编码在 IO 线程。错误不回显响应正文、密钥或原始异常。SSE 单行与累计轮次有内存预算。内部明文回环接口仅供测试夹具。

三种协议保留完整原生输出、Chat reasoning_content、Claude 签名/内容块和 Responses opaque 项。只执行完整终态；重复调用 ID 拒绝；Anthropic 多个结果合并为一个 user turn。稳定系统/工具前缀指纹、连接、模型、协议、地址和密钥修订约束历史复用。工具开始/完成账本持久化；取消传播，不自动重放动作。

官方 OpenAI GPT-6 Astra/6.1 Sol 的 Chat 工具请求提前拒绝并提示 Responses；GPT-6 Luna 的 Chat 工具需要 none。未知中转不被假定与官方路由完全相同。思考等级按核实的具体型号与协议开放，未知名称只提供 auto。官方核实的 GPT-6 Responses 模型变更显式等级时追加 configuration_update，顶层 effort 保持最初值，历史更新保留原位置；切回 auto、认证/密钥/前缀变化或中断时重建范围，保留完整事实和工具账本。网关能力探测尚未完成。

来源：[追加更新与手动历史限制](https://developers.openai.com/api/docs/guides/reasoning)。本地测试验证不出现相邻更新、数据库重开后保留原始/有效 effort，以及两轮工具调用只执行一次；没有付费服务缓存命中实测。

来源：[OpenAI 当前协议限制](https://developers.openai.com/api/docs/guides/latest-model)、[Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[OkHttp Call](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt)。

## 密钥、数据库与中断

Keystore 管理 AES-256-GCM 密钥。随机 IV、连接/修订认证附加数据，密文在 noBackupFilesDir 用 AtomicFile 发布；相同密钥保持修订。Credential 字符串脱敏，配置数据库不存密钥。云备份与 Android 设备迁移规则排除私有数据。

SQLite WAL 与外键；IO 线程操作，串行写事务。消息/协议记录分表追加，期望序号冲突拒绝。中断的 started 工具变 uncertain，已完成结果保留；切换/恢复时把结果作为执行事实供模型继续判断。突然进程终止仍可能丢失尚未保存的流式片段；后台持久服务未完成。

来源：[Keystore](https://developer.android.com/privacy-and-security/keystore)、[SQLite](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)、[AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)、[备份规则](https://developer.android.com/identity/data/autobackup)。

## 文件与空间

会话空间、只读共享资源、保留工作区；共享命名空间复用文件核心实例，保持同一写锁。导入副本每文件最多 64 MiB，不删除原件；不等同于原生图片/PDF 理解。

读取每批 64 项、4 并发、每片 64 KiB，保留顺序与错误。编辑每文件 2 MiB、每组 16 MiB，SHA-256 预检；禁止重复/父子目标、逃逸和链接。备份、日志、逐文件原子替换，未提交组恢复回滚，外部版本冲突停止并保留日志。目录元数据断电持久性需真机验证。

删除先停止并等待任务/导入，封闭新任务，再标记 deleting。遍历不跟随链接，检测当前命名空间子挂载并拒绝；删除中断可继续。测试验证共享链接不会删除共享文件。这不是任意 Root 程序的安全隔离；Root 挂载、浏览器和子进程尚未接入清理协调。

## 验收证据

- 核心 42 项测试，零失败：协议、取消、认证/路由、重定向、脱敏、GCM 篡改/身份替换、缓存范围/追加更新、文件冲突/恢复和任务清理。
- Android 16/API 36 ARM64 realme GT6 SE 真机、Android 17/API 37 模拟器各 8 项测试，零失败：数据库重开/分页、原生追加冲突、真实 Keystore、删除链接边界、删除恢复、工具中断账本、配置更新持久化和文件日志恢复/列举。
- 0.1.2 模拟器普通测试 14 项通过，另起进程的浏览器恢复检查 1 项通过；新 UI 尚未完成最终真机验收。历史真实手机结果仍为 8 项，不能用模拟器结果替代。
- debug 与 R8/resource-shrunk release 构建通过；release 未正式签名。
- lint 无错误；保留 MonochromeLauncherIcon 和一项 MiMo 复杂矢量路径警告。品牌路径未为消除警告而删减形状，也没有据此宣称渲染性能已优化。两次单色衍生图不合格，未采用。
- 界面已实际启动与检查。无真实 API 密钥，未验证真实服务计费、缓存命中或模型效果。
- Android 16 ARM64 真机开发 APK 安装、启动成功。未证明 Root、手机 APK/NDK 编译或性能领先。
- [GitHub Linux CI](https://github.com/Asagnc/Aurora/actions/runs/38269980475) 对 f70ac51 通过核心测试、debug/release 构建和 lint，并保存开发构建文件。
- 模拟器界面操作已验证创建、重命名、置顶、多选和两个会话批量删除；验收用会话随后清理。

## 构建与已处理陷阱

2026-10-15：0.1.1 在 API 37 模拟器检查实际主界面、会话抽屉和供应商面板；确认 Gemini 弧线路径恢复正确形状，键盘弹出后输入框、操作按钮与三项指标仍位于键盘上方。输入测试未发送模型请求。当前本地核心 42 项、模拟器 9 项通过，debug/release/lint 构建成功；lint 仍有单色启动图标和 MiMo 长路径两项警告。新版真机检查按用户要求放到最后。

固定 Gradle 9.8.1、AGP 9.4.1、Kotlin 2.4.21、JDK 25、SDK platforms;android-37.0、Build Tools 36.0.0。最低 SDK 36。

同一仓库避免同时运行两个配置缓存构建。Android 平台 AtomicFile 无 exists；用 openRead 支持备份恢复。测试进程启动失败曾产生零测试 XML 而构建返回成功；默认 verify-emulator.ps1 只操作明确指定的实际模拟器、只覆盖安装、不卸载，且强制至少九项通过。Gradle connectedDebugAndroidTest 曾在收尾卸载手机应用，不再作为连接日常手机时的默认工作流。资源目录移动曾导致 AGP 增量遗漏 launcher；清理 app 构建输出后完整构建通过。

真机暴露 Files.readString/writeString 在基础 Android 16 不存在，它们在 36.1 才加入；文件恢复与设备测试使用基础版本可用的字节 API。JVM 模块中的 API 调用不能仅靠新平台模拟器和 app lint 判定可用性，必须在最低支持平台执行核心路径。[官方 API 版本](https://developer.android.com/reference/java/nio/file/Files)。

其他精确依赖版本在构建文件内，来自 Google Maven/Maven Central/Gradle 官方接口/libsu Releases，排除 alpha、beta、RC、preview。构建组合验收不等于服务能力或真机性能验收。

2026-10-15 final device verification: Android 16 ARM64 realme GT6 SE passed 14 ordinary tests, one separate-process browser recovery test, and one explicitly authorized RootConnectionTest (read-only connect/disconnect/reconnect). FolkPatch authorization is configured inside its app. Packet capture, privileged operations and revocation remain unverified.

## 0.1.7 Root command interface (2026-10-17)

Supersedes the earlier read-only implementation status: RootService now exposes asynchronous start, completion callback, cancel-and-wait and close. Only an explicitly connected, UID-0/protocol-verified endpoint is included as command_execute_root at agent-run construction. Existing ordinary command execution remains separate; no retry/fallback after an uncertain result. UID checks cover each production method. Controlled supervisor artifacts use descriptor-based ownership restoration; arbitrary Root files/mounts remain outside automatic cleanup.

See ROOT_COMMANDS.md for lifecycle, wire budgets and limits. Seven ordinary-UID cross-process contract tests added; emulator 33 ordinary tests + 1 isolated browser recovery and core 45 pass. Debug and R8 release compile for ARM64/x86_64; lint has no errors and retains the existing MiMo VectorPath warning. Two RootRequired command tests compile but have not run. Android 16 Root execution/ownership/manager revocation still require hardware evidence; historical read-only FolkPatch evidence does not verify them.

## 0.1.8 capsule B and hardware validation (2026-10-18)

Supersedes pending hardware statements above: on Android16 ARM64 realme GT6SE, 33 ordinary tests, 1 isolated browser recovery and 4 explicitly authorized RootRequired tests pass. Existing FolkPatch grant remained available after install-r over0.1.3; app was not uninstalled/cleared. Root UID0, controlled artifact ownership/readability, direct cancellation/timeout, disconnect cleanup/reconnect are verified. Strong-killing the actual Root service, external manager revocation and arbitrary Root files/mounts remain unverified/unfinished.

The approved capsule B is implemented with thinking bulb, concrete model, inline context ring, external statistics and bounded in-place details. Execution history defaults collapsed, active titles align with actual tools and completion count is explicitly recent history. Existing themes preserved. Actual device/keyboard/narrow long-name views are separate from clearly labelled debug examples. Details and raw test reports: COMPOSER_B_2026-10-18.md and docs/evidence/0.1.8. Core45, emulator33+1 and debug/release/lint pass; no paid-provider or comparative performance evidence.

2026-10-20最终验证：core45、模拟器普通39、独立浏览器恢复1、Agent进程恢复prepare1/verify1通过；debug/AndroidTest/R8 release构建通过，lint0错误、1项既有MiMo矢量警告。序列化1.12.0升级后已复跑。证据：docs/evidence/0.1.9；新版真机后台/Root联合验收未做。
