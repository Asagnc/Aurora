# 当前接续清单（2026-10-29，0.1.40 真机验收中）

只记录当前状态；历史实验/失败/修复见PROGRESS、IMPLEMENTATION和docs/evidence，不把早期缺失当成当前待建设。

## 工作约定

- 在现有main连续实现、验证并推送；例行修复直接做。新底层/依赖/持久化选型先比较确认；已批准方案不重复问。
- 用户已明确“开始测试吧”，随后要求先测试全部既有能力，再细说UI问题，UI处理后由用户配置模型密钥再测付费模型，最后拓展。当前允许真机测试；不重装Android Studio/WSL，不调用真实付费模型。
- 真机保留安装/数据，由0.1.32同调试签名覆盖到0.1.40；FolkPatch已授权，不等待Magisk式弹窗。当前设备失败、修复与复测见DEVICE_ACCEPTANCE_20261027，不把历史主机通过当设备通过。
- 长期发布密钥在手机公共Downloads，留到最终验收；现在仅unsigned-review，不生成替代正式密钥。
- 只有实际当前Root身份/服务代/环境代/工具版本资格通过才开放执行；安装、编译或主机CI不授予手机资格。
- 失败/取消只清理自己的暂存；未知占用、退出或清理失败保留恢复记录并阻断相应删除。

## 已接入，不重复建设

| 范围 | 当前实现及主机证据 | 尚未手机验收 |
| --- | --- | --- |
| Frida Root Binder/活动账本/Agent | schema8不可变票据、sealed memfd/FD、独立guard/cgroup/journal、恢复/删除屏障；protocol2 spawn两阶段记录；Agent status/recover/probe/attach/spawn。真实Frida28组及spawn记录5组CI40158211528成功。 | 当前Root/ART、普通App attach、自有spawn早期hook、取消、Root服务死亡及App死亡恢复通过；worker死亡/未知删除屏障亦已通过；撤权/boot、后台冻结及适配原生启用入口仍待。 |
| 网络底层/轮转 | libpcap worker、tcpdump离线；http_request；原始发包/PCAP精确回放；socket_owner_root为真实NETLINK当前socket快照；capture protocol3/schema9同表轮转、BOOTTIME、封口/副本持久后回收。网络/guard/capture专项与完整CI均已通过，见各实现记录。 | Root采集/UID、30分钟低流量与64段/512MiB配置预算已通过；高负载dropped6678明确非零丢包，熄屏/后台及故障完整矩阵仍待。 |
| 代理 | mitmdump12.3.0 fixed ARM64、不可变regular loopback profiles、HTTP/WS字面修改、严格TLS、自有HTTP/HTTPS/未信任CA/WS自检、产物/离线读；同共享安装器/PTY/Linux租约/Agent、公有CA SHA导出；私钥缓存只凭既有Root ticket/stopped/completed.scopeRemoved清理。ARM64产品专项40294444272成功。 | 实际Binder/PTY/chroot、HTTP/HTTPS/WS/未信任CA、CA导出和私有缓存清理2项通过；故障完整矩阵仍待，不自动安装系统CA/改路由。 |
| 静态逆向 | 固定JADX/Apktool/JRE，共享只读版本/持久租约/当前Root资格与Agent分析。 | 实际JRE27/JADX/Apktool/自有DEX/APK/只读工具5项通过；故障完整矩阵仍待。 |
| APK构建 | 同共享安装器android-build，JDK25/Gradle9.8.2/AGP9.4.1/SDK与ARM64原生host，外来SDK ELF拒绝入口；实际Java/Compose/双ABI JNI/DEX/16KiB/readonly布局在40294449390通过。Agent apk_build/apk_export（512MiB/120秒）已接。Linux环境页直接SAF选择固定180MiB原生归档，复用同一staging，不走64MiB普通附件、不新建导入缓存；实际固定字节流式导入本地已通过。 | 手机标准只读Compose/双ABI JNI构建、导出及普通UID JNI加载通过；小ZIP可视SAF已通过，SDK大归档可视SAF、冷缓存预算、故障和正式签名仍待；候选不冒充完整Android补丁NDK等价。 |
| Skills | 已批准私有GitHub API/仓库限定PAT、现有KeyStore及schema10来源/秘密归属恢复；可选用户钉公钥/BC1.86轻量Ed25519，不注册全局provider；本地ZIP＋旁路离线验签；原生配置/撤销/指纹、Agent仅选既有profile。发布前重核策略，历史本地版本继承当前绑定；已运行快照持旧版。Android黄金向量/凭据库/迁移/恢复5项通过，opt-in R8压缩验签smoke通过。 | 撤销UI/自有私仓、默认正式发布APK和正式签名最终验收；R8测试App含两类公共测试契约保留规则，非默认发布包逐字节资格。 |
| 浏览器 | 独立WebMessagePort/文档版本、二进制上传/流式下载、原生chooser/精确URI快照、背压/归属/账本恢复。新同源/跨源frame边界、实际renderer死亡用例已编译；后者显式auroraBrowserCrashTest=true且真实页面全关才执行。 | WebView/SAF/提供器/代理/导航/跨源边界/renderer完整矩阵；不绕过同源策略、不重放未知副作用。 |
| 系统快照 | system_snapshot按需普通Android API：系统/硬件、近似RAM、App data文件系统空间、电池原始属性、缓存Root连接状态。无shell/轮询/新增权限/Root连接。 | 两项普通UID用例仅编译；不宣称任意系统写API或包/权限管理已实现。 |
| 其他基础功能 | 普通/Root命令、PTY、Root Linux、文件/图片/日志、后台Agent宿主、模型10家官方目录已接；不从目录推断账号能力、缓存命中或性能。 | 后台/Thanox/长时压力与真实供应商路由/usage。 |

