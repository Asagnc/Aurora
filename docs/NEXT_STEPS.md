# 当前接续清单（2026-11-10，0.1.40 UI 预览验收中）

当前接续：来源列表／分组编辑／签名展开／撤销确认及Linux版本／维护／说明二级页已实现，产品46fecce宿主221任务50秒通过。主包保留数据更新，来源失败／取消／展开草稿、Linux子页返回与说明横屏130%字体已验；原外观及系统布局恢复，手机本轮结束。真实凭据编辑／撤销确认和输入跨重建仍保留缺口，完整CI42772674262五项全部成功。见UI_REBUILD_20261110及ui-source-environment-20261110.json。下一处处理SpaceBrowserSheet旧面板和错误的“Skills尚未接入”文案，后端已经实现，不重建。

本轮继续：外观整行选择／COUI分组、工具目录与独立详情、Skills独立安装页已实现。横屏实测发现设置导航丢失，已修复页面状态保存；产品a45543f宿主221任务50秒通过，最终主包明暗／返回／键盘／130%字体／横屏补验完成，原外观与系统布局设置均已恢复，手机本轮结束。完整CI42770875328五个任务全部成功；见UI_REBUILD_20261110及ui-settings-rebuild-20261110.json。

2026-11-10 当前接续：模型已按用户最终决定统一详情列表，取消胶囊／自动阈值／展示偏好分支，保留搜索、多选／全选、勾号反馈。共有填充输入／COUI提示弹层、模型目录、同连接多模型及连接编辑／删除已实现，整套 UI 仍在推进，后续不反复更换已确定的列表样式。

本轮手机验收已结束，可以拔线：公开目录读取、同连接保存／切换、编辑留空密钥、取消／确认删除、长连接名顶栏及最终详情列表均已实测；精确自有配置和占位密钥已清理。354 项核心测试通过／16 项跳过，目录元数据请求同字节和原生复用测试通过；编辑保留原 effort／enabled，保存无关连接不抢当前选中。真实供应商命中率待用户配置后测试。产品395820d的完整CI42767158633五个任务全部通过；记录见 UI_REBUILD_20261110 与 ui-provider-rebuild-20261110.json。

当前最高优先级：用户2026-11-10明确要求整体重构UI，以真机Eta为参照，未发布不需要旧UI兼容／迁移。已实际查看Eta提供商／配置／模型／设置页；沿用固定COUI组合，统一45个UI源码文件涉及的导航、表单、列表、弹层与对话。共有填充输入及中转表单重做已开始，整套尚未完成；接续UI_REBUILD_20261110，不把a418首版当最终UI。

2026-11-10最新：用户已批准COUI0.0.1-alpha9先资格再迁移。稳定Compose宿主守卫拒绝实际1.13.0-alpha02传递选择后，独立alpha组合基础资格已过，主包沿同一固定alpha组合接入COUI顶栏／浮动按钮／设置分组排版／开关与表面。消息时间／schema11、复制下移与会话活动时间已实现；真实用户库升级原值保留，数据与恢复47项6.782秒通过，原始数据仅私有忽略目录。主包UI键盘、明暗／纯黑、草稿、横屏／130%字体已检查，最终Debug a8f08f9f已安装／摘要一致，47项在精确包再通过5.003秒；仅本轮自有测试会话／两个临时测试包清理，原表原值保留，手机本轮结束。产品a418ded完整CI42652258424五个job全部成功，最终证据ui-coui-chat-20261110.json。UI_CHAT_IMPLEMENTATION为本轮入口。下文“主依赖未改／设备未更新”为此前隔离阶段状态。

2026-11-09最新UI接续（优先于下文上轮状态）：用户要求对话整体参考新版Eta／ColorOS17，复制只是其中一项，并指出缺少时间。已实现消息nullable时间／schema11、会话最后活动时间、复制移到回复下方／用户保留长按选择。宿主224任务66秒通过，设备尚未更新、迁移夹具只编译。用户批准COUI0.0.1-alpha9先隔离资格再迁移；实际发现它会带入Compose1.13.0-alpha02，不能声称兼容现有稳定基线。隔离预览继续，主依赖未改；见UI_COLOROS17_PROPOSAL、UI_CHAT_IMPLEMENTATION。

