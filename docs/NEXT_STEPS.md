# 当前接续清单（2026-10-25，0.1.40）

## 2026-10-25 资源工具首次编译失败与兼容头修复

ARM64资格CI40177570595：JVM job再次成功，资源job在真实编译时缺utils/TypeHelpers.h失败。根因是固定源码的归档内兼容链接未物化，不是资格通过。现只将可在同一固定归档内解析的有界链接复制为普通文件，越界/循环/非文件链接不物化；原始归档保持不变。新增实际TypeHelpers路径及越界边界回归，累计4项归档检查本地通过，重新CI另记。已完成的资格报告目录禁止覆盖，避免后续失败误留旧成功报告。没有手机安装。

## 2026-10-25 ARM64 JVM实际通过与资源工具源码闭包

独立CI40177012737在真实ubuntu-24.04-arm成功执行固定JDK25.0.5+8、Gradle9.8.2离线Java编译与生成类运行，报告保存为linux-arm64-jvm-qualified.json。这不是Android执行或完整APK资格。

资源候选15份精确Git子模块/子树归档已实际取得，共47,633,935字节；14项Gitiles按路径/Git模式/内容SHA256固定，根构建适配器固定归档SHA。新增独立ARM64原生aapt2/aidl/zipalign构建实验：手动应用固定补丁、禁用CMake自动git拉取，保留原始源码归档，不物化格式化/兼容链接；若编译需要缺失链接必须明确失败，不能用占位文件掩盖。host编译依赖记录为CI实验环境，不作为手机安装闭包。实验结果另记，工具尚未发布/安装。

## 2026-10-25 ARM64 JVM/Gradle资格入口

已固定Temurin25.0.5+8 AArch64 JDK与Gradle9.8.2的官方发布SHA256/字节数，新增仅真实Linux ARM64可执行的资格脚本；Windows仅获取固定输入，不伪装执行。原有Root chroot/租约方案不变；工具仍未安装到手机。3项归档边界测试本地通过。独立手动workflow使用公共仓库标准ubuntu-24.04-arm，运行离线自有Java工程的编译与实际执行，结果另记；此步骤通过也不代表aapt2/JNI/完整APK已资格。

c20e885完整CI40158211528三job成功，包含28真实Frida、自有fixture构建与新增5项spawn记录/精确恢复主机测试。后续模型目录变更本地构建已通过，远端跟踪最新提交。

## 2026-10-25 构建链路线确认与公开模型核对

用户已确认APK_BUILD_PROPOSAL的Root chroot/共享ARM64 JDK25＋完整Gradle/AGP路线，先来源与主机资格、通过后再安装。签名密钥在手机公共下载目录，留最终集中验收处理，当前不访问手机。模型目录已按10家官方公开文档复核至10-25，新增Kimi K3.1 Pro/高速版、MiniMax M3.1 Flash与严格effort编码，核心269项257通过/12跳过。Debug/Release/testAPK/lint/自有fixture本地通过；无付费请求。见PROVIDER_REVIEW_20261025.md。

发布包补aapt2身份/版本/SDK与非debuggable、zipalign检查、dirty标记；4项打包回归通过。当前最终手机与真实账号缓存/费用仍未验收。新增工具链资格实验接续进行，不能把方案获批记成工具已安装可用。

## 2026-10-25 spawn 首轮完整CI与最终设备夹具

fc6b4c8完整CI40157675374三job成功，28项真实Frida（新增2项自有原生spawn）、原生监督与Android/载荷审计通过；独立源码CI40157695350也成功，24份自有源码/65份上游材料离线重链接通过。Windows完整材料包已实际生成并核实。后续补同boot暂停目标恢复、resume意图/旧boot不终止的2项主机用例与pidfd回收绝对截止，等待新CI。

新增单独testOnly自有Frida夹具APK及FridaSpawnDeviceTest，明确3个测试参数才允许运行。检验早期Java方法hook、真实工具/账本/产物链；清理只针对固定自有包，不以force-stop冒充未知spawn的退出证明。Debug/Release/testAPK/lint/夹具APK本地通过；手机仍未操作。手机APK构建链已形成具体比较APK_BUILD_PROPOSAL.md，等待用户确认新增工具链路线；正式签名密钥安排也待确认。