主要入口：ROOT_MANAGED_BINDER、ROOT_MANAGED_TASKS_IMPLEMENTATION、FRIDA_SPAWN_PROPOSAL、FRIDA_AGENT_ACCEPTANCE、CAPTURE_ROTATION_IMPLEMENTATION、NETWORK_PROXY_IMPLEMENTATION、STATIC_REVERSE_IMPLEMENTATION、APK_BUILD_PROPOSAL、SKILLS_IMPLEMENTATION、SKILLS_TRUST_FORMAT、BROWSER_TRANSFER_IMPLEMENTATION、BROWSER_CHOOSER_IMPLEMENTATION、BROWSER_ASYNC_IMPLEMENTATION、SYSTEM_SNAPSHOT、PROVIDER_REVIEW_20261025。

## 发布及最新验证

- 当前代码c26b701完整CI40689586191成功。更新完整审查包release-review-c26b701-complete绑定该提交，打包时clean：9项载荷/重建报告1,690,823,325字节，APK45,070,798字节，10条checksums独立复核，worker离线重链接/当前guard重建通过；仍unsigned-review/releaseReady=false，详见RELEASE_REVIEW_20261028。普通App attach在最小SELinux资格适配后实际1项通过；自有spawn早期ART hook已实测1项通过，resume前异常和完整故障矩阵继续。最新主机367f840 CI40691904960因Android36.1 Files.readString入口失败，现已改有界流并通过API守卫，待新CI。

- 2026-10-27真机普通/浏览器172项及独立启动清理1项通过；Linux/PTY/元数据11项、离线Agent5项复测、Agent死亡恢复两阶段、当前socket真实UID、短时轮转恢复与部分Root抓包/发包已通过。BC后默认完整构建224任务成功。静态逆向5项、代理2项实际设备测试已通过，代理私有缓存确认清理；SDK固定归档安装通过，标准手机构建/JNI探针1项通过（预热缓存41.816秒；冷缓存270秒超时未通过）。dbb5a0a完整CI40524669781成功，8078f46三项CI均成功；后续修复长时采集Java等待上限，当前224任务通过、356核心340通过/16跳过，356核心/233 Android编译签名检查通过；30分钟亮屏自有loopback实测1项通过（1800.018秒、16段、3571包、2,035,854字节，内核dropped/interfaceDropped均0；不代表满512MiB/64段压力或熄屏）；实际原生SAF取消/自有小ZIP导入/快照/清理1项通过；Frida spawn/熄屏/长时/SAF/正式签名仍有未通过项目；以DEVICE_ACCEPTANCE_20261027为当前状态，不把以下历史编译记录当当前全部通过。

- 完整实现提交f561e10：完整CI40327134461通过；实际ARM64 SDK产品专项40327139608通过，8项零跳过，包含真实固定归档下载/180MiB导入/生产安装/readonly Java＋Compose＋双ABI JNI探针及源码审计。后续纯文档提交单独跟踪，不把取消记成功。
- 本地实际351核心336通过/15跳过（含显式180MiB原生归档输入）；Debug/Release/R8/AndroidTest/lint220任务通过。新增编译后JUnit签名检查覆盖351核心和228 Android @Test，无非void方法；不会再把返回Throwable/Nothing的用例默默忽略。Android用例仍未执行。
- 发布材料已接SDK原生包＋41份固定源码输入、代理官方分发＋584份固定源码/候选wheel/Cargo超集、精确自有git源码快照、Frida/原生源码重链接及重建、实际APK资产/16KiB/身份/图标链与批准像素校验，全部纳入manifest/SHA256SUMS。
- 完整审查包release-review-f561e10-complete已生成并独立复核全部SHA256SUMS：APK45,018,519字节，9项载荷/重建报告共1,690,661,029字节，另有manifest与checksums；工作树当时clean。SDK41项/代理584项输入及源码快照全部对应f561e10，离线Frida重链接/guard重建通过，图标链/像素通过。见RELEASE_REVIEW_20261026；保持unsigned-review/releaseReady=false，设备和正式签名未验收。