最新产品快照：401db41e8a34574a0422dda54c102032a876ceef，官方Material3 Expressive 1.5.0-rc01设置整理已完成首轮，12类真机UI检查及完整CI42641876487五个job通过。手机沿原签名保留数据更新，本轮已结束。Frida17.26.2及worker c9b22da8字节未改，旧a621主包九项／Linux30项证据按原范围保留，正式策略门控false。最近完整未签名审查包仍为release-review-a621f7f-complete，不含本轮UI；新UI正式打包须重绑来源，默认Release真机／正式签名／大字体横屏等缺口仍保留。


这里只列当前实现、实际证据和未完成工作。历史失败、修复及各提交的构建记录保留在PROGRESS、IMPLEMENTATION、DEVICE_ACCEPTANCE_20261027和docs/evidence；不得把历史缺失再次当作待建设。

## 工作约定

- 在现有main连续实现、验证并推送，例行修复直接做。新底层/依赖/持久化选型先比较确认，已批准方案不重复问。
- 用户已授权真机测试，并已自行重启、撤销/恢复FolkPatch授权、解除Aurora的Thanox冻结/后台限制。不自行再次重启或改Root管理器/Thanox配置。
- 当前顺序：用户2026-11-09明确要求UI优先，先整理设置与官方MD3E组件，再接续剩余功能验收；签名与付费模型仍待用户配置密钥，拓展最后进行。不调用真实付费模型，不重装Android Studio/WSL。
- 保留原安装和数据，0.1.40沿用既有调试签名。正式密钥在手机公共Downloads，最终签名阶段再由用户配置；不读密钥、不生成替代正式签名。
- 只有当前Root身份/服务代/环境代/工具版本实际资格通过才开放执行。编译、CI、安装、旧连接的资格都不替代手机当前资格。
- 未知占用、退出或清理失败保留恢复记录并阻断删除；不强删unknown，不重放未知副作用，不以资源退出冒充脚本卸载。

## UI 优先接续（2026-11-09）

用户已批准官方 Material3 1.5.0-rc01。设置、Root／Frida、Linux、工具、外观、供应商与 Skills 首轮整理已实现；最终 Debug 保留数据更新，12 类 UI 检查通过，包含抽屉不自动弹键盘、搜索时底部入口避让、供应商／文件返回设置、详情展开、明暗恢复和输入法。原始截图／XML只留 app/build；大字体、横屏、全部无障碍与默认Release真机仍不算完成。具体实现见 UI_MD3E_IMPLEMENTATION。后端与 Frida worker c9b22da8 不变，正式门控false；旧a621f7f审查包不包含新UI，后续重打须绑定新来源。

## 当前状态与缺口