## 2026-10-25 spawn 与完整材料包继续实现

用户确认推荐恢复方案后，已增 protocol2 包名 spawn、Root 私有64字节阶段记录、捕获前未知/恢复执行意图未知保留、同 boot pidfd 精确回收尚无 resume 意图的本任务目标。旧 protocol1 attach 票据省略新默认字段，保留原摘要；schema仍8。新增5项核心回归通过，核心268项256通过/12跳过；Debug/Release/testAPK/lint通过。新增2项真实Frida spawn与3项原生记录测试待Linux CI，Android包启动尚未执行。

新增发布材料打包器，检查APK/worker/桥/源码对应及两份离线重建报告；未签名审查和固定证书验证分开，不能自动宣称设备已验收。此前ff3cece的CI40156042220因GitHub绑定下载网络超时失败，另两job通过；现补最多3次仅网络错误重试，摘要/文件/永久HTTP错误不重试，3项回归通过。见FRIDA_SPAWN_PROPOSAL.md、RELEASE_PACKAGING.md；新CI另记。

## 2026-10-25 当前完整验证结果

实现提交3e3c227的完整CI40155403134三个job全部成功，含26项真实Frida、原生监督、核心与Android Debug/Release/testAPK/lint、APK资产/ELF和源码重建审计；独立源码CI40155411773也成功，65输入获取与离线重链接通过。此前18c1474的桥输入换行失败已修复，失败记录保留。当前本地核心263项251通过/12跳过。手机、付费模型、正式签名均未执行，下一步是集中设备资格与签名发布准备，参照FRIDA_AGENT_ACCEPTANCE.md。

本文件是当前摘要；PROGRESS/IMPLEMENTATION中按时间倒序的早期记录可能包含已完成的旧待办，不应将它们重新当作当前缺失功能。细节以对应实现/证据文档为准。

## 工作与验收约定

- 用户明确不长期保留无用安装半成品：成功才发布，失败/取消清理暂存；清理失败或未知占用保留并报出。Agent通过管理接口安装/更新/移除共享只读工具，通过普通文件接口管理共享工作区，运行中版本回收需真实退出证明。

- 继续已授权实现、测试并推送main，少做常规汇报。新底层/依赖/持久化选择仍须检索比较、形成可审查方案并让用户确认，例行修复不重复问。
- 当前不连接、更新或操作手机，也不重装Android Studio/模拟器/WSL。所有最终手机测试集中进行；保留Aurora数据和安装，测试辅助包完成后清理。
- 手机最后已验收版本0.1.32；代码0.1.40。用户已在FolkPatch应用内授权，不能等Magisk式弹窗。真的需要处理授权/阻碍时优先联系用户。
- 本轮不调用真实付费模型，不要求用户发送密钥。gh已登录，不使用聊天历史泄露过的PAT。
- 不能从编译、主机夹具或UI推断Android性能领先、所有模型最高缓存、任意网站无限访问或全协议解密。

## 本轮已经实现

- Root抓包：libpcap独立worker、严格整包预算/截止/取消、结构化统计/截短计数、Root选定PCAP属主发布与Agent离线tcpdump分析。普通UID不提供实时抓包，无VPN。见ROOT_CAPTURE_IMPLEMENTATION.md。
- 浏览器异步：每次独立WebMessagePort、精确origin/页面revision、awaitPromise选择、一次结果、关闭/截止/取消收尾；同步保持直接路径。加载从50ms轮询改回调StateFlow，外层取消不再被工具/批处理吞掉。见BROWSER_ASYNC_IMPLEMENTATION.md。
- 原有Root命令/PTY、Root Linux执行与Agent入口、会话文件通道、图片发布、事件日志、Skills内容版本管理、后台Agent宿主等已实现，勿从早期记录重新列为从未做过。

- 0.1.38 Agent文件供给/页面流式下载已接入，64KiB背压与会话产物发布，实际事件合并显示字节；0.1.39已补原生chooser、精确URI快照及旧上传快照有界回收；0.1.40新增账本关联的下载中断恢复；未登记/类型不明记录不自动删除。0.1.38新4项核心含14个生产JS子场景；4项Android用例仅编译。