## 真机集中验收与明确边界

- 继续执行Root/ART/自有spawn、静态逆向、APK构建/JNI、代理、采集轮转、WebView/SAF、管理器撤权、未知恢复、取消/删除/服务死亡、熄屏/后台/Thanox和长时压力。正式签名仍留待用户提供安排，不读取Downloads密钥。
- 用户要求检索Claude等主流做法后取最优，已按新授权选择BC1.86轻量Ed25519，修复平台外部公钥导入不可用；来源/版本固定保留，发布者签名仍可选、不强制所有Skill配置公钥。黄金向量/凭据库/迁移/恢复5项真机通过，普通+浏览器172项及独立启动清理1项通过；R8专用运行验证继续，见SKILLS_CRYPTO_PROVIDER_PROPOSAL和DEVICE_ACCEPTANCE_20261027。
- 固定Release性能工作负载尚未实测：正确率、TTFT/整轮/工具时间、CPU/RSS/耗电、采集吞吐/内核丢包；真实模型调用继续遵守禁止付费调用约定。
- 普通PRoot在该Android16硬链接资格不足时executionReady=false；不加入link2symlink/QEMU冒充成功。
- 历史逐包UID关联需后续独立eBPF资格；当前只做真实socket快照。任意跨源frame访问、私有GitHub release asset API、Sigstore/插件市场及任意系统写API未声明支持，不能用已有命令/公共下载能力冒充完成。

- 2026-10-29：用户批准Frida最小SELinux先资格再开放；固定SDK已携带sepol，无新增库。仅当前boot增加frida_memfd及file_type/mlstrustedobject属性，untrusted_app和实际Root域magisk各一组file规则；无全域execmem/普通tmpfs执行/permissive/持久模块。离线和内核读回逆向差异相等验证通过、Enforcing=1、专用memfd标签/六项权限通过、普通tmpfs执行仍EACCES。普通App native attach实际1项通过；尚未开放App自动适配，完整spawn/故障与当前身份门控仍待，见FRIDA_ANDROID_POLICY_PROPOSAL。


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


2026-10-30策略编辑器接续：将已批准最小策略的纯内存编辑、精确类型/属性/约束与两条allow差异审计移入现有Frida worker，复用固定SDK内libsepol及固定源码49份头文件；无新增动态库，不写selinuxfs/load，不授予policyQualified。独立正向复核实际发现固定avtab读取会反转同key的xperm序列，现只允许与原始策略派生的两个完整序列化字节像之一相等，不按字段忽略差异。最终worker 1546b5f6ab3c0bd2333e0cf27372313182ee90dfd9c49d22a65b50d896d0ca68：手机10项命令边界及正向/6项越权变体共17项通过，内核策略摘要前后不变；拒绝已有输出、坏格式、链接、属性域、相同源域、已有专用类型及向已存在load路径写候选。5项构建输入/归档守卫通过，完整224任务通过，65份来源源码包完全离线重链接得到同worker。显式启用/Binder/UI与boot/身份门控仍待，不把本次纯编辑器验证记成生产策略启用；详见evidence/0.1.40/frida-policy-editor-device-20261030.json。af451ab完整CI40910882424已成功。

同最终worker覆盖安装后，无宿主中途唤醒的Frida亮屏5项回归全部通过111.947秒（Root native/selected attach/ART/status、普通App attach、早期ART spawn、resume前脚本异常及注入后取消保活）；未扩成后台/撤权/新boot资格。


2026-10-30显式适配代码已接入：现有worker单次load/读回审计、Root Binder末尾事务与protocol协商、当前boot/Root身份/worker/内核摘要门控、Root私有原始基线、设置页资格状态。Debug仅显式qualification验收请求可启用；FridaPolicyAvailability.releaseQualified=false，正式入口和UI启用按钮关闭，须满足批准方案的新boot/撤权及剩余故障矩阵后再开放。最终worker0ca9bd59：17项纯检查、11项native启用/拒绝/同一已批准候选实际重载全部通过，Enforcing保持1、内核摘要前后相同；同boot已知原始策略仅由宿主显式复制为本次资格基线，产品不接管未知已有专用类型。Binder旧身份拒绝/启用/真实ART/重新启用清零/重连不继承专项1项19.426秒，Frida5项与普通Binder/Linux准备16项共21项115.127秒通过；最终发布门控/protocol变更后针对性复测待做。新增Root拒绝实机夹具已编译，未执行。用户改为亲自重启及操作管理器：先重启解锁、暂保留Root，核验新boot后再撤权；不得由ADB重启或自行改管理器授权。详见FRIDA_POLICY_IMPLEMENTATION及evidence/0.1.40/frida-policy-activation-device-20261030.json。