| 范围 | 已实现并取得的证据 | 接续工作 |
| --- | --- | --- |
| Frida Root Binder/活动账本/Agent | 独立活动意图、guard/cgroup/journal、protocol2 spawn两阶段目标记录、FD/sealed memfd、恢复/删除屏障与Agent接入已完成。Root native/ART、普通App attach、自有包早期ART hook、resume前异常、取消、Root/worker/App死亡恢复均实际通过。 | 早前受限配置下出现App原生SIGSEGV/PC0x320，根因仍未确认。解除限制后六项组合已连续三个独立批次通过（133.086/133.790/134.105秒），不能当因果修复；继续复现/排查与剩余压力矩阵。 |
| Frida最小策略入口 | 纯差异编辑、单次load/读回、Root私有boot基线、Binder身份/protocol、当前身份资格清零、设置页均已接。用户手动新boot后从零捕获基线并启用通过；撤权时普通功能保活、恢复/死亡后资格不继承通过；实际UI禁用入口和只读状态通过。 | releaseQualified=false继续保持；不因当前配置测试通过就开放正式策略启用。无permissive、通用tmpfs执行、全域execmem、持久模块或旧整份策略回写。 |
| 网络捕获/UID/轮转 | libpcap worker、离线PCAP、原始发包/精确回放、HTTP重放；NETLINK当前真实socket UID快照；protocol3/schema9轮转。30分钟低流量1800.018秒/16段/dropped0，以及64段/512MiB预算封口/发布/清账均通过。 | 高负载实测kernel dropped6678，明确非零丢包。熄屏/后台长时与故障完整矩阵仍需补齐；不冒充逐包历史UID或线速资格。 |
| TLS/HTTP代理、静态逆向 | 固定mitmdump12.3.0 ARM64及JADX/Apktool/JRE共享只读版本/租约/Agent已接；实际HTTP/HTTPS/WS/未信任CA、CA导出/私有缓存清理及自有DEX/APK分析通过。最新Root工具回归合计14项51.824秒通过。 | 继续补全任务死亡/取消/恢复矩阵，不自动安装系统CA或改路由。 |
| APK构建 | JDK25/Gradle9.8.2/AGP9.4.1/ARM64原生host、Java/Compose/双ABI JNI/DEX/16KiB/只读布局/Agent构建导出已接，固定180MiB归档导入已主机验证；实际手机标准构建/APK导出/普通UID JNI通过。 | 公共只读依赖基线已接入；新会话标准完整探针98.354秒通过，未配置宿主relay。历史无基线冷缓存超时保留。真实484MB SAF完整安装/清理已在独立空安装区通过，使用固定离线伴随归档；其余故障矩阵及正式签名仍待，见最新补充。 |
| Skills | 私有GitHub API/仓库限定PAT、现有KeyStore/schema10归属恢复、用户钉公钥/BC1.86轻量Ed25519、离线验签、来源/撤销UI与Agent profile已接。实际黄金向量/凭据库/迁移/恢复与信任回归通过；opt-in R8验签smoke通过。 | 自有私仓/撤销UI与默认正式APK、正式签名的最终验收；R8专用测试保留规则不等于正式包逐字节资格。 |
| 浏览器/系统快照/基础运行 | 普通/浏览器172项及启动清理、真实renderer死亡、同源/跨源边界、普通UID系统快照2项、Linux/PTY/Agent恢复已有设备证据。最新浏览器异步/下载恢复、Skills信任和Root副本合计21项7.125秒通过。 | 浏览器/SAF/提供器完整矩阵按DEVICE_ACCEPTANCE逐项核对；不以已覆盖子集代替完整矩阵。当前解除限制配置下后台/约15秒熄屏Root tick两项35.423秒通过，不代表全部后台管理策略兼容。 |

实现入口：ROOT_MANAGED_BINDER、ROOT_MANAGED_TASKS_IMPLEMENTATION、FRIDA_SPAWN_PROPOSAL、FRIDA_AGENT_ACCEPTANCE、FRIDA_POLICY_IMPLEMENTATION、CAPTURE_ROTATION_IMPLEMENTATION、NETWORK_PROXY_IMPLEMENTATION、STATIC_REVERSE_IMPLEMENTATION、APK_BUILD_PROPOSAL、SKILLS_IMPLEMENTATION、SKILLS_TRUST_FORMAT、BROWSER_TRANSFER_IMPLEMENTATION、BROWSER_CHOOSER_IMPLEMENTATION、BROWSER_ASYNC_IMPLEMENTATION、SYSTEM_SNAPSHOT。

## 正在推进：公共只读APK依赖基线

