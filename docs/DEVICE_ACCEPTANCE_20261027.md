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


2026-10-29 接续：30分钟亮屏自有loopback采集实际1项通过，1800.018秒/16段/3571包/2,035,854字节，最终duration/status0，raw kernel received7142/dropped0/interfaceDropped0，逐段SHA/PCAP、pending0与自有会话删除通过；不冒充熄屏/512MiB/64段吞吐资格。新增final字段断言在运行后按同一Root最终记录独立核实，未声称改后用例重新跑满30分钟。真实原生OpenDocument取消/自有小ZIP导入/快照/清理1项通过85.293秒，实际OEM picker为com.coloros.filemanager，host只选本次自有ZIP；不是SDK180MiB可视导入资格。两项摘要见evidence/0.1.40的capture-long-device-20261028和native-saf-device-20261028。

用户明确批准最小SELinux先资格再开放。已确认固定Frida17.24.3 devkit包含其命名空间sepol符号，复用既有固定507985d源码头，未引入新库。scripts/frida-policy-*为显式资格夹具，未打包/自动启用：当前boot仅加frida_memfd、file_type/mlstrustedobject及untrusted_app的open/read/write/getattr/execute/map和实际Root域magisk的同组＋relabelto，两条allow键。重新解析后撤销新增项，与同等往返的原策略序列化完全相等。内核接受2,007,921字节，保持Enforcing=1；读回字节摘要不同，首次严格摘要检查明确返回失败，未假报通过。另行读取内核策略并验证仅有批准增量，专用memfd重标记/权限及通用tmpfs execute仍EACCES通过。没有permissive、通用tmpfs执行、全域execmem、持久模块或旧整份策略回写。

同一普通Aurora App只读native attach实测1项通过15.085秒，自有会话清理；旧两个spawn捕获前unknown仍保留。自有包spawn已由attach失败推进到workerSucceeded/卸载/分离/资源退休成功，但早期ART hook未观察到，整项仍失败。核实post用户脚本与resume之间存在异步竞态；正在补固定bridge既有evaluation确认的有界顺序等待，确认不作为脚本业务成功/清理证明，不改变protocol2恢复/目标身份/取消语义。frida-compile输出为分段bundle，简单追加JS的试验未生效且已撤回，不把此失败计成功。

367f840 CI40691904960仅在API守卫发现新增Files.readString需36.1，现改有界InputStream，Android36 API守卫351源通过、DebugAndroidTest/lint108任务通过。新的Frida产品代码仍须完整CI；历史c26b701审查包不自动代表新worker。无付费模型/正式签名密钥操作。


后续实测：spawn增加等待固定bridge既有user_script_evaluated的预算内屏障，失败/取消会在resume之前退出并沿用精确自有目标回收；确认只作顺序信号，不是业务成功/清理证明。修复后自有包早期Java方法hook实际1项通过33.759秒，包含真实hook事件及原卸载/分离/资源退休/会话删除。新增Linux宿主“延迟bridge仍捕获main首次调用”和“求值异常不resume”两项回归，待Linux CI；Android新增resume前异常用例正在设备复测。默认核心/Debug/Release/R8/AndroidTest/lint224任务通过，356核心/236 Android编译JUnit均void，API36守卫通过；不能据此宣称所有Frida故障矩阵或自动策略入口完成。资格摘要见evidence/0.1.40/frida-minimal-policy-device-20261029.json。


Frida四项组合复测：3通过/1失败，261.704秒。resume前用户脚本异常回收、早期ART hook及普通App native attach均通过；Root自检组合遇70秒超时，整批不算通过。增加阶段诊断/独立断连后，单独Root native＋选定attach＋ART＋status实测1项通过33.603秒，不能抹掉批次失败。两个已确认退出的自有失败owner经精确既有恢复/删除1项通过1.245秒，旧两个捕获前unknown保持。继续统一夹具Root连接的finally断连并复测同批次，不先断言已确定超时根因。

更新完整审查包release-review-9ceed07-complete已生成：精确9ceed074a5fad9fa10f176561eefbecd7f7bad0d、打包时clean，9项载荷/重建报告1,690,890,277字节、APK45,070,914字节，10条校验独立复核，当前worker离线重链接/guard重建通过。仍unsigned-review/releaseReady=false，资格程序未作为App策略入口打包；详见RELEASE_REVIEW_20261029。


9ceed07完整CI40863121380成功，五个job均成功，包含真实Frida/Linux30项（新增延迟payload首次调用和求值异常不resume）。组合超时后续直接观测：App所在/apps/uid_10429父cgroup.freeze=1、events frozen=1，进程自身freeze=0但events frozen=1，设备仍Awake；Root原生worker日志已有native_hook_observed及成功卸载/分离/退休。宿主仅启动Aurora MainActivity恢复前台后，App继续收到结果；不改cgroup/Thanox/系统策略。此受干预批次不作为独立后台或亮屏完整通过证据。FridaManaged亮屏夹具增加显式自身Activity及原flag恢复，继续无干预复测；背景冻结能力仍未通过。