补跑普通UID系统快照2项与浏览器同源/跨源边界1项通过；renderer死亡首次因未开显式开关跳过，随后单独显式运行1项通过0.582秒，不把跳过计通过。d9f5872完整CI40917630532成功；对应完整审查包release-review-d9f5872-complete绑定该提交且打包时clean，9项载荷/重建报告1,691,128,452字节、APK45,173,118字节、10条校验独立复核，仍unsigned-review/releaseReady=false。该包不包含后续显式启用实现，不能作为后续新代码审查包。


新完整审查包release-review-5edfdc3-complete已生成并独立流式复核全部10条checksums：精确5edfdc319093fa0a38c1c93a47ddc8f14c140c1d、打包时clean；9项载荷/重建报告1,691,202,794字节、APK45,188,830字节。包含显式策略实现及关闭的正式资格门控，worker0ca9bd59；65份Frida/41项SDK/584项代理材料、精确自有源码快照、离线worker重链接与guard重建通过。仍unsigned-review/releaseReady=false，最终boot/撤权/设置页实测待用户手动重启解锁后接续。完整CI41057202983运行中，不先记成功。


2026-10-30实际手动重启/撤权/恢复实测：新boot改变，启用前内核策略回到原始1ff62aa2且Enforcing，新boot产品基线不存在；没有旧策略持久装载。产品从零捕获的新基线与启用前内核字节完全一致，启用后完整差异/标签及Enforcing检查、Binder资格/重连失效1项19.388秒、只读17项通过。用户在FolkPatch拒绝Aurora后，Root连接UNAVAILABLE且特权接口均空，普通UID命令和不触发Root的系统快照1项1.735秒通过；随后由用户恢复授权。

恢复后受限批次6项最终203.212秒但发生宿主唤醒干预，不计独立通过；透明自有fixture仅保证可见性，另一受限批次前5项通过后普通App出现原生SIGSEGV/PC0x320，整批失败，不直接归因Thanox。与固定Frida远程调用哨兵地址相同只作排查线索，未确认根因。失败自有owner经既有精确恢复/删除1项2.05秒完成，无强删unknown。

用户随后明确解除Thanox中Aurora的冻结/后台限制：单独取消1项17.09秒、策略先资格后的Root服务死亡1项18.057秒、worker精确SIGKILL/活目标unknown删除屏障1项19.263秒、App已注入/resume后自杀prepare（非JUnit通过）＋重启verify1项3.188秒全部通过；Root/worker宿主仅依本次测试提供的PID/ticks执行pidfd信号，错误ticks先拒绝。断连/死亡后策略资格不继承，原目标保护与旧票据恢复无重放。完整六项组合无宿主中途唤醒通过133.086秒。此前原生崩溃仍保留、未声称被修复；正式策略启用门控仍false。浏览器异步/下载恢复、Skills验签/信任恢复及Root既有副本共21项复测通过7.125秒；新自有会话APK构建270秒预算复测运行中。

356f157完整CI41057445450五个job全部成功，产品代码与5edfdc3审查包一致（其间仅文档）；5edfdc3的41057202983被后续推送取代取消，不计成功。当前新增只修改验收夹具/宿主脚本与记录；frida-fixture改为透明自有目标并临时KEEP_SCREEN_ON，不改任何系统/Thanox策略。


2026-10-30补充验收：Root抓包/短轮转/静态逆向/代理14项通过51.824秒；当前用户解除Aurora限制的配置下，后台与约15秒熄屏Root tick两项通过35.423秒。实际设置页导航、Root连接、Frida启用按钮父控件禁用、只读状态和返回断连均通过。SDK新会话标准探针273.334秒超时（原生预算270秒、scopeRemoved=true）；保留工程诊断再次273.544秒超时，已到Java/Compose/JNI/DEX后下载Release lint依赖。保留部分缓存的标准完整探针/APK导出/普通UID JNI加载1项通过94.172秒，不是冷缓存通过。用户已批准公共只读依赖基线＋私有可写缓存，先ARM64隔离资格、再两份新会话真机资格；现仅增加CI准备/封口/只读与离线机制验证脚本，尚未改正式工具布局或安装手机基线。原270秒和全部检查保持，见APK_DEPENDENCY_CACHE_PROPOSAL。