- 用户2026-10-30已确认推荐路线：固定公共依赖只读基线＋会话私有可写缓存；复用Gradle官方GRADLE_RO_DEP_CACHE、既有版本/只读挂载/租约。不共享凭据、配置、daemon、transforms或工程输出，不加数据库/daemon。
- 首轮隔离ARM64 CI41069037872：6项边界测试通过，生产Kotlin工具准备后独立完整探针96.528秒成功；快照检查错误要求40位SHA1目录，而固定Gradle会去掉前导零，导致封口失败。未发布缓存归档，整体不算通过。
- 已核对固定Gradle9.8.2实现并修正比较，新增实物回归；Windows7项中5通过/2项Unix专用跳过，Unix实际证据由CI提供。
- 修正版还验证真实依赖缺项：离线拒绝、联网后只写该会话私有缓存、只读基线不变。随后两份全新自有会话，在原270秒下离线完整构建/APK导出/普通UID JNI加载，且基线不可写、摘要不变。Android夹具已编译/lint108任务通过，251个Android测试签名均void；尚未执行。
- 666cc72修正版隔离ARM64资格CI41071359765成功：8项边界通过、两份全新私有缓存离线完整探针73.256/76.219秒，缺项离线拒绝及联网私有回写64.692秒，基线不可写且摘要不变。966文件展开341,863,935字节、压缩303,975,306字节，650项公开依赖文件；手机2026-10-31两份全新自有会话的完整离线构建/导出/普通UID JNI已通过（1项215.886秒，包含两次构建，各自270秒预算）；两份空写缓存、只读写入拒绝、前后摘要及退出/自有会话清理均通过，不直接授予生产runtimeReady。666cc72完整CI41071281794五个job成功。先前失败/取消保持原记录。
- 只有主机与手机资格、缺项回写/取消/不完整发布/活动租约删除屏障及来源/许可材料完成后，才接正式共享版本。现在沿用同一android-build-v1共享版本，把固定基线封入原生审查输入，安装前核对manifest/逐文件摘要与完整清单，启动器默认GRADLE_RO_DEP_CACHE固定只读路径；新组合包484,215,713字节重复生成SHA一致。手机正式安装/标准探针/导出/普通UID JNI整项已通过225.475秒（含安装），原270秒构建预算保持；自有会话清理、Root断连及临时TLS relay移除完成。runtimeReady只由当前身份实际标准探针授予；95e7b7d完整CI41172360549五job与生产ARM64安装专项41172360761均成功；共享版本故障/完整发布材料校验继续。详见APK_DEPENDENCY_CACHE_PROPOSAL及evidence/0.1.40/apk-dependency-cache-qualification-20261030.json。

## 发布边界

- 已完成完整CI41057445450，5个job成功，HEAD356f157仅文档变化，产品代码与5edfdc3一致。5edfdc3的41057202983被后续推送取消，不计成功。
- 当前完整审查包app/build/outputs/release-review-5edfdc3-complete精确绑定5edfdc319093fa0a38c1c93a47ddc8f14c140c1d，打包时clean；9项载荷/重建报告1,691,202,794字节，APK45,188,830字节，10条checksum独立流式复核，worker离线重链接/guard重建通过。
- worker为0ca9bd596c919af1c6483f7747591a67c3de74c0af33413119bde2f58aa69dd8；65份Frida、41项SDK、584项代理材料。unsigned-review/releaseReady=false；后续正式工具/产品字节变化须重新生成精确包，不把测试夹具提交混称旧包来源。
- 正式签名、用户UI问题处理、真实供应商路由/usage、付费模型调用仍未完成；付费测试等用户配置密钥后进行。缓存新依赖材料也必须追踪来源/许可，不能以CI缓存包代替完整发布材料。


2026-10-31材料收尾：公共基线186个二进制坐标，185份对应源归档（含2份官方固定版本tag归档）、6个无class/dex/native的占位坐标，公开POM许可证声明186项均有记录。787个元数据/通知/源文件封口包204,253,459字节；来源缺项与声明缺项均0，但不宣称总体许可闭包或二进制逐字节重建。SDK审查材料增加固定公共材料、原生base/公共cache/成功资格JSON共4项重建输入，总SDK输入45项；原41项及代理584项按现有摘要复用，正在绑定当前自有Git快照重打完整包。

