# 2026-10-27 真机验收进展

状态：进行中。用户已授权真机测试，保留现有安装和数据。先测试/修复既有功能，再由用户说明UI问题；付费模型调用等待UI处理和用户配置密钥，拓展最后进行。本记录不是全部通过声明。

设备：RMX3850、Android16/API36、ARM64、FolkPatch。原安装0.1.32与本地Debug签名一致，已保留私有备份后覆盖安装0.1.40。原始数据库、日志、设备标识及备份只在被忽略的app/build/device-acceptance/20261026目录，不进Git。

## 已执行与修复

- 普通初测142项：134通过、8失败。包括真实KeyStore/JCA、SQLite迁移、FD socket夹具、tcpdump离线执行；失败没有计入通过。
- 浏览器初测31项：26通过、5失败，真实renderer终止用例通过。已修正Agent调用结束过早停止异步导航，以及Android可信filesDir别名导致下载恢复误判链接；相关异步6项、下载恢复4项复测通过。启动profile清理须在新进程独立执行，独立用例通过。
- FD socket测试先connect再设timeout；合成旧数据库夹具先移除依赖新列的索引；版本断言接受真实封装后的tcpdump argv[0]。针对性35项复测31通过，余4为当时尚未修复的下载恢复3项及平台验签1项。
- APK UID离线tcpdump的无意义setgid/setuid触发真实SIGSYS。仅在Android APK非特权且真实/有效UID、GID已相同时省略；Root和set-ID路径保留。离线过滤复测通过。
- Root基础23项初测5失败、1跳过。内核未启用PID namespace，明确跳过；基本Root/cgroup/私有挂载资格有实际通过证据。补齐自有会话目录及明确IPv4 loopback夹具后，原始发包1项、抓包7项均通过；不改全局网络配置。
- 抓包轮转真实工作进程完成后，清理器因本机缺proc children接口不能确认。增加waitid/ECHILD内核退出证明，活子进程无法枚举时继续失败保留；另修复旧worker PID复用为非leader线程时过早pidfd_open失败。两份原生夹具在手机执行通过；旧任务正常恢复和新的短时轮转合计2项复测通过。没有手删未知Root记录。
- Frida实际原生探针、自有脚本attach、ART String调用探针通过。libsu app_process的ART探针改用VM附着，避免普通App数据目录初始化。Android包spawn的stdio改为inherit；仅保留平台ART运行路径环境，避免helper缺BOOTCLASSPATH时abort。spawn仍在查验，不能宣称通过。
- Socket查询同一NETLINK后端新增活内核CONFIG_USER_NS=n资格：仅Root服务读取有界/proc/config.gz，缓存限服务生命周期；缺文件本身不作为资格。输出userNamespace=null及initial_user_namespace，不虚构inode。Java会折叠IPv4-mapped IPv6字面量，解析已保留AF_INET6；自有Java双栈socket使用真实IPv6族查询。真机当前socket精确UID用例复测通过，仍不代表历史PCAP逐包UID。

## 待完成及阻塞