- 原生chooser现在提供会话/共享资源与SAF入口，选定文件流式复制、原生URI保留到关页/删会话；未交付的选择在导航取消。新增5核心与5项Android回调生命周期用例，详见BROWSER_CHOOSER_IMPLEMENTATION.md；手机仍未执行。

- 下载现在先登记目录再创建文件；启动/会话Agent开始时有界处理已中断账本，保留完成产物和uncertain状态。schema7部分索引避免重复扫描完成历史；见BROWSER_DOWNLOAD_RECOVERY.md。

- 传输诊断新增精确白名单错误码，网页异常原文不入账本；修复 Agent 上传 accept=*/*，新增生产脚本通配符/错误形状验证。下载展示名/MIME已补齐，严格握手、规范路径产物入口和名称/类型提示，不改固定磁盘名；新增1项Android仅编译。真实WebView/SAF资格仍待最终手机验收。

- 静态逆向方案已批准；来源/准备与共享安装管理已实现；Agent可安装/更新/删除/清理，持久引用与删除屏障已连接，原生只读挂载与reverse_analyze已接入；runtimeReady必须经过当前Root身份/环境代/bundle的真实CLI启动资格，手机尚未执行。

- 探测/分析统一无图形JVM参数；已做固定归档43 ELF名称盘点（不是装载验证）。清理失败现在在Agent错误和状态中可见，源码获取失败也清理自己创建的partial。

- 修复任务固定Linux代与逆向probe的全局当前代错配；状态观察不取租约，probe与分析共同固定同代。扩展Android版本切换断言，最终设备执行仍待进行。

- 逆向资格现按Root身份/环境代/bundle隔离，最多32项；旧probe完成必须匹配当前尝试，删除/重试/淘汰后迟到结果不能恢复资格。原持久任务删除屏障不变；新增3项核心回归。

- Linux执行/逆向分析须明确完整退出字段才判成功；非法超时和多余参数在产物登记前拒绝。新增2项核心回归已通过，原生协议与持久记录不变。

- 已补JADX按类分析与5项静态逆向Android资格用例；自有DEX的Windows全量/按类实际解码通过。Android用例仅编译，见STATIC_REVERSE_ACCEPTANCE.md。

- 修复静态逆向产物白名单与三步probe重复登记，结果列出全部步骤日志；普通错误/退出小投影持久化，设备用例改走真实账本链，仅编译。该次修复schema仍7；本轮Root受管任务基础已升级8。

- 2026-10-24 Root受管组合已批准；schema8/不可变票据与精确证明/删除屏障、封印memfd快照基础已实现，详见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。基础9项+发送通道6项Android用例仅编译；AF_UNIX脚本FD开发通路已接发送方/worker，Root Binder派发、任务监督/恢复与Agent仍待接。

- 退出事实/父进程控制原生开发通道已实现，52字节AEX1与精确进程/票据绑定；attach失败无session不再当作已detach。7主机控制+19真实Frida用例已在完整CI39997696909通过；Root Binder/cgroup guard/持久恢复/Agent仍待连接。见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

- 新增事件驱动监督观察器：owner/worker pidfd、严格报告流、绝对截止，最多15秒协作窗口且不侵占调用者强制回收预留；10项自有原生子进程用例已在CI40001467909独立job实际通过。NDK30/API36 ARM64夹具链接/16KiB通过。尚无实际cgroup guard/持久退役/Binder/Agent接入，见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

- 独立Root journal已实现意图/worker身份/放行/持久退役与同任务磁盘锁；启动不可重放，退役前后迟到启动互斥，坏记录/部分写保留。17项Root主机故障用例待CI，NDK30 ARM64链接/16KiB已通过；还没有实际worker fork/cgroup guard/Binder/Agent接入。

- 原生admission/GO基础已接journal与已有cgroup算法：fork子进程先入域并等待，捕获真实PID/start/boot后持久READY，RELEASED后单次GO；拒绝自动回收，父死保护和跨exec FD隔离。10项Root cgroup/自有原生worker用例已在CI40005725398独立job实际通过；仍没有完整Root guard主程序/Binder/Agent或Frida设备资格。

- 新增worker最终流/资源收尾持久记录，严格身份与完整EOF，明确NOT_CREATED/UNKNOWN与已退出的区别；8新Root故障用例（journal合计25）待CI，ARM64夹具链接通过。继续独立guard/恢复，不每个阶段停下来等待用户“继续”。

- 已补受管cgroup创建计划：先持久化parent身份/随机name/guard/boot再mkdir，填补完整记录前崩溃留下空组的窗口；恢复只移除精确空组，未知已占用组保留。9项Root主机用例待CI，ARM64夹具链接通过；继续独立guard主程序/恢复。

- 独立coordinator/guard开发CLI及recover已接现有组件；专用subreaper+pidfd回收自己的后代，严格区分worker报告、真实进程退出和域回收。14项Root模拟worker生命周期用例待CI；ARM64完整链接通过。尚未APK打包/连接Root Binder或Agent；继续真实Frida端到端资格和产品接口。

## 当前接续（优先于下列历史快照）

18c1474 的源码 CI40155072277 成功，真实 Frida/监督 job 成功；完整 CI40155067590 的 Android job 因桥输入 CRLF/LF 摘要差异失败。现固定仓库 LF 输入及精确摘要，桥产物 SHA 未变，本地重新打包/lint通过；修复后完整 CI 另查。

ARM64 worker、固定 Java 桥及许可文本已打包；Agent `frida` 提供 status/probe/attach，固定当前 Root 身份、目标 PID/start、脚本 SHA 和桥版本。原生与 Java 资格仅在当前 Root 服务内存中有效，须固定自测成功、独立清理证明与 Root 私有日志实际调用证据同时成立。workerSucceeded 不代表异步用户脚本完成。退出屏障后发布有界会话日志，日志发布失败不丢失清账证明。

修复 GO 后未尝试 attach 的独立退役证明，以及已验证新 boot 对旧资源的退出证明；同 boot 未知状态继续阻断。Debug/Release/testAPK/lint 本地通过，核心测试 263 项，0 失败、0 错误、12 跳过。新增 Android 集成用例仅编译，无手机或付费模型调用。源码容器身份修复及 65 输入离线重链接另见 FRIDA_SOURCE_MATERIALS.md；最终 Root/ART、服务死亡/取消矩阵与正式签名仍待集中验收。见 FRIDA_AGENT_ACCEPTANCE.md。

## 当前证据

- 1f601fa完整CI40150310737三job成功；另一个独立源码workflow40150310929因Gitiles容器字节不稳定而失败，不能混记。已改为固定13项Gitiles的文件路径/Git模式/blob内容身份，未放松源码校验；新回归通过，重新运行CI另查。

- worker/固定Java桥/132份去重许可文本现已进入实际APK，Gradle沿用相同资格构建器，strip后worker仍为a1486cd…，Release unsigned44,448,774字节，Debug85,268,848字节。APK ELF/资产摘要/notice审计与Debug/Release/testAPK/lint本地通过；新增1项ARM64版本/桥快照Android用例仅编译。Root Binder桥选择只接受安装包固定描述；Agent attach、最终Root/ART/日志产物接入及正式签名仍待完成，不显示注入ready。见FRIDA_SOURCE_MATERIALS.md。

- 00c5782完整CI40094912932三个job成功，Binder/活动账本/Agent恢复代码与原生回归通过。随后增加每次最多4项恢复及64个owner有界游标轮转，避免未知项长期挡住后续票据；新增1项Android分页仅编译，本地Debug/Release/testAPK/lint通过。

- 发布源码材料已实际取得65份固定归档（288,562,897字节）及157份许可/版权文本记录；真实描述表审计28嵌入资源，识别NDK29 agent与NDK30外层的不同来源、Java桥的3项发射shim。349,673,283字节材料ZIP实际离线重链接得到相同worker SHA。见FRIDA_SOURCE_MATERIALS.md；完整SDK重建、APK notice/worker/桥携带与Agent注入仍不是已完成项。

- 本轮已接Root受管Binder派发/恢复、PFD所有权、精确结果清账、Agent status/recover与删除前恢复。三项新Core回归通过，255项243通过/12跳过（含2未提供样本）；Debug/Release/testAPK/lint及双ABI guard可信摘要/ELF审计通过。新增6项Android用例仅编译。worker/桥尚未打包，注入仍不可用，不显示ready。见ROOT_MANAGED_BINDER.md；接续发布材料闭包、真正Agent注入入口和最终手机资格。

- 发布构建已增加最终LLD map并实际交叉链接：ARM64 worker89,143,968字节、1,894个保留归档成员（含调试段来源，非精确运行时依赖闭包），SHA256 a1486cd58c2d25ca2caa014075fb5eb9ac85623c5a135f03ba87c4b28ab49fb1。CI将保存map/成员清单，继续逐项固定源码/notice和嵌入资源核实；尚未完整发布打包。

- 51620c6完整CI40076439930三job成功，包含新的Linux实际Java子进程取消/截止测试、19 guard/25真实Frida、Android构建/lint与离线源码重建。见ci-40076439930-passed.txt。继续发布输入盘点：官方Frida最新仍17.24.3，确认顶层Core/Gum/releng gitlinks、2,598个实际devkit成员、嵌入agent/helper与桥包缺少独立许可文件；已取得约33KiB固定许可文本，尚不是完整闭包/源码。见FRIDA_RELEASE_AUDIT.md；不因此声明已打包或ready。

- 新执行器本地完整构建/lint成功；核心252项242通过/10平台跳过，含三项实际Java子进程用例。双ABI APK审计与45文件源码归档离线等哈希重建通过；见local-managed-runner-passed.txt。新增Android六项仅编译（五pin、一持久通道拒绝）。新CI推送后核实，Linux TERM清理hook尚不从Windows结果推断。

- 9b42c24完整CI40075197598三job成功：19 guard、25真实Frida、Android构建/lint、双ABI APK审计与源码归档离线等哈希重建均通过。之后新增Root固定可执行inode/直接coordinator执行器、核心阻塞退出屏障和accept后读取READY的FD交付；三项实际Java子进程测试本地通过，五项Android固定文件与一项普通UID通道拒绝用例仅编译。见ROOT_MANAGED_PROCESS.md；Binder派发/账本收尾/Frida worker打包仍待接入，不显示ready，不操作手机。

- c52f10a完整CI40021399232三job成功：11观察/12放行/18 guard/25真实Frida通过；见ci-40021399232-passed.txt。guard现已接APK（Frida Core worker仍未打包/Agent未注册），共享显式sources清单、版本只读探测、严格系统依赖与ELF硬化检查。Debug/Release/testAPK/lint、双ABI APK审计及源码归档离线等哈希重建本地通过；Release guard ARM64 48,920字节、x86_64 50,448字节。新增2普通UID打包用例仅编译、guard主机用例增至19待新CI，未操作手机。

- b89dbd8的CI40020643752放行回归发现启动前取消可丢最终流（无GO/无exec、exit125但flags0），未记整体通过，18 guard用例因此未执行；独立25真实Frida job成功。已修复：真实GO成功前禁止发送协作命令，仍按pidfd等待并读取最终流，增加专门观察器回归；放行前已知资源退出可形成独立未派发证明。NDK guard 44,936字节/16KiB通过，Core249项239通过/10跳过。新CI推送后核实，证据ci-40020643752-launch-failed.txt。

- 82473bd完整CI40018887965三job成功，Root记录/target-exit代码及Android构建/lint通过；见ci-40018887965-passed.txt。随后新增可选cooperative-cancel开发模式（TERM/INT先通知guard、coordinator等收尾）、父线程死亡通知与有界收尾。12放行/18 guard/25真实Frida用例待新CI；NDK ARM64 guard 44,872字节/16KiB链接审计通过，Core249项本地239通过/10跳过。仍未打包或接Binder/Agent。

- c58f3dd完整CI40017402355三job成功，含派发前退役、上传残留恢复、Android基础Files API门禁、24真实Frida主机用例及Android构建/lint；见ci-40017402355-passed.txt。后续Root记录读取/target-gone待新CI；没有手机执行。

- 已补Root锚定目录FD的小记录读取器及target-gone独立恢复证明：仅确认worker/domain资源退出后，原目标boot变更、PID确实不存在或startTicks变更可释放精确意图，不声称已显式unload。Root观察器仅查询，EPERM/隐藏proc/坏记录不算退出。核心249项：239通过/10跳过，Debug/Release/testAPK/lint本地通过；Root读取/观察及新增账本Android用例仅编译。尚未接Binder/Agent，不算手机验收。见local-managed-target-recovery-passed.txt。

- 新增派发前持久退役的独立证明及精确账本完成路径，不伪造worker退出；Core新用例通过、Android事务/删除用例仅编译。发现并修复BrowserUploadSnapshot/StaticReverseStore生产路径及测试里的Files.readString/writeString（基础Android36不可用，SDK标since36.1），增加针对性CI门禁。核心248项：238通过/10跳过，Debug/Release/testAPK/lint本地通过；见local-managed-retirement-passed.txt。Root Binder/target-gone仍待接。

- a36e31d完整CI40015005822三job成功：23项真实Frida主机测试（含4项独立guard端到端）通过，Android构建/lint通过；见ci-40015005822-passed.txt。之后新增第24项强杀worker及Core解析器，后续CI另查。

- 新增Core原生记录严格解码与正常清理证明映射；本地246项核心测试：236通过/10平台跳过，4新用例全部通过。ARM64 guard自动审计43,152字节/16KiB通过；实际Frida强杀缺失报告用例及CI构建脚本推送后核实。尚未连接Root Binder或Agent，不能显示ready。

- 336deb8完整CI40013810311三job成功，14项独立guard/真实cgroup模拟worker用例通过，原19项真实Frida用例另计；见ci-40013810311-passed.txt。随后新增4项真实Frida穿过guard的端到端用例（共23），推送后核实，不当作手机或已打包产品功能。

- 1c73a8d完整CI40009832615成功，包括9项创建计划崩溃窗口Root用例；见ci-40009832615-passed.txt。

- 7025c34完整CI40008334926成功（journal/退出持久化25项实际通过）；证据ci-40008334926-passed.txt。

- c6fce52完整CI40005725398已成功；证据ci-40005725398-passed.txt。

- c6fce52的40005725398 managed-supervision job成功：9接收+10观察+17 journal+10实际Root cgroup放行用例通过；完整workflow待核实。见ci-40005725398-managed-passed.txt。

- aeebb08完整CI40003765109成功，17项Root journal故障用例实际通过；证据ci-40003765109-passed.txt。本轮新增admission/GO的10项用例随后核实。

- f53d824完整CI40001467909现已成功，三job均成功；证据ci-40001467909-passed.txt。以下早期“整体待核实”是当时状态。本轮Root journal的17项用例推送后另查。

- f53d824的40001467909 managed-supervision job成功：9接收+10观察主机用例实际通过（包含15秒真实等待）；完整workflow待核实。5d9ebff的40001398070被替代取消，不记通过。证据ci-40001467909-managed-supervision-passed.txt。

- 51a5658完整CI39997696909成功：7控制/解码、9脚本socket、19真实Frida生命周期及Android构建/lint通过；见evidence/0.1.40/ci-39997696909-passed.txt。随后b7c3882完整CI40000038379也已成功，9项严格EOF接收用例实际通过；10项观察器用例已在f53d824的CI40001467909独立job通过；完整workflow尚待核实。

- cc84c22完整CI39993061650已成功，覆盖握手前取消修复；随后原生监督/事实通道CI推送后核实。
- dbea45a完整CI39991608445成功，9真实socket+11真实Frida通路、完整Android构建/lint通过。随后握手前取消修复新增Android用例仅编译，Debug/Release/testAPK/lint本地通过，最新CI推送后核实。

- 1e5baf1完整CI39989586948已成功，含8真实socket/8真实Frida生命周期。随后端到端脚本FD入口本地构建通过、最新CI推送后核实。

- Frida805f5cd完整CI39967903594成功，8项真实主机测试通过；本轮App账本修复本地构建通过，完整CI39971396204已成功。

- 按类分析/设备用例f2d3b4b完整CI39959191594成功，证据evidence/0.1.40/ci-39959191594-passed.txt。Frida构建原型526c4c4完整CI39960629598也成功；本轮附加会话新增CI推送后核实，均不是手机运行证据。

- 7e750f0完整CI39956810632成功，覆盖此前并发资格与严格退出证明；e2f14b4的39955989822被后续提交取代而取消，不记成通过。证据evidence/0.1.40/ci-39956810632-passed.txt。

- 上轮任务固定代修复3beb4c7完整CI39953323831成功，证据evidence/0.1.40/ci-39953323831-passed.txt。

- 原生挂载bedcfd3完整CI39925157055已通过，3新挂载测试在日志中实际成功；无图形profile与清理诊断556ecf6完整CI39947239121也已成功，见evidence/0.1.40/ci-39947239121-passed.txt。

- 静态来源/准备层d16f49d完整CI39920712690已通过；后续共享管理/挂载/无图形profile完整CI亦已通过（见上项），Android安装/运行仍未测试。

- 9517ea9完整CI39909014575成功，包含核心/签名镜像/POSIX/PTY/Root原生主机/PRoot内核与生命周期/抓包/Android Debug、Release R8、testAPK、lint及ELF/源码检查。证据evidence/0.1.37/ci-39909014575-passed.txt。
- 当前本地核心242项：232通过、10项平台跳过（含新POSIX清理失败用例）。异步JS14、传输JS18场景分别属于各自一个JUnit测试，不重复加到242总数；下载恢复原5项与本轮新增1项元数据Android测试仍仅编译。
- 随后仅补测试任务对mjs文件的输入追踪，以及Android跨域导航夹具，core/testAPK/lint本地再次通过；2214641完整CI39910230241也已成功，见evidence/0.1.37/ci-39910230241-passed.txt。0.1.38实现40d96bc完整CI39913505934已通过，见evidence/0.1.38/ci-39913505934-passed.txt；0.1.39实现d403a4f完整CI39915958302也已通过，见evidence/0.1.39/ci-39915958302-passed.txt；0.1.40下载恢复b75113f完整CI39917911407已通过；后续传输错误码849561c的CI39918760978被新推送取代而取消，4c60026的CI39918856612同样被后续推送取代而取消；跟踪最新main运行，不能把cancelled记成通过。
- Root抓包主机外层12项、内层私有netns10项成功；新Root真机7项与BrowserAsyncTest六项仅编译，没有执行手机。

## 尚未实现，建议继续的顺序

1. **浏览器文件传输**：Agent与用户页面的上传/下载、会话归属、可观察进度/产物、取消/删除协调。先比较onShowFileChooser/精确URI授权与字节传输，核实程序点击文件输入的用户手势要求；下载需比较现有OkHttp、WebView网络上下文与系统下载器，正确处理各会话Cookie、HTTP/HTTPS/SOCKS代理、重定向和POST/blob。不能把GET重发或直连降级当所有下载都支持。新的通道/持久化设计先确认。组合方向已获确认；0.1.38已实现Agent二进制上传/页面下载，详见 BROWSER_TRANSFER_IMPLEMENTATION.md。原生chooser与精确URI快照、旧上传快照有界回收已接入0.1.39。0.1.40下载中断恢复已接入。错误诊断、文件展示/MIME已接入，接续实际WebView资格；勿重复确认同一方案或把Agent路径当全部浏览器传输。
2. **逆向/CTF工具包**：动态Frida新运行器组合已比较并写入DYNAMIC_REVERSE_PROPOSAL.md；用户要求综合取优后，已做真实devkit摘要/NDK交叉链接/外层16KiB与体积资格，原型尚未打包。attach/有界消息/取消与卸载分离已做原生实现并交叉链接，3输入用例CI已实际通过；6真实Frida用例在事件上下文/关闭顺序/上游设备后端启动屏障修复后，39965185125的独立job已实际全通过（含5次身份快速退出子场景）；Java桥固定开发期构建已通过，第7主机加载用例39966517578独立job实际通过；native post原型已接，第8项选定脚本单次执行用例与805f5cd完整CI39967903594已实际通过；Root产品接入新增持久化/FD方案ROOT_MANAGED_TASKS_PROPOSAL.md已获确认；schema8账本/票据证明/删除屏障与封印memfd基础已实现，AF_UNIX/SCM_RIGHTS脚本通路已接发送端/worker，Binder、受管执行和恢复待接；原生上限250秒已由CI39989586948执行，新增3项Frida通路在39991608445实际11项通过，socket9项与整体CI均成功；尚未打包或注册Agent工具，spawn/ART资格/Root与Agent接入/持久恢复仍未完成，见DYNAMIC_REVERSE_IMPLEMENTATION.md。静态运行方案已研究并写入STATIC_REVERSE_PROPOSAL.md，JADX1.5.6+Apktool3.0.3、Temurin27+35共享Root chroot按需CLI，用户已确认新增工具安装/只读挂载与租约布局。编译期目录、校验与暂存准备层已实现，官方包实际字节验证通过；共享安装发布/移除/清理及Agent管理接口已接入；持久任务bundle引用、精确退出证明和删除屏障已接入；Root只读挂载/能力协商3/固定CLI资格/Agent分析执行现已接入；最终手机上实际JRE27/CLI/自有APK资格与故障验收仍待进行。详见STATIC_REVERSE_IMPLEMENTATION.md。JADX、Apktool、原生库分析、Frida等实际ARM64运行器、版本化共享依赖与Agent工具，不用普通SKILL.md假装这些二进制已安装可用。
3. **手机APK构建链**：当前一手检索见APK_BUILD_RESEARCH.md，尚未定新增运行器/缓存布局。实际ARM64可用JDK/aapt2/D8/R8/签名/安装链，含产物和失败诊断；Linux基础环境存在不等于已能编译APK。
4. **网络进阶**：发包、HTTP修改/重放、TLS解密、UID归属和长时轮转。抓包不代表已提供这些功能；按需独立运行，不能给每个模型请求增加重型代理。
5. **模型与缓存**：刷新当前稳定在售型号/端点能力、网关能力探测及各家缓存/期限策略。真实账号路由/命中/费用/延迟验证留用户允许真实连接之后；现有序列化/原生历史/前缀策略不能称全部模型最优。
6. **其他增强**：Skills私有认证/签名、更多结构化系统API工具、浏览器跨站frame与renderer完整矩阵、正式发布签名/图标检查。先核实当前实现，不能因历史待办重复建设。

## 已实现但最终验收尚未完成

- Root抓包7项、异步WebView六项（含跨域与同URL刷新）、后续Skills/图片/日志等累计设备用例的增量集中回归。
- Root元数据回收新增流程、管理器外部撤权、未知恢复、会话删除、熄屏/后台/Thanox组合与长期压力。
- 普通PRoot的Android16完整资格仍不足；本机此前普通硬链接探测失败，executionReady=false，不加link2symlink/QEMU等隐式替代。Linux主机成功不能让其在用户手机上自动可用。
- 固定工作负载的Release性能：Agent任务正确率、TTFT/整轮时延、工具时间、CPU/RSS/耗电、抓包吞吐与内核丢包。未测不宣称优于Eta/DSHA。
- 手机镜像真实联网、真实SAF/提供器/断电及崩溃恢复边界，不能只用本地模拟故障替代。

## 已批准，勿重复确认

浏览器组合传输（原生chooser + 有界二进制Agent供给/页面下载）；原生SQLite WAL；仅任务期间有界CPU锁；前台Agent服务；共享版本化SKILL.md；Ubuntu共享代/Root原属主/chroot私有挂载和任务cgroup；Root批量文件通道；有界旧元数据GC；Root-only libpcap worker+离线tcpdump；浏览器每次独立WebMessagePort。对应方案文档均在docs。

最终手机操作分别参照ROOT_CAPTURE_ACCEPTANCE.md及BROWSER_ASYNC_IMPLEMENTATION.md；不得现在因看到清单就请求连接手机。