## 2026-10-31最新接续补充（优先于上文旧状态）

- 用户明确：先完成功能与验收，再处理UI；UI阶段先复查既有Eta比较，不要求用户现在重列问题。历史实拍与批准概念图见UI_AUDIT_2026-10-16、UI_AUDIT_2026-10-21、COMPOSER_B_2026-10-18。历史截图不足以评价当前0.1.40整体，届时重新实拍。
- cba97ab完整CI41173766390五job成功。新的完整审查包app/build/outputs/release-review-cba97ab-complete精确绑定cba97ab62ffb274f50ce5af8fc5fbd8375334292，打包时clean；9项载荷/重建报告2,683,811,595字节，APK45,192,270字节，10条checksum已独立复核。SDK材料45项、代理584项、Frida源码65份；保留unsigned-review/releaseReady=false。guard独立重建与APK字节不同，分别记录来源对应，不宣称逐字节重建。
- SDK生产只读基线：标准探针98.354秒；租约/取消/未派发意图删除屏障3项7.895秒。先完整资格再精确pidfd终止Root的恢复用例1项102.468秒通过，旧资格清零、不重放、租约释放。Linux PTY Root死亡1项3.798秒、Root元数据4项16.668秒通过；错误startTicks均拒绝信号。
- 最新Root/Linux/PTY/离线Agent/网络/SDK矩阵为28通过、1跳过（57.565秒），不能把JUnit的29 tests算作29通过。可选PID namespace探针因当前可见/proc/self/ns/pid缺失跳过；普通PROOT正向因普通UID硬链接不可用跳过，拒绝该输入且保留已有Root版本的负向用例已通过。历史跨版本方法覆盖统计不代表当前APK全部通过。
- 用户已批准Frida17.25.0独立资格；候选构建/ELF与普通Shell版本执行通过，未接生产、未Root注入，不能宣称修复0x320或无需策略。下一步固定源码/运行闭包审查与自有夹具资格，见FRIDA_1725_MAPPER_PROPOSAL。

## 本轮先验收的最新结果

2026-10-31当前Debug回归：成功批次229次执行通过、去重227项；两个跳过执行中的renderer死亡已独立补过，仅可选PID namespace仍缺资格。Root取消夹具首轮目录准备竞争已修正，相关批次28通过/1跳过；SAF包名推断与实际ColorOS选择器不同，按实际包配置后生产取消/导入/清理1项通过。保留两次失败，不改全局默认选择器或生产协议。现有Frida六项133.318秒再通过，仍不声称修好历史0x320或开放入口。见DEVICE_ACCEPTANCE_20261031及精简JSON。当前用户要求人工操作能加速时优先明确请求，不反复自行摸索手机选文件/授权步骤。


2026-11-09接续：用户确认独立包Root整页引导正确、连接成功。主包同步改为管理器先授权后“连接Root”，不改libsu/Binder后端。Frida17.25独立资格首轮0通过/6失败、三次诊断复测失败；原生SIGABRT提示Unable to locate the libc，随后Root Binder死亡。候选不晋升、生产17.24.3及正式门控false保持；9条测试票据经恢复协议收尾，不强删unknown。详见FRIDA_1725_MAPPER_PROPOSAL及精简失败证据。

2026-11-09收尾实测：独立包9个精确自有夹具经现有恢复协议1项4.905秒通过，数据库只读复核待恢复票据0、自有会话0；没有强删数据库或声称脚本已卸载。主包保留数据更新后Root连接/重连/命令/PTY/取消/超时清理6项13.859秒通过；Debug/Release/AndroidTest及双lint共224任务构建成功。见root-guidance-20261109.json。