- Skills发布者签名：按用户“检索Claude等主流做法后取最优”的新授权，选固定BC1.86轻量API，保持可选签名和来源/摘要固定，不注册provider。外部SPKI黄金向量、凭据库、v9迁移及恢复5项真机通过；普通+浏览器172项零失败、独立启动清理1项通过。JDK增加非法/小阶/非规范公开点及S=L签名标量拒绝回归。
- R8实际运行：opt-in `-PauroraTestBuildType=release` 生成压缩App和专用测试APK；两份调试签名临时副本核对与既有签名相同后覆盖安装，独立Java Instrumentation实际黄金验签、篡改拒绝、provider列表不变全部通过（R8_SKILL_SIGNATURE_SMOKE_PASS）。为跨APK测试保留SkillSignatures/SkillSourceProfile公共调用契约，方法体及BC仍允许优化；这份instrumented Release有测试保留规则，不能冒充默认发布APK的逐字节验收。普通AndroidX runner因被压缩的Trace/Kotlin facade引用先后启动失败，未算验签失败或通过。原始unsigned APK未签名，未读取正式密钥，结束后同签名恢复Debug且保留数据；默认发布已重新构建。
- Frida spawn、服务/App死亡和捕获/resume故障矩阵尚未通过。失败任务unknown保留，严禁猜PID回收。两个捕获前失败的自有夹具owner：bba8cd10-a465-4282-bf69-4bd707ef2346、89cde311-80cb-4ba5-8f4a-3700081e5e4c。后续接续必须核实当前记录，不因worker已退出直接删账本。
- Linux/PTY/元数据/离线Agent首批16项15通过、1失败；交互Agent夹具改为先读ready再送输入，并沿next游标增量读取，5项离线Agent复测全部通过。未使用真实模型。
- 正式共享工具安装发现Android NIO getFileStore抛SecurityException，改用File.usableSpace，同样空间门槛且零值拒绝；静态逆向下载切到已限定编译目录的downloadTool，沿用180秒预算。首批传输因超时/冻结连接中止失败，暂存按既有安装器清理，未发布或授予资格；当前前台重试。代理/静态逆向实际运行和APK手机构建仍待验收。
- Root镜像安装和精确Linux恢复各1项、后台亮屏Root任务1项通过；普通PRoot实机缺硬链接资格，既有Root代保留。原无条件PRoot成功夹具现仅在实际资格满足时执行，不添加替代后端或宣称普通PRoot可运行。
- Agent进程死亡prepare/verify两阶段各1项通过，旧命令只执行一次，重启标记interrupted/uncertain，不重放。熄屏Root任务曾超时；独立复查实际观察到Aurora命令PARTIAL_WAKE_LOCK、Dozing及App cgroup frozen=1。宿主在40秒后唤醒/打开Aurora，runner最后OK（42秒）；因宿主干预，不算独立熄屏完成资格，不声称已确定具体冻结控制器。SAF、长时完整预算、Thanox/撤权、Release性能与正式签名继续验收。
- 不调用付费模型，不读取正式发布密钥，不改全局SELinux/网络/冻结策略。遇到App被冻结或测试进程退出时记录中断，不把未完成批次算通过。

## 本轮主机检查

接BC后默认Debug/Release/R8/AndroidTest/lint完整224任务通过。实际XML为354核心：338通过、16跳过、零失败；编译后354核心/231 Android @Test全部void。本轮未注入大型原生归档输入，故不冒充此前显式归档传输的主机证据。新增Java Release smoke不是JUnit、不混入计数。两份原生清理夹具已在手机执行通过，Linux版本在Windows显式跳过，已加入Linux CI。

4382fc5完整CI40519975449的socket、managed-supervision、capture通过，整体因Linux spawn继承目标stdout导致JSON污染/pipe持有而失败；已将inherit限制为Android，Linux恢复PIPE，须新CI复验。新的完整源码发布审查包尚待刷新，历史f561e10包保持历史证据。

## 2026-10-28 接续