2026-10-29最新：所有Frida验收夹具各自持有临时MainActivity/KEEP_SCREEN_ON并恢复原flag、Root在finally断连后，无宿主中途唤醒干预的5项亮屏批次全部通过111.298秒：早期ART spawn hook、resume前脚本异常精确回收、Root native/selected attach/ART/status、普通App native attach、观察到实际注入事件后取消并确认卸载/分离/worker及任务域退出，原App PID/start ticks存活。原失败/受干预批次保留，不把结果扩成后台/Thanox或App/worker/Root死亡完整矩阵。另一个冻结失败自有App owner经精确恢复/删除1项通过1.285秒，两个早期spawn捕获前unknown仍保留。当前产品worker仍绑定9ceed07及其成功完整CI/审查包；最新改动仅测试夹具/验收记录。新增显式64段/512MiB配置预算设备夹具已实测通过；高负载内核丢包另记如下。


64段/512MiB配置预算真机1项通过44.798秒：实际捕获37.253秒、64段、8896包、534,277,504字节，以storage_budget/status0按整包段边界结束；每段PCAP/摘要与总数、pending0、自有会话删除均通过，最终Root记录独立匹配。高流量raw kernel received24534/dropped6678/interfaceDropped0，明确有内核丢包，不宣称零丢包或线速资格。此项验证预算/封口/发布/回收，与先前低流量30分钟dropped0分开记录；熄屏/后台/故障矩阵仍待。证据capture-budget-device-20261029.json。


Root服务实际死亡专项1项通过15.566秒：已观察真实注入后，宿主使用本次测试明确提供的Root PID/startTicks、Root UID校验及pidfd_send_signal终止该确切进程，先验证错误ticks拒绝且不发信号；不按猜测PID发kill。新Root身份不同、native/ART资格均归零，旧票据只恢复不重放，实际卸载/分离/worker与任务域退出、原App PID/start ticks存活及自有会话删除通过。scripts/owned-root-signal-fixture.c仅显式测试临时二进制，不打包。首轮夹具误要求必须抛异常，生产实际也可保留结构化不确定结果；改为拒绝正常worker成功并独立要求恢复证明后通过，失败自有owner经既有恢复/删除2秒通过。App进程死亡专项继续；此项不冒充Root管理器撤权或boot变化资格。


App进程死亡两阶段专项通过：prepare仅在自有fixture已捕获/注入/持久resume确认后自杀，预期Process crashed，不计JUnit通过；重启verify 1项通过3.061秒。启动未自动重连/重放，原票据保留，Root旧任务卸载/分离/worker与域退出证明成立，已resume的目标PID/startTicks仍存活，脚本事件仅1次，pending归零，自有会话/checkpoint清理。首次实际发现Root日志已发布但O_EXCL导致恢复不返回artifact；现仅对完整同字节的既有副本复用，检查regular/nlink1/0600/允许UID与GID、流式逐字节和前后stat/目录条目身份，同步后交付；拒绝不完整/篡改/链接/宽权限，不删改现存不匹配文件，诊断失败不编造清理事实。新建副本也补handoff目录条目身份检查。6项真机文件检查通过1.934秒，含实际Root创建硬链接拒绝；cache特殊GID/普通UID禁硬链的初始夹具失败已明确修正，未放松产品检查。

本地主机完整核心/Debug/Release/R8/AndroidTest/lint224任务通过，356核心340通过/16跳过，356核心/247 Android编译JUnit均void，Android36 API守卫357源通过。一次构建重试过早与未退出旧构建重叠导致lint临时输出冲突，已串行重跑通过；不将失败记录算通过。0c057b4完整CI40866353580成功；新的日志恢复产品修复还需新CI和新精确源码审查包。


2026-10-30 worker实际SIGKILL专项1项通过17.373秒。仅自有fixture持久resume确认且已真实注入后，从绑定票据/boot的Root READY记录取worker PID/startTicks；宿主pidfd校验并拒绝错误ticks再发SIGKILL。原目标同PID/ticks仍存活时，恢复保持unknown、pending1，删除被阻断，资源退出不冒充脚本卸载；只在显式停止该固定fixture后，以真实targetExit证明清账，cleanup仍null，pending归零并删除本次自有会话。没有强制清未知记录或重放，两个历史捕获前unknown保持。复用测试调用辅助类，不改产品协议/持久格式。

更新完整审查包release-review-16abd22-complete绑定16abd2246873591b7df9f49ed4c88e39cecad82c且打包时clean；9项载荷/重建报告1,690,959,613字节，APK45,074,402字节，10条校验独立复核，worker离线重链接及guard重建通过，unsigned-review/releaseReady=false。参见RELEASE_REVIEW_20261030。当前主线继续接续策略显式启用及身份/boot门控，不把本次临时全局策略资格当已有产品入口。