2026-11-09用户协助大归档选择：两次选择后看似无响应，真机实际成功提示在下方；检查store确认已有完整版本时提前返回，未读取所选归档，因此不计484MB完整SAF导入。已修正Linux页：上方明确已安装、禁止重复选择、进度/取消/结果靠近入口；190任务Debug/Release/双lint成功，更新主包并实际UI核对。61f4f6e完整未签名审查包10条checksum通过；此次后续UI字节变化需重新绑定，旧包不冒充最新。

当前最新完整审查包为release-review-85b790e-complete，绑定85b790e、打包时clean、10条checksum通过；覆盖Root引导与已安装SDK反馈修复。详细边界见RELEASE_REVIEW_20261109。Frida17.25独立设备资格已失败并清账，不再列作“尚未Root注入”；生产17.24.3与正式门控false保持。484MB选择发生过但已有版本提前返回，完整大归档SAF导入仍未通过。

61f4f6e完整CI42565543872五个job全部成功。后续85b790e只改SDK页面反馈，主机190任务及实际页面核对通过；将随本轮推送重新触发完整CI，不把前一提交结果算作新提交CI。

2026-11-09修正版接续：官方17.25.1已修复mapper/Bionic匿名命名空间下agent无法定位libc，当前稳定17.26.2沿已批准独立路线验证。固定worker05abea25；首次Root native/ART1项34.450秒通过，组合5通过/2前置参数失败（未触发这两项Root操作），取消正确参数补测1项16.616秒通过。Root/worker死亡及完整干净组合继续；生产17.24.3、正式入口false保持。当前主包SDK实际标准探针99.728秒，导出/JNI整项103.122秒通过。CI42566743223两次因原17.24.3 devkit公开下载504失败，新增官方Release API预取和三次有界重试，仍由原builder核对固定长度/摘要；未换生产版本或放宽检查。

17.26.2后续实际结果：Root死亡1项17.855秒、worker死亡1项18.734秒通过（精确pidfd，错误ticks先拒绝）；App自杀prepare为故障准备，不计通过，重启verify1项3.151秒通过。正确参数的完整六项131.554秒全部通过，无失败/跳过。本轮共16次通过执行、9个去重用例，原两次参数前置失败保留；独立包待恢复票据只读复核0，普通Shell实际版本17.26.2。仍不是stock策略、完整来源/许可或正式发行资格，不自动晋升生产；详见frida-1726-qualification.json。

CI传输续查：aecf6db的Android工作已越过全部固定devkit/Node预取，进入Android构建；独立Linux frida-session job随后在其另一个固定Linux devkit公开下载504，尚未执行该job测试。现同样使用官方Release API预取固定78,853,964字节输入，显式环境路径仅用于主机夹具，复制前后保持原SHA/长度校验；不更换版本或执行未验证源码。

用户再次手动重启解锁并恢复ADB授权后，启动标识变化已独立核实；17.26.2最小适配/Root native/ART/普通App attach/spawn/异常/取消六项新启动132.171秒全部通过，Enforcing保持，独立包待恢复票据复核0。候选累计22次通过、9个去重用例；不是未适配或stock平台策略资格。主Aurora新启动Root连接/断开重连1项2.571秒通过。bf2d135的Linux Frida及其余3job已成功，Android APK步骤另行重新下载同一devkit504失败，现复用已验证归档输入但重新编译/校验，不复用worker或report。

2026-11-09大归档缺口补齐：新增仅复制进独立资格树的未导出Activity/opt-in测试，生产源码集无该入口。实际OpenDocument由用户选择484,215,713字节f60e350b归档，provider为com.android.externalstorage.documents；空安装区生产安装器完整读取/校验/发布28.280秒，整项55.051秒通过，生产租约感知删除后版本不存在，四份固定离线伴随归档清理完成。非官方网络下载或新安装SDK运行资格。主包新boot标准构建/导出/普通UID JNI另项100.062秒通过，标准探针96.680秒、仍原270秒预算；完整CI42569418729五job全部通过。见apk-large-saf-20261109.json和root-guidance-20261109.json。