- dbb5a0a完整CI40524669781成功，包含修复后的Linux Frida标准流及socket/managed-supervision/capture专项。
- 静态逆向实际Root/chroot工具运行5项通过；代理安装和实际HTTP/HTTPS/未信任CA/WebSocket/离线读取及缓存清理2项通过。原实现把guest `/mnt`误认作workspace而非workspace/project，导致代理公有产物、私有缓存及APK探针路径多了一层project；现统一实际挂载路径，缓存仍需原有精确票据、停止和scopeRemoved证明，新增project挂载屏障。ARM64产品资格脚本同步生产挂载，需新工作流重跑。
- 手机共享SDK安装通过，包括固定180MiB原生归档经FileProvider输入；不是可视SAF chooser验收。安装测试显式离线输入只服务编译目录中固定URL/长度的归档，生产安装器仍验证SHA；不算手机官方网络下载资格。
- 首次手机构建探针270秒超时，作用域确认清理，不能计JNI通过。新诊断用例保持测试Activity亮屏，并可保留已确认退出的自有会话缓存；复测已进入实际Gradle依赖解析。其间USB调试重新开关令ADB中断，任务最终exit1/scopeRemoved=true，日志明确为测试专用loopback下载连接拒绝；复用原会话继续，不重放未知任务。
- Windows原生ADB后端看不到已存在的WinUSB接口，使用官方支持的LIBUSB后端后恢复连接；未装驱动/IDE、撤销授权或清除App数据。临时CONNECT只允许固定官方主机，TLS端到端，不改设备全局代理/CA，结束移除自己的reverse。
- 当前路径修复后默认Debug/Release/R8/AndroidTest/lint完整224任务通过；编译后354核心、232 Android @Test均void。新增APK/JNI设备用例尚未通过，不混入此前通过计数。正式密钥、付费模型仍未使用。

- 最新标准手机SDK探针1项通过，41.816秒：同一不可变只读工具包，真实Java/Compose/双ABI JNI构建、zipalign 16KiB/ELF检查、Root文件桥导出，普通App UID的DexClassLoader实际nativeMarker返回owned-42，会话正常删除。依赖缓存已预热，不算冷缓存首跑通过；此前270秒超时仍保留。详细诊断复用旧项目assembleRelease成功（99秒，53任务16执行/37缓存），仅诊断不授予资格。实际标准探针最终通过才登记当前Root/环境/工具资格。见evidence/0.1.40/android-build-device-20261028.json。
- 8078f46完整CI40685540287、ARM64代理40685566575、ARM64 SDK安装/readonly产品探针40685573229全部成功，实际报告已归档；各自范围不相互冒充手机/正式签名资格。

- APK诊断入口仅显式auroraBuildDiagnosticProject使用已生成的自有UUID项目与同一270秒/工具租约，--info记录实际任务；始终以“诊断、未授予资格”结束，不伪装测试通过。Frida新增显式保留失败夹具选项，只在恢复确认后保留自有会话供诊断，unknown仍阻断清理。两项诊断夹具编译及lint共108任务成功。

- 30分钟采集首次在启动阶段失败，真实定位RootManagedProcessWait硬编码275秒；现增加采集专用1,825,000毫秒入口，保留Frida/普通命令上限和原取消/强退未知语义。Root命令级错误现在映射为managed_outcome_uncertain、无exit/cleanup事实，仍保留intent。两项核心回归及完整224任务通过；356核心340通过/16跳过，356/233编译JUnit均void。修复后30分钟亮屏loopback夹具正在运行，未计通过。
- Frida自有包spawn复测仍在attach阶段Agent connection closed unexpectedly，已确认退休/resourcesClear；显式保留诊断owner 6b5fe7fd-ba7f-447a-b09a-01e5eb495982。近期自有目标日志/内核环形日志没有取得直接AVC证据，不据此断言SELinux根因。上游17.24.3 server/server-glue.c会调用修改全局策略的frida_selinux_patch_policy，当前未调用，不把该操作当例行修复。

## 实现依据（延续）

现有guard/cgroup/journal与NETLINK路线保持，不新增daemon或任务持久格式。Linux内核/proc children受CONFIG_PROC_CHILDREN控制，且退出并发下空列表不构成无子进程证明：[Linux手册](https://man7.org/linux/man-pages/man5/proc_tid_children.5.html)、[waitid语义](https://man7.org/linux/man-pages/man2/waitpid.2.html)。NETLINK UID按查询socket的user namespace映射：[Linux inet_diag实现](https://github.com/torvalds/linux/blob/master/net/ipv4/inet_diag.c)；USER_NS配置：[Android内核源码](https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/init/Kconfig)。Frida采用固定17.24.3/Java bridge7.0.13，已核对其打包源和[固定Linux后端源码](https://github.com/frida/frida-core/blob/17.24.3/src/linux/linux-host-session.vala)。