17.26.2来源审查接续：新core/Gum/releng等14份固定增量60,368,478字节已通过归档与Git许可blob验证，原65份保留，共79项保守材料；477,598,734字节独立源材料包已离线重链接匹配05abea25。1636479完整CI42571394022五job成功，仍为生产17.24.3 CI。已形成FRIDA_1726_PROMOTION_PROPOSAL，按原先“独立资格后再决定替换”约定，主包版本接入待用户决定；不自动升级或开放。

2026-11-09用户已批准17.26.2接入main，当前更新生产Android/Linux资格SDK固定字节、精确5成员/351,169,328展开字节、版本描述及79项材料目录。主机构建224任务（Debug/Release/AndroidTest/core/双lint）成功；worker05abea25与独立资格相同，79输入完整源材料离线重链接通过。NOTICE79归档/137去重文本，保留上游版权原始空白，仅该生成许可文件免Git空白风格检查。Node桥编译宿主仍固定17.24.3生成同一7.0.13字节，不混入运行时SDK。磁盘预检正确拒绝不足900MiB后，仅校验摘要再清理过期大载荷/重复中间ZIP，cba97ab和85b790e完整旧审查包及所有清单/失败日志保留，回收约8.76GB。主包真机六项及完整CI正在执行；正式策略门控仍false。


2026-11-09主包17.26.2集成：六项129.935秒全通过，Root/worker精确pidfd死亡恢复各1项、App死亡verify1项3.116秒通过（prepare为故障准备不计通过）。首轮4通过/2失败源于同boot独立包已适配但主包无原始基线；使用已有审计脚本严格逆差异验证后引入同boot Root私有原始基线，9项拒绝/证明用例通过，内核策略摘要不变，不回写整份策略。Linux CI42598766393保留22失败/7错误及超时；后续fail-fast诊断42601682617定位frida_deinit→gio_shutdown→_g_dbus_shutdown等待，非脚本清理阶段。一次性worker保留库全局状态至进程退出，不再在attach尾部做全局析构；实际unload/detach/manager close、context drain、监管报告和退出状态要求保持，正在重跑全部相关资格。正式门控false保持。


2026-11-09主包17.26.2集成最终批次：a621f7f的worker c9b22da8，已安装保留数据的Debug主包九项/九个去重用例全部通过；六项130.767秒，Root死亡17.760秒、worker死亡18.803秒、App死亡verify3.000秒。错误startTicks拒绝、精确pidfd、卸载/分离/监管证明、已resume目标存活与不重放均沿原断言；App自杀prepare不计通过。Enforcing复核保持。本批使用当前boot经审计的原始基线，不能冒充主包从空基线的新boot验收；独立包的新boot结果仍按原版本分别保留。当前手机工作结束，暂不需要连接。

Linux17.26.2退出故障已修正并取得30项80.351秒全通过（CI42602162251的frida-session job129109148124）；原22失败/7错误和后续fail-fast诊断记录保留。一次性worker不执行进程全局GIO析构，Frida context已停止，自有源/描述符已关闭，显式会话清理事实及最终监管报告保持；全局库缓存由进程退出回收，不把它描述成全库析构通过。Android/Release/AndroidTest/双lint221任务16秒成功。

完整未签名审查包release-review-a621f7f-complete精确绑定a621f7f06a455b0a968c0a2cfeae701977ea3618、打包时clean；9项载荷/重建报告2,744,829,128字节，APK45,349,526字节/SHA256 271e0eeb725ca0d14afbb728608710af0fb13771a351d92f6308cc05eda0fc52。10条checksum独立流式复核一致，79份Frida输入离线重链接同c9b22da8；SDK45、代理584项固定材料已重绑来源快照。unsigned-review/releaseReady=false，不宣称正式签名或默认Release真机资格。证据见frida-1726-main-integration.json及release-review-a621f7f-complete-*。


最终核对：a621f7f完整CI42602162251五个job全部成功。后续提交仅增加/更新验收文档与证据，产品字节和完整审查包仍精确对应a621f7f，不重写旧包来源。
