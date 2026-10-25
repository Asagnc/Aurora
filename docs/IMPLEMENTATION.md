## 2026-10-25 AGP strip校验与代理源码对应

AGP/JNI第三轮40206074783已实际构建50任务并生成APK，但最终审计发现C++运行库未按预期剥离；现有shell将strip转发objcopy不兼容AGP的-o选项，AGP明确警告退回原库。已根据固定LLVM源码改为同一原生二进制的strip/ranlib文件名分派，添加真实-o输出/JNI导出/原始输入不变及ranlib符号索引实验。APK现在在后置审计前保留以便诊断，失败不记资格通过。

代理固定上游commit ac4f4cf7、UV锁git blob b96fe291已核对，43份源码与候选ARM64 wheel合计69,723,152字节只读审计。12个内嵌原生组件精确匹配上游wheel，均有来源/版本/摘要与许可材料；libpython/libgcc及静态Rust/OpenSSL许可闭包仍未完成，licenseClosureVerified=false。新增冻结运行时身份探测与5项源码边界回归，不导入下载代码。无手机/付费调用。

## 2026-10-25 原生CMake/Ninja JNI成功与Gradle对齐

CMake首次40204855557已配置/编译，但官方NDK目标材料遗漏libatomic导致链接失败；补同固定归档的双ABI libatomic后，40205203214真实ARM64 CMake4.1.2/Ninja1.13.2通过双ABI JNI构建、16KiB/导出/依赖核验。目标数据3318项168450397字节，无外来host ELF执行，仍非NDK全部补丁等价或Android运行证明。

AGP Java/Compose/JNI入口已接同一候选，核对APK内双ABI JNI/C++运行库、真实strip结果和16KiB zipalign。首轮40205366822在NDK短HTTP体阶段被固定长度检查拦截，尚未进入AGP；已补短EOF/IncompleteRead的有界传输重试与3项真实下载器故障用例，摘要错误不重试。主wrapper从9.8.1对齐用户已批准且ARM64资格通过的9.8.2，官方SHA复核，本地291核心/Debug/Release/testAPK/lint218任务全部执行通过。原wrapper直接下载连接未传数据，复用先前已固定校验归档完成构建。无手机/付费调用。

## 2026-10-25 PCAP片段、JNI成功与已批准socket快照

classic PCAP精确选择已接Agent/Root注入批次，普通UID封印输入快照，protocol2逐前缀变长帧计数、全选择拒绝、父死亡终止；9234db2的CI40202879875 packet实验步骤13项已通过，整体构建另追踪。真实ARM64 JNI CI40202399728成功，API36双ABI库16KiB/导出/依赖验证，完整AGP/CMake和手机执行仍待做。

用户确认精确socket快照和复用受管引擎的长时轮转。新增socket_owner_root与原生有界SOCK_DIAG，所有精确匹配UID/cookie/inode、时间/namespace、unknown/multiple保留。291核心279通过/12跳过，完整本地构建/lint通过；Linux内核7项新增实验待CI，Android2项仅编译。浏览器夹具固定10ms清理等待在Windows并行构建暴露竞争，已改实际MessagePort关闭事件并通过。见SOCKET_OWNER_IMPLEMENTATION.md；轮转尚未开放。无手机/付费调用。

## 2026-10-25 完整ARM64编译器候选成功与Android JNI入口

CI40183812668实际成功，6份固定源码构建、原生AArch64 intrinsic C与C++ vector在迁移后的候选中编译执行成功；工具、clang资源头及入口合计282文件228,341,810字节已保留。仍明确不是全部Android补丁等价或手机JNI执行证明。

实际取得官方NDK30 Linux归档738,633,529字节、SHA256753611f4…，仅选择API36/37、aarch64/x86_64目标头/库/compiler-rt与notice，3316项168,450,097字节；不提取/执行官方x86 host程序。Windows对Linux头大小写同名不适合物化，已做只读库存审计，真正提取/编译限定Linux ARM64。新增JNI真实交叉编译/链接与16KiB/依赖/导出审计workflow，引用精确成功编译器run身份和逐文件摘要，结果另查；无QEMU/手机/付费调用。

## 2026-10-25 Root原始帧发送产品接入

新增packet_inject_root，沿用已批准libpcap1.10.7与既有Root任务监督、Binder/账本/产物，不改系统路由。明确接口、14..16384字节帧、包数/字节/间隔/BOOTTIME截止，非阻塞发送，首次错误/短写不重试；只有全部接受且真实正常退出/完整报告/计数一致才完成，pcap接受不等于送达。双ABI Debug/Release/testAPK/lint和ELF16KiB审计本地通过，核心284项272通过/12跳过。

新增7项私有netns/veth真实收帧/节流/预算/截止/取消/MTU拒绝及host-only短写故障实验待CI；普通UID拒绝和显式Root自有lo实验帧2项Android仅编译。PCAP/PCAPNG重放仍在接续。无手机或付费调用。见PACKET_INJECTION_IMPLEMENTATION.md。

## 2026-10-25 单次HTTP产品工具与Android网络权限

已实现http_request Agent工具，复用OkHttp5.5、既有tool_runs/schema8/OwnedTasks和download.bin发布器；显式direct/http/socks，精确文件体SHA、无自动重定向/重试/凭据继承、TLS正常验证、有界解压响应、取消/超时与未知结果提示。特别处理OkHttp503/Retry-After:0内部follow-up，不重放GET并恢复原头。11项真实loopback测试通过（含SOCKS远端DNS、自签名TLS拒绝、失败发布/取消不重发），核心280项268通过/12跳过，完整Debug/Release/testAPK/lint本地通过；新增2项Android只编译。

显式HTTP需要允许cleartext，模型/Skills/图片仍保持原HTTPS检查；Android17声明局域网运行权限并提供用户主动申请与系统设置入口，不在后台自动弹窗。更新能力页过时的“Linux/抓包/逆向尚未接入”说明，仍明确APK/JNI/代理产品工具未全部完成。无手机或付费调用。详见HTTP_REPLAY_IMPLEMENTATION.md。

此前a6aa95a完整CI40182008579成功，证据已保留；新生产变更CI推送后跟踪。

## 2026-10-25 LLVM真实编译缺同基线libunwind头修复

CI40178609738实际编译约66分钟，在3068/3153项Mach-O/ARM64.cpp因缺mach-o/compact_unwind_encoding.h失败；不是成功、不是超时或内存失败。补同一已批准2d287f51…基线的libunwind源码，新增第6份归档168,152字节/固定内容身份，不删除Mach-O能力或改用其他编译器。构建前检查所需头，保留失败日志。

候选打包补真实clang资源头、clang++/ld.lld固定入口及完整toolchain目录；在重定位后的包中真实编译并执行AArch64 intrinsic C与C++ vector夹具，通过后才原子移动候选目录并出具清单。仍不是Android补丁等价、JNI或手机安装资格。独立长时workflow新结果另查。

## 2026-10-25 真实ARM64 AGP Java/Compose APK成功

CI40181701787整体成功：真实JDK25/Gradle9.8.2/AGP9.4.1/Kotlin2.4.21、固定API37/SDK36 Java材料与自建ARM64 aapt2，实际执行43/48个任务，分别生成2,286,626和18,025,198字节unsigned自有APK，验证DEX包含自有Java/Compose类。39个SDK原ELF均先替换拒绝入口，只有3个已验证ARM64资源工具恢复可执行；未执行x86替代。保留前两轮失败与最终成功报告。仍没有JNI、手机运行/安装或完整工具发布资格。

网络固定归档追加只读DT_NEEDED/RPATH/RUNPATH/SONAME盘点及越界字符串回归，明确外层libz和guest glibc系列依赖，不把14个可见ELF误称完整静态链接源码/许可闭包。LLVM独立源码编译CI40178609738仍在运行；无手机或付费调用。

## 2026-10-25 AGP SDK注册元数据修复

CI40181504510已越过旧Kotlin/JVM25错误，在查找android-37.0目标时失败。固定SDK ZIP只有source.properties，缺sdkmanager安装时另行生成的package.xml；现保留原ZIP及API37.0身份不变，复制现有官方SDK安装的两份精确注册元数据（含原许可），只写CI私有SDK，报告记录其摘要。不重装IDE、不访问手机。失败日志保留，完整AGP成功仍待新实验。

## 2026-10-25 API37/AIDL整体通过，AGP显式Kotlin版本修复

资源/JVM CI40180937353整体成功，实际API37资源编译链接、R.java、AIDL Binder Java、16KiB zipalign与坏XML拒绝全部通过。网络CI40181238469新增严格退出码0收尾后也成功。

首轮完整AGP CI40181353310在dex任务依赖阶段失败：夹具只声明AGP，没有主工程顶层Kotlin2.4.21 apply false约束，默认内置Kotlin不支持JVM25。按现有主工程补相同Kotlin版本声明，保持JDK/Java25目标，不降级。官方built-in Kotlin支持更高KGP版本的说明见https://developer.android.com/build/releases/agp-9-0-0-release-notes。失败证据保留，新CI40181504510结果另查；仍无手机或付费调用。

## 2026-10-25 API37资源成功、AIDL夹具失败修复

CI40180474337资源源码构建再次成功，API37 compile/link/R.java/16KiB zipalign/坏XML拒绝均实际通过；AIDL因夹具未传源码import根目录而失败，保留失败日志，不记整体验证通过。现补显式-I目录，新运行另记。网络新增冻结索引审计CI40180852638成功，仍不代表许可闭包或手机接入完成。

## 2026-10-25 冻结模块盘点与完整AGP资格入口

mitmdump固定归档额外盘点1176个冻结Python模块；只解析有界primitive索引并禁止marshal代码对象，不导入或执行冻结代码。索引/代码对象拒绝回归通过。模块含标准库和缺独立元数据的第三方库，不以uv.lock或12份内带notice冒充完整许可闭包。

新增独立真实ARM64 AGP9.4.1 Java＋Kotlin2.4.21/Compose APK实验，复用精确成功workflow身份与资源二进制摘要。官方SDK36归档仅复用Java/元数据，外来ELF替换为显式exit126，只有已验证的原生ARM64 aapt2/aidl/zipalign替换回可执行工具；不安装手机或第二套IDE。固定输入已取得，5项归档测试本地通过；实际AGP结果另记，不提前宣称资格。

## 2026-10-25 网络ARM64实际资格通过与API37资源工程验证

CI40180289970已在真实ubuntu-26.04-arm通过官方mitmdump12.3.0启动、自有HTTP/HTTPS双向修改、严格信任/未信任CA拒绝、WebSocket修改和完成流文件离线回读；证据已保存。仍未完成许可/手机依赖闭包或产品Agent接入，不显示proxyReady。

下一步资源实验新增官方API37.0 revision2固定输入（67,281,901字节、SHA256840b23e8…），实际编译/链接自有资源APK、生成R.java与Binder接口、16KiB zipalign检查，并要求坏XML编译失败。只读使用平台JAR/AIDL，不执行官方x86工具，不安装手机。结果另记；不是完整AGP/Compose/JNI资格。

## 2026-10-25 ARM64资源实际构建通过与网络资格入口

真实ARM64 CI40178993015的资源/JVM两job成功，aapt2/aidl/zipalign已从固定源码编译并执行版本检查；报告已保存。还未完成API37/AGP/Compose/JNI工程资格或手机运行闭包。成功产物现保留bin与固定源码归档供下一阶段使用。c9878b9完整CI40178992261成功；独立LLVM编译实验仍在运行。

新增官方mitmproxy12.3.0归档固定与有界只读PyInstaller审计：98,927,179字节，121内嵌条目、14 ARM64 ELF；仅选择mitmdump。3项拒绝路径测试本地通过。新增真实ARM64自有loopback HTTP/HTTPS/WebSocket修改及流文件离线读取实验，结果另查；不是产品已接入、许可闭包完成或Android已验收。无手机/付费模型操作。

## 2026-10-25 C++23宿主资格与网络后端已确认

资源CI40178607483的JVM成功，资源编译已越过全部缺头阶段，至ResourceTable.cpp遭GCC13/libstdc++缺C++23 P2165R4 tuple/pair转换而失败。官方GCC14起实现该特性；资源实验改用已GA的ubuntu-26.04-arm宿主（与已选Ubuntu26.10 guest兼容方向），保留原C++23源码语义，记录实际宿主包版本；新结果另查。compiler独立job仍按原基线运行，尚未完成。

用户确认NETWORK_ADVANCED_PROPOSAL的libpcap/OkHttp/mitmproxy12.3组合，先来源与资格后产品接入；不自动修改全局路由、安装CA或使用VPN替代抓包。当前仍未安装手机工具，签名密钥仍留集中验收处理。

## 2026-10-25 资源Binder头闭包与原生编译器候选

资源CI40178107011已编到CursorWindow.cpp，缺binder/Parcel.h失败。已将原先只取native/include的范围扩为同一固定commit的完整frameworks/native归档，避免compatibility链接指向未取得的libs/binder；仍15份归档，总59,443,068字节，新内容身份已固定，失败证据保留。

NDK30实际已安装clang --version为Android r574158c/clang21；源码说明指向LLVM基线2d287f51…及Android补丁。已取得该基线llvm/clang/lld/cmake/third-party五份归档，共165,335,622字节、展开1,422,579,593字节，并固定内容身份。新增真实ARM64 host编译器实验，随后运行自有Linux C编译/执行；这是NDK30上游基线候选，不声称已应用全部Android补丁或与官方SDK等价，也不声称已具Android JNI能力。独立长时workflow仅手动触发；当前仍不安装手机工具。

## 2026-10-25 资源兼容目录别名继续修复

CI40177784058的JVM job成功，资源编译已越过TypeHelpers，但RefBase.cpp通过旧core/include/utils目录别名引用CallStack.h失败。已补归档内已解析目录的受限别名，实际目标必须在同一提取根内且已存在；文件链接仍复制成普通文件，外部链接不跟随。回归覆盖内部目录及头文件访问，本地4项检查通过。旧失败证据保留，新实验另查。完整发布审查包已在release-review-145a1df实际通过APK身份/16KiB zipalign/载荷与两份离线重建，仍未签名、未设备验收。

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

## 2026-10-25 Frida APK、Agent 与精确探测证据

ARM64 worker、固定 Java 桥及许可文本已打包；Agent `frida` 提供 status/probe/attach，固定当前 Root 身份、目标 PID/start、脚本 SHA 和桥版本。原生与 Java 资格仅在当前 Root 服务内存中有效，须固定自测成功、独立清理证明与 Root 私有日志实际调用证据同时成立。workerSucceeded 不代表异步用户脚本完成。退出屏障后发布有界会话日志，日志发布失败不丢失清账证明。

修复 GO 后未尝试 attach 的独立退役证明，以及已验证新 boot 对旧资源的退出证明；同 boot 未知状态继续阻断。Debug/Release/testAPK/lint 本地通过，核心测试 263 项，0 失败、0 错误、12 跳过。新增 Android 集成用例仅编译，无手机或付费模型调用。源码容器身份修复及 65 输入离线重链接另见 FRIDA_SOURCE_MATERIALS.md；最终 Root/ART、服务死亡/取消矩阵与正式签名仍待集中验收。见 FRIDA_AGENT_ACCEPTANCE.md。

## 2026-10-25 有界恢复与Frida源码材料

Root活动索引分页(limit<=64)，管理器每次恢复4项并以有界内存游标公平轮转，不从游标推断清理。新增Android分页用例仅编译，Debug/Release/testAPK/lint通过。

最终LLD map、28个精确root_blobs资源与Android工具链note已接构建审计；新增4资源/4源码/原2归档故障用例通过。65份源码材料已实际取得并固定SHA/Git或npm integrity；ZIP重链接得到相同89,143,968字节worker。记录在FRIDA_SOURCE_MATERIALS.md、tools/frida-sources/catalog.json与证据JSON。独立源码workflow后续执行，完整SDK重建和Android运行不在该证明范围。

## 2026-10-24 受管Binder、活动账本与Agent恢复

新增RootManagedDispatch/Recovery/Client/RunManager连接现有执行器、封印FD与schema8。RootManagedResult拒绝错票据/多重证明/任意错误原文；仅传输成功不清账，已确认清理与执行成功分开。Agent提供当前会话status/recover，开始任务和删除前尝试恢复，未知状态仍受数据库屏障保护。

安装APK的guard摘要由stripDebugSymbols后生成，实际APK审计核对两个ABI；不允许调用者指定可执行文件。worker/桥仍缺完整发布输入，不生成可信资产、不开放注入。core255项243通过/12跳过，Debug/Release/testAPK/lint成功；新增6项Android用例仅编译。详情ROOT_MANAGED_BINDER.md，后续CI独立记录。

## 2026-10-24 独立原生guard与恢复主程序

新增开发期独立coordinator/guard主程序，连上意图/创建计划/真实准入/单次GO、owner/worker pidfd观察、独立卸载报告、退出与域回收。持久worker报告在资源收尾前保存，正常成功还必须实际worker退出码为0；缺失/部分报告不从stdout补造。coordinator与guard都为专用subreaper，收尾仅用/proc真实children及P_PIDFD waitid核实自有/接管后代，再按pidfd停止/回收，既有附加目标不成为选择器。合作结束最多15秒，之后保留5秒强制收尾；coordinator对guard也有绝对等待截止，冻结不无限waitpid。

开发CLI支持独立recover，不重放脚本。先退役，复核旧guard/boot及worker PID-start，按完整inode记录/空组计划回收；已有最终记录精确复用。即便元数据拒绝恢复，coordinator仍尽力停止自己接管的后代，不据此去杀未经验证的其他任务组。Root权限不是对其他Root程序的沙箱，Android实际任务cgroup/Frida helper行为仍须资格测试。

新增14项真实Root cgroup/自有模拟worker测试，覆盖正常/到期、两层父进程退出、冻结guard、脱离进程组及主动迁移的自有helper、exec错误/坏报告、重复恢复/迟到启动、完整报告但不退出和无响应；待CI。ARM64完整guard与fixture链接通过，Windows仅跳过。不含真实Frida注入，不是已打包/注册Agent的产品入口；接下来接真实Frida通路及Root Binder/恢复策略。

创建计划1c73a8d完整CI40009832615已成功（9项新cgroup窗口测试通过），见对应证据。继续推进，不停止等待用户确认例行实现。手机仍最后集中验收，无真实模型调用。

## 2026-10-24 cgroup创建计划与空组恢复

独立guard接入时补上mkdir与完整inode记录之间的崩溃窗口。TaskScope拆为reserve/materialize，旧create仍保持原组合行为；受管路径先将任务key、guard身份、boot、parent dev/inode、随机name及变长parent路径带校验和落盘，再mkdir、再持久完整记录，之后才允许fork。普通路径不增加计划文件。

恢复先按原完整记录回收，再核对计划guard已死、boot/父目录身份，仅移除精确计划指向的空组；未完整登记的已占用组只报busy，绝不kill。无mkdir/已删除/完整登记/短tmp记录均有选择性处理，不扫描整个cgroup树。计划最大约8KiB，常规仅写实际路径长度，Root ownership而非校验和提供信任边界。9项真实Root cgroup故障用例待CI，ARM64完整夹具链接通过，Windows仅跳过。

前一7025c34完整CI40008334926已成功，含25项journal/退出持久化；见ci-40008334926-passed.txt。继续独立guard及恢复接入，不停止等待用户“继续”。仍无手机/真实模型操作。

## 2026-10-24 退出事实与资源收尾持久记录

新增managed.worker与managed.stopped：只有专用流验证EOF并匹配READY身份/票据的52字节报告才落盘；123字节收尾记录区分CLEAR/NOT_CREATED/UNKNOWN，明确真实退出码可未知。先持久退役才允许收尾记录，已有矛盾/部分文件拒绝覆盖；无worker报告仍是缺失，不能从资源清理合成脚本卸载。新8项与原17项Root journal合计25项主机用例待CI，NDK30/API36 ARM64夹具链接通过，Windows仅平台跳过。独立guard/恢复正在接入，不是已开放的Agent功能。

用户要求连续推进，不再每个小阶段结束等待“继续”；保持只在新增底层选型确认或必要设备/授权阻碍时联系。c6fce52完整CI40005725398已成功，证据ci-40005725398-passed.txt。手机仍最后集中验收，不调用真实付费模型。

## 2026-10-24 真实cgroup放行10项通过

c6fce52的CI40005725398 managed-supervision独立job成功，9接收/10观察/17 journal/10实际Root cgroup放行均实际通过。放行夹具包含真实fork/入域/GO/worker及helper exec/回收，最终加载位是模拟，不计为Frida注入或Android设备资格。证据ci-40005725398-managed-passed.txt；ARM64夹具完整链接及16KiB证据managed-launch-arm64-build.json。完整workflow仍待核实。

Root独立guard主程序、最终事实持久发布、精确恢复/版本固定、Root Binder和Agent仍未接。保持Frida未打包/未ready，手机仍最后集中验收。本提交仅文档/证据，不重复触发未变构建。

## 2026-10-24 原生cgroup admission与GO放行基础

新增managed_launch_prepare/release：单线程Root guard中fork，父死信号及父PID复核；子进程先关闭自己的journal锁副本，进入本任务新建cgroup，停在私有socket门前。父进程捕获pidfd/startTicks/boot，持久READY后才返回；release持久记录后再核实owner/worker存活并单次GO。拒绝SIGCHLD自动回收/自定义处理器，避免失去未回收子PID所有权。新子进程close_range CLOEXEC，只有控制流明确跨exec，worker bootstrap再恢复CLOEXEC；exec失败由原生launcher报告never-attached事实。

新增10项Root真实cgroup/自有原生worker测试（不注入Frida）：放行前不执行、退役/父死/worker死亡、准入失败、exec失败、重复GO、FD及helper exec隔离、错误参数和自动回收拒绝，待CI；Windows仅平台跳过。完整ARM64夹具链接已通过；不是独立Root guard主程序，也未APK打包。未来调用者仍须核实/固定worker版本与普通exec属性、记录scope、持锁及监督/真实回收，Root Binder/最终事实持久化/Agent仍未接。

上一轮aeebb08完整CI40003765109已成功，含17项Root journal故障用例；证据ci-40003765109-passed.txt。手机验收仍最后集中进行，未调用真实模型。

## 2026-10-24 Root一次性启动与持久退役屏障

新增独立Root journal实现：私有目录内固定文件，Root属主/权限/单硬链接/NOFOLLOW验证，与既有.run.lock约定相同的非阻塞flock。意图、164字节guard/worker PID-startTicks-boot身份、放行记录和退役标记均先写入并fsync文件及目录。启动/身份/放行不可重放，退役可幂等但重试仍需fsync；仅退休标记也阻止之后迟到start。损坏/部分写/错误阶段顺序保留失败状态，不删除或覆盖。

新增17项Linux Root主机测试含真实进程互斥、父进程死亡、子进程关闭继承锁不会释放父锁、短写/文件或目录fsync失败、错误票据/身份、symlink/hardlink/FIFO/权限等，待CI；Windows仅平台跳过。NDK30/API36 ARM64完整链接和16KiB检查通过。当前是独立持久原语，尚未被实际cgroup guard/Binder/Agent调用，不能把身份记录当作worker已启动或注入已清理。

上一轮f53d824完整CI40001467909已成功：9接收+10观察+19真实Frida及Android构建/lint通过，见ci-40001467909-passed.txt。本轮仍不操作手机/真实模型；继续接实际fork/admission/GO与回收。

## 2026-10-24 监督端新增19项主机用例通过

f53d824的CI40001467909独立managed-supervision job已成功：9项报告流接收、10项真实自有子进程/pidfd/截止测试实际通过，后者19.048秒，包含真实15秒取消上限。证据ci-40001467909-managed-supervision-passed.txt；该job无Frida注入或手机执行。完整workflow仍在运行，不能将独立job成功写成整体成功。5d9ebff的40001398070被新提交取代取消，不记通过。

Root guard/阶段日志/退役恢复/Binder/Agent入口仍待接。当前产品不会因这两个监督原语而显示Frida ready；继续按ROOT_MANAGED_TASKS_IMPLEMENTATION.md接入。只更新文档/证据的本提交不重复触发未变构建。

## 2026-10-24 事件驱动的受管观察循环

b7c3882完整CI40000038379成功，9项严格EOF接收主机测试、19项真实Frida和Android构建/lint均通过；见对应证据。

新增独立原生managed_observe：同一个poll等待owner/worker pidfd、最终事实流和绝对单调时限；不新建线程、不定时扫描/proc。到期发duration、owner退出或流损坏发cancel，单次通知后最多给15秒合作清理，并受调用者预先扣除强制回收预算的force_deadline限制。无响应返回超时，交给后续guard强制回收，不把报告EOF当worker退出；报告已完整但worker仍活也不结束等待。

新增10项自有原生子进程主机用例，包括真实pidfd、协作到期/父死取消、无报告/损坏流、报告完整但不退出、无响应强制边界及真实15秒上限，待Linux CI；Windows按平台跳过。NDK30/API36 ARM64独立夹具完整链接且LOAD均16KiB对齐，19184字节，仅夹具不是Frida worker体积。还没有实际cgroup guard/阶段日志/Binder/Agent产品入口，也没有手机执行。

## 2026-10-24 监督端严格退出流接收

51a5658完整CI39997696909已成功，7项控制/解码与19项真实Frida生命周期均实际通过；Android构建/lint成功，仍不是手机运行证据。

新增独立纯POSIX接收器，按事件非阻塞累计52字节，完整帧也必须等EOF才发布事实；额外字节/重复帧/截断/附带FD一律失败，关闭收到的FD，失败状态不可被后续合法字节改回成功。每次最多处理53字节，不随对端大流分配或无限排空。新增9项Linux主机测试覆盖所有分片/截断边界、延迟尾部及控制消息截断，待最新CI；NDK30 ARM64/API36编译检查通过。本机Windows按平台跳过这9项，未当作执行通过。

这里只补监督侧接收原语，尚未接实际cgroup guard/Binder/持久退役/Agent。接收事实与worker/domain退出仍需分别核实。没有操作手机或真实模型。

## 2026-10-24 原生监督控制与最终事实开发通道

新增私有原生父子stream：到期/取消事件、关闭时清理、Frida deinit后52字节独立最终事实，绑定票据摘要和worker PID/startTicks。bootstrap验证Root父身份、恢复CLOEXEC、清除内部环境；不使用stdout头部作证明。无frame/强杀/报告失败仍是未确认。修复attach API已尝试但session缺失被误标detached的边界，保留部分事实。

新增7项控制/帧解析主机用例与8项真实Frida监督用例（合计19），待最新CI；ARM64原型交叉链接/16KiB检查通过89143968字节。未改变App打包，未重复运行未变的Android/JUnit。上轮cc84c22完整CI39993061650成功；15项受管基础/通道Android仍仅编译，没有操作手机/真实模型。

Root Binder、实际原生cgroup guard、Root持久阶段/退役与App恢复尚未接上；worker事实不能单独释放待处理意图。下一步按ROOT_MANAGED_TASKS_IMPLEMENTATION.md继续连接，不宣称本轮已完成Root受管派发。

## 2026-10-24 FD通路完整CI通过与握手前取消补查

dbea45a完整CI39991608445成功：9项真实Unix socket（含对端死亡）及11项真实Frida生命周期/脚本FD通路全部通过，Android Debug/Release R8/testAPK/lint通过。证据ci-39991608445-passed.txt、fd-tests-passed.txt、frida-tests-passed.txt。

随后补上已claim但进入超时域前就已取消的关闭兜底；外层finally释放监听/连接和自有快照。新增第6项Android通道测试，检查监听不可连接、自有副本已关闭、调用者原FD保留；仅编译。Debug/Release再次通过29秒，testAPK/lint26秒及引用断言编译13秒；不重复核心/原生测试，未改其生产源码。累计此受管基础/通道15项Android仅编译，仍留最终手机验收。

下一步接Root Binder描述符所有权、实际worker PID捕获、独立于Linux的任务cgroup监督、持久阶段/退役标记及最终证明小通道；随后连接Agent/UI、ART/spawn、许可证闭包与APK体积资格。FD交付不是执行/清理成功，也没有将Frida显示为ready。

## 2026-10-24 封印脚本FD端到端开发通路

1e5baf1完整CI39989586948成功，8项真实Unix socket测试实际通过。新增Android RootScriptChannel、固定72字节帧核心与原生attach-channel：Root UID/PID/startTicks、nonce/票据摘要、首片rights、封印/内容校验、单次ACK/EOF、取消关闭accept及自有快照。不增加网络库或磁盘脚本副本，仍无Root Binder/任务cgroup产品派发。

Frida运行时统一初始化一次，解析JSON前先准备随附GLib；反初始化由调用者统一负责，保留硬截止到退出。新增3项真实Frida通路用例和对端死亡第9项socket用例待最新CI执行。核心242项（232通过、10平台跳过）与Debug/Release R8/testAPK/lint本地通过；新增5项Android通道用例仅编译。ARM64原型已重新链接/16KiB检查，未打包/未注册Agent，手机仍最后集中验收。见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

## 2026-10-24 原生Frida预算与证明错配补查

将原生attach观察上限从270秒收紧为250秒，和已批准Root总270秒/预留20秒一致；解析夹具补250000接受、250001及270000拒绝。ARM64 NDK重新编译/16KiB外层检查通过，报告frida-managed-budget-build.json；新原生输入边界须由最新Linux CI实际执行。票据证明补App UID、Root PID/startTicks/bootId、脚本版本/字节数错配，核心239项本地再次通过（229通过、10跳过，9秒）。

schema1..7迁移/保留Linux票据租约、封印内存等新增9项Android仅编译，尚未执行手机。受管基础4a5f89e的Frida独立job已通过；完整CI状态另查，不将进行中记为已通过。

## 2026-10-24 Root受管任务账本与内存快照基础

用户确认组合后新增schema8 root_managed_runs、不级联删除的owner屏障、不可变attach票据/严格清理证明；不绑定Linux环境。SpaceStore在删文件前拒绝未确认任务，损坏记录不跳过。修复历史迁移用例版本断言与重复索引创建。RootScriptMemory新增<=1MiB封印memfd、精确摘要与大小、CLOEXEC/取消回收；没有写盘回退。FD握手/Root入口/恢复/Agent仍未接入，不能显示Frida可用。

核心239项（229通过、10跳过），Debug/Release R8/testAPK/lint本地通过；新增9项Android用例仅编译。上一提交16a32b5完整CI39971396204已成功。当前提交推送后核实CI，不操作手机/真实模型。详见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

## 2026-10-23 静态逆向Agent账本接入修复与Frida完整CI

发现真实Agent路径的两个遗漏：ToolRunStore产物白名单未含shared_toolchain/reverse_analyze，调用prepared会在执行前失败；且一次probe三步都登记同一单产物字段，第二步会被拒绝。现允许这两个已注册工具，probe只登记首步主产物，结果另列每个尝试的规范logDirectories；不改数据库结构、不允许覆盖其他工具既有产物。独立Linux run票据仍保护每个原生步骤。

reverse_analyze结果使用现有Linux小投影，shared_toolchain保留小型错误投影；大日志/历史截断不再掩盖非零退出或probe错误。Android映射重开测试扩展两个工具，新增1项长输出错误投影重开用例；五个Root静态逆向资格用例现在先经真实SharedToolchainTool+ToolRunStore.recording探测，分析也经过账本，避免只测底层绕过注册路径。均只编译，设备仍留最终验收。

本地核心235项（225通过、10平台跳过）及Debug/Release R8/testAPK/lint通过，35秒，证据local-static-agent-ledger-passed.txt。Frida805f5cd完整CI39967903594已成功，八项真实主机用例全部通过（含桥控制单次执行）；证据ci-39967903594-passed.txt及frida-tests-passed.txt。此CI尚不包含本段随后发现的App账本修复，后者完整CI推送后核实。

下一步Root持久化/FD新通道方案已比较，见ROOT_MANAGED_TASKS_PROPOSAL.md，待确认；当前schema仍7，没有新增Root执行入口，也未操作手机/真实模型。

## 2026-10-23 Java桥加载与选定脚本控制

CI39966517578的Frida独立job七项实际通过，包括生成桥加载/普通C目标不误报Java；整体workflow另查。原生原型接上一次aurora:run payload消息、严格文件预算与userScriptPosted/桥收到/同步求值返回三种不同事实，新第8项真实单次执行用例待CI。未接APK/Root/Agent、未做ART资格，不能宣称手机Java动态逆向已可用。详见DYNAMIC_REVERSE_IMPLEMENTATION.md。

## 2026-10-23 真实Frida会话与Java桥构建进展

修复共享事件上下文和GLib销毁顺序后，又通过实际崩溃栈定位Frida设备后端半启动关闭竞态；改用启动完成事件屏障，不添加固定延时。CI39965185125的独立frida-session job六项已实际通过，身份错配含5次快速退出子场景；整体workflow状态另查，不能算全CI已通过。

Java桥7.0.13+官方编译器19.1.0+Nodebinding17.24.3固定锁文件/摘要，Windows开发期两次编译成功235893字节；新增第7主机用例验证真实加载时普通C目标不会误报Java能力，待CI执行。完整native post/ART、spawn、Root/Agent包装、持久恢复与许可证仍待完成。源码及生成过程不向手机安装半成品，详见DYNAMIC_REVERSE_IMPLEMENTATION.md。

## 2026-10-23 Frida附加、消息与退出收尾原生实现

已新增原生附加流程：PID/pidfd复核、受限脚本读取、QuickJS加载、有界序号消息、跨线程取消、独立清理截止、unload/detach/manager确认。使用frida_unref与最终Frida上下文排空处理晚到回调。未打包/注册Agent工具；spawn/Java桥/持久恢复仍待接入，不能宣称动态逆向可用。

ARM64 NDK交叉构建/依赖与外层16KiB检查通过。新增3项Linux输入+6项真实Frida主机生命周期测试，Windows跳过，推送后必须核实CI实际运行；不是9项本地通过。上一轮构建CI39960629598已成功。详情与证据见DYNAMIC_REVERSE_IMPLEMENTATION.md，手机留最终集中验收。

## 2026-10-23 动态逆向原生构建资格与APK构建链检索

用户回复“多处对比取最优”，按DYNAMIC_REVERSE_PROPOSAL推进独立Frida Core worker的构建资格。官方17.24.3 devkit字节摘要通过，现有NDK ARM64交叉链接与外层16KiB段/系统依赖检查通过；strip后约89MB、单库ZIP约36MB，仅原型体积，不是完整APK/RSS。新增可复现构建适配器、2项离线归档拒绝测试及CI资格步骤。

worker目前只实现版本/Root本机进程探测原型，未打包/注册Agent工具，没有手机安装半成品或常驻server。attach/spawn、Java桥、消息与取消清理必须接着完成，详见DYNAMIC_REVERSE_IMPLEMENTATION.md。原生构建完整CI推送后核实；不因静态链接通过就显示runtime ready。

清理本轮重复展开devkit及旧strip文件，释放434501141字节，固定源压缩包和当前原型产物保留供后续开发。手机APK构建链的AOSP/Termux/归档AndroidIDE/CodeOnTheGo来源已重新比较，见APK_BUILD_RESEARCH.md；这是待定研究，不是新构建链已批准或实现。手机与真实模型均未操作。

## 2026-10-23 按类静态分析与完整设备资格用例

reverse_analyze新增可选JADX className，使用上游--single-class/--single-class-output及--no-res；输出固定在当前唯一run目录的selected.java，向Agent返回selectedSource计划路径。严格限制参数类型、长度、控制符/路径分隔符/选项前缀，Apktool不接受该选项。未指定时保留原全量行为，不改变JVM/Root架构。按类仍需解析输入和依赖，不宣称已测性能领先。

新增自有732字节DEX及可复现Java/D8生成脚本；Windows主机实际验证全量和按类JADX输出中的标记。新增StaticReverseDeviceTest五项：真实启动+只读挂载、DEX全量/按类、自己APK清单、缺失输入失败、挂载任务取消及持久bundle引用回收；只编译，未执行手机、不联网下载。准备/故障边界见STATIC_REVERSE_ACCEPTANCE.md。

当前核心235项：225通过、10平台跳过；新增1核心选择参数回归已运行。Debug、Release R8、testAPK和lint通过（39秒），证据evidence/0.1.40/local-selected-analysis-passed.txt。完整CI推送后核实。

动态逆向新底层方案见DYNAMIC_REVERSE_PROPOSAL.md，Frida17.24.3+Java桥7.0.13来源已核实；仍待用户确认，不视为已接入。

## 2026-10-23 Linux执行结果完整性与参数校验

修复Linux命令结果投影把缺失timedOut视为未超时的问题。现须有明确scopeRemoved=true、interrupted=false、timedOut=false以及数值型0..255退出码和stopped=true才归为exited；字段缺失或类型不符为outcome_unknown，success=false、exitCode=null。明确超时/中断仍保留对应状态，非零真实退出仍保留原退出码。外层protocol/runId也不接受字符串数值或错误类型冒充。reverse_analyze复用该投影，自动获得相同行为；退出成功仍不证明反编译完整。

command_execute_linux现在先拒绝额外参数与null/字符串/布尔/小数等非法timeoutMs，不再默默换成默认60秒，也不在参数失败时登记产物或派发命令。未提供timeoutMs的正常请求继续使用默认值。对照原生guest_run_finish源码确认真实完成记录总是输出上述明确布尔字段；没有修改Root协议、持久化或任务收尾策略。

新增2项核心回归（多个字段/参数子场景）已运行。核心234项：224通过、10平台跳过；Debug、Release R8、testAPK和lint通过（41秒）。证据evidence/0.1.40/local-strict-linux-proof-passed.txt。手机留最终集中验收，本轮完整CI推送后核实。

## 2026-10-23 多环境逆向资格隔离与过期探测收尾

原先只有一个全局资格槽，其他会话在不同Linux代发起probe会清空已通过的资格。现改为按Root身份、Linux代和bundle隔离的进程内有界资格表，最多32项；新探测只撤销自己的scope，失败不会抹去其他环境资格。淘汰项需要重新探测，不从旧结果推断就绪。

每次probe持有独立尝试标识，完成时必须仍为该scope的当前尝试；被重试、删除或淘汰取代的迟到结果不能重新授权。工具删除前和finally均撤销该bundle资格，清理失败/取消也不保留旧证据；持久任务租约与真实退出证明继续由原Store/SQLite/Root流程控制，资格表不代替删除屏障。删除未成功也会保守撤销资格，需要重试probe。

只在极短无I/O的表操作上使用JVM成员同步，CLI运行与文件操作不持有该锁，没有新增依赖/持久化/常驻工作。依据Kotlin官方Synchronized文档：https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-synchronized/ 。新增3项核心回归覆盖跨代/身份隔离、旧尝试拒绝、移除及重装、独立bundle和容量淘汰。ARM64 CLI仍待最终手机验收。

上一轮3beb4c7完整CI39953323831成功，证据evidence/0.1.40/ci-39953323831-passed.txt。本轮核心232项：222通过、10平台跳过；3项新资格测试实际通过。Debug、Release R8、testAPK和lint通过（42秒），见evidence/0.1.40/local-concurrent-qualification-passed.txt。本轮完整CI推送后另查。

## 2026-10-23 逆向资格与Agent环境版本一致性

修复共享环境更新后的版本错配：shared_toolchain probe此前独立读取全局当前代，而reverse_analyze沿用Agent已固定的代，导致重复探测仍不能满足分析资格。现在probe先取得同一LinuxAgentEnvironment的任务租约，三个CLI及后续分析共用固定代。status展示该任务选择的generation并据此判断runtimeReady；只查看状态不新增租约，未开始执行的任务仍能看到后续选择变化。没有新增运行时、持久化结构或后台进程。

扩展既有Android回归断言：无环境观察为null、观察不固定旧代、已固定任务跨全局更新仍报告原代。设备执行留最终集中验收，不能以testAPK编译替代设备通过。

上一轮556ecf6完整CI39947239121已确认成功，见evidence/0.1.40/ci-39947239121-passed.txt。本轮核心测试、Debug、Release R8、testAPK、lint通过（41秒），见evidence/0.1.40/local-generation-check-passed.txt。

## 2026-10-23 无图形JVM一致性、依赖盘点与清理错误可见性

上轮bedcfd3完整CI39925157055已通过，日志确认3项新增原生工具挂载测试实际成功。不是Android JVM运行证据。

新增staticReverseJava统一启动参数，探测/分析共同使用无图形profile、native-access、显式堆与ActiveProcessorCount；CLI不再依赖显示环境的自动判断。主机实际启动子JVM验证isHeadless、处理器1、最大堆128MiB三项语义；不是仅断言参数字符串，更不是ARM64性能结论。依据OpenJDK27 GraphicsEnvironment源码对java.awt.headless的处理，未加GUI/音频包或新运行时。

新增scripts/audit-static-reverse-elf.py：读取固定摘要的JRE与Ubuntu ARM64归档，用已有NDK llvm-readelf做43个ELF的DT_NEEDED名称盘点，不执行或解压不可信路径。名称缺口仅见libjsound/libsplashscreen/libawt_xawt的ALSA/X11相关库；不自动安装桌面栈。报告只做basename可用性，未证明路径搜索、符号版本、dlopen或Android SELinux可用，实际probe仍必须进行。工具只作按需离线开发审计，不加入每次Agent任务开销。

修复Store清理异常仅被原安装异常suppressed而Agent看不见的问题：正常失败+清理失败明确返回cleanup remains pending；取消仍传播CancellationException，状态通过cleanupPending记录已知清理失败。成功显式cleanup后清除此错误标志；它不是“全目录已经扫描为空”的保证，有界维护仍按64候选。新增POSIX权限失败用例覆盖普通错误/取消及恢复；Windows跳过，此用例需要Linux CI实际执行。

主机源码获取脚本也改为仅在完整校验后发布，网络/摘要/预算失败移除本次创建的partial；不覆盖或删除未知已有partial，也不为它发起网络请求。新增3个离线Python测试，含3种失败子场景，加入CI；测试不访问互联网。

当前核心229项：219通过、10跳过（原9 POSIX+新1权限失败）；本轮新增主机JVM测试已执行。3 Python测试通过。Debug、Release R8、Android测试APK、lint通过，日志outputs/static-reverse-headless-cleanup-qualified.txt。新POSIX用例与新改动完整CI推送后另查。手机、真实模型均未操作。

一手依据：https://raw.githubusercontent.com/openjdk/jdk/jdk-27%2B35/src/java.desktop/share/classes/java/awt/GraphicsEnvironment.java 。报告与证据见evidence/0.1.40。

## 2026-10-23 静态逆向原生挂载、资格与Agent执行

已接入任务专属工具目录到guest /opt的只读绑定，复用现有open_tree/mount_setattr/move_mount与私有挂载域，不改原Linux基础镜像。源与guest目标均固定描述符且不跟随链接；无工具参数的普通Linux任务保持原路径。工具票据仍在派发前绑定持久引用，只有原任务退出证明才释放，删除仍受保护。

Root linuxRunProtocol从2升级3；新App拒绝旧Root执行服务，最终更新后要断开/重连Root，FolkPatch已有授权不重置。prepare按票据bundle选固定共享目录并校验标记，不能由Agent传任意宿主挂载路径。原生--tools-dir只接受绝对路径，旧命令格式仍支持。SQLite schema仍7。

shared_toolchain新增probe：使用当前Root身份、Linux代与bundle执行固定Java/JADX/Apktool --version；协议、runId、环境、bundle、停止/退出状态及版本文本都匹配才缓存该身份的启动资格。App重启/Root身份或环境变化后重新探测；资格不持久伪装通用兼容。runtimeReady现在由真实探测与安装状态决定，失败含步骤与原证明，不靠“文件存在”置true。启动资格不是任意APK分析质量证明。

Agent新增reverse_analyze，JADX java和Apktool manifest/resources/smali，字面argv、无shell/--force，产物在会话Linux项目reverse/UUID，日志复用runs/UUID。Agent须先将输入置于Linux项目；不把Android宿主绝对路径悄悄挂入guest。默认堆768MiB/4线程可调，仅为初始资格配置，不代表总RSS或安卓最优。厂商framework缓存按会话HOME和工具bundle隔离。退出码/超时/部分结果保留，失败不称完整成功，不自动重放。工具定义固定，不随Root连接临时增删来扰动请求前缀。

复用上游JADX启动器的native-access与ZIP额外字段兼容参数，保留Aurora显式堆/线程预算；Windows Java17自有class由JDK25运行JADX实际反编译成功，采用上游参数后JNI警告消失。不是ARM64 JRE27执行证据。系统可信Context filesDir先toRealPath，再检查受管理子路径，修复/data/user/0别名导致合法安装误拒绝。

新增5核心测试（3个参数/失败结果，2个资格证明）与Linux原生主机3项挂载/链接/参数测试。当前227核心：218通过、9 POSIX跳过；Debug、Release R8、Android测试APK、lint本地通过，NDK ARM64编译通过。主机原生3项本机Windows仅语法检查，实际运行待Linux CI；手机未执行，不把CI夹具当实际JVM/Android结论。对应源码包重新生成，未新增依赖/守护进程或付费API调用。

后续：检查本次完整CI，最终手机执行安装/probe/自有APK分析、取消/超时/并发删除/撤权/重启与性能；逆向静态链已实现而未做Android运行验收，Frida/原生分析/手机APK构建链仍属后续范围。

## 2026-10-23 共享工具持久任务引用与删除屏障

复用linux_runs现有ticket JSON，增加可选toolBundle SHA256，与原Linux环境租约在同一事务登记；旧票据默认null，schema仍7，无新表/框架。退出证明必须同时匹配原runId/generation与toolBundle；缺失或不同bundle不释放记录。恢复仍由原Root停止证明驱动，不重放命令。

StaticReverseStore.reserve在同一操作锁内确认已安装版本并提交持久任务；remove持该锁查询真实待处理Linux票据，有引用或查询失败则保留版本。删除检查只读取尚未完成的linux_runs，不解析历史消息。App重建管理对象不能抹掉数据库引用；更新不改旧bundle。LinuxRunManager内部工具调度入口使用此登记顺序，原命令与PTY同样复用结构化Dispatch；连接身份仍从同一Root端点捕获。

这轮只连接持久引用/删除屏障。原生工具只读挂载和Agent reverse_analyze尚未实现，Root prepare明确拒绝非空toolBundle；管理状态runtimeReady仍false，不把登记成功当能执行，不把工作强行回退普通命令。后续需原生只读挂载、Root能力协商、CLI计划与实际ARM64运行资格后才开放分析。

新增3核心测试：bundle序列化/精确证明、模拟持久记录查询跨Store重建的删除保护、查询失败保留。新增1项Android SQLite持久引用/证明测试仅编译，未在手机执行。当前222核心：213通过、9 POSIX跳过；Release R8、Android测试APK及lint通过。来源样本仍显式提供；不能从注入查询夹具称已做进程重启或Android挂载测试。

本轮未操作手机/真实供应商；完整CI推送后另查。日志outputs/static-reverse-leases-qualified.txt。

## 2026-10-23 共享工具管理与失败清理闭环

用户进一步明确：不长期保留无用半成品；Agent要能安装、更新、删除共享工具及管理共享文件。已接入shared_toolchain（status/install/update/remove/cleanup）到Agent工具集。ARM64且已连接Root才下载，固定可信目录逐组件校验与准备，全部完成才原子发布；安装失败/取消finally清理整次暂存，成功也删除归档与暂存。已安装同bundle直接复用，不再重复下载。update选择当前APK编译期可信目录，不承诺动态追随未验证的上游latest。

新增StaticReverseStore，所有管理操作由同一进程Mutex串行。卸载先把选定版本原子退役到带标记的暂存，再删除；清理保留owner标记到最后，进程在清理中断后仍可重试。启动/Agent显式cleanup每次最多64候选，删除已识别旧暂存及最终空UUID目录，不扫描/删除无关内容。未知标记、清理失败仍保留；错误不伪装成功，启动清理失败以cleanupPending提示而不阻断应用启动。已知标记的损坏版本可显式remove后重装。

StaticReversePreparation本身失败也清理自己创建的target；不删调用前已存在的target。既有SkillNetwork复用原OkHttp/HTTPS/取消逻辑，新增受限maxBytes参数供官方73MB JADX包使用；Skills默认64MiB不变，不增加网络库。

共享工作区原有files_edit_batch可创建/修改，本轮新增files_remove用于显式选定单个普通文件：核对观察到的字节数后unlink，不递归、不留回收站。大小检查不是内容版本保证；同UID/Root程序并发更改不构成安全沙箱。共享resources依旧按原三类空间约定由Skills/Linux/工具安装管理器变更，不开放任意原地改写来绕过版本管理。

新增7个Store生命周期测试+2个共享文件删除测试；使用注入的小型准备器验证发布/清理控制流，不能冒充真实ARM64整包安装。真实官方包校验与JADX/Apktool准备仍由上一轮来源测试覆盖；JRE展开/Android Root执行要最终手机验证。当前219核心：210通过、9 POSIX跳过；Debug、Release R8、Android测试APK、lint通过。

工具文件安装管理已接入，但分析执行的只读挂载与持久租约仍未连接，因此runtimeReady明确false；当前没有任务能挂载这些新工具版本。启用实际分析前必须接入原Linux持久任务退出证明与工具租约，不能只用此进程Mutex保护Root任务。完整运行能力未完成，不向模型宣称可执行。手机未操作，未调用付费模型。

## 2026-10-23 已批准静态逆向：来源与准备层

用户确认STATIC_REVERSE_PROPOSAL推荐组合：JADX1.5.6、Apktool3.0.3、Temurin27+35 ARM64 JRE，按需共享Root chroot，复用监督任务。新增编译期可信目录、固定bundle ID、长度/SHA256/取消/物理路径检查；准备层复用RootfsArchive/SkillArchive，ZIP和tar仍遵循各自链接规则，JAR完整复制后再次校验。只写未发布暂存目录，不把准备成功当已安装。

真实发布包重新下载重算摘要，JRE为ELF64 AArch64，304条目/197873172文件字节；JADX实际为lib/jadx-1.5.6-all.jar，已修正原先CLI单jar名称猜测。归档版共149086879压缩字节，不逐会话下载。可复现脚本scripts/verify-static-reverse-sources.py（默认核验本地文件，--download显式下载缺失包），未将大包提交Git。

新增5核心用例，含显式官方样本的扫描/真实JADX和Apktool准备；输入目录及样本文件计入Gradle test inputs，避免未提供样本的缓存掩盖后续验证。当前210核心：201通过、9 POSIX跳过；CI未提供官方逆向样本时该1项另跳过，不能写成来源已验证。Debug Kotlin、lint与Release R8通过。

Windows x86_64 JDK25上两个CLI --version返回正确；自有APK清单夹具解码exit0并产出manifest，但Apktool保留5项应用资源引用警告，不作为完整资源反编译通过。JRE27 ARM64未执行，手机未操作。源码包校验/WindowsCLI成功不能证明Android16 chroot执行。

尚未完成：共享版本发布与回收/租约、Root只读挂载、Agent安装/分析接口、UI状态、实际ARM64 CLI与取消/低内存/会话删除验收。本阶段不对Agent广告工具已安装可运行。

## 2026-10-23 下载展示元数据与产物详情

下载握手单独验证 mime（128字符）与 Content-Disposition（512字符），超长提示整体丢弃；上传 ready 仍只允许 kind。复用已有 AndroidX URLUtilCompat 解析 filename/filename*，再移除路径片段、控制/格式字符及孤立代理，名称最多120个UTF-16单元且不截断代理对；MIME只保留规范化类型，不用于内容验证/自动打开。原始头、Response URL均不写入结果。

displayName/mimeType进入已有工具结果，无数据库迁移、旁路文件或新依赖；磁盘名仍 runs/UUID/download.bin，原中断恢复协议不变。下载详情展示名称、类型、字节与产物入口；只允许规范UUID下载路径，旧结果回退download.bin。下载不再打开不存在的命令输出日志。

新增4项核心测试，生产传输JS增加2个元数据场景共18个（属于一个JUnit）。核心205项：196通过、9 POSIX跳过；Debug、Release R8、Android测试APK与lint通过。新增1项Android文件名扩展参数端到端用例仅编译，最终真机统一执行。未调用付费模型或操作手机。

官方依据：https://developer.android.com/reference/androidx/webkit/URLUtilCompat 。名称/MIME始终是网页提供的不可信提示；跨域未暴露的头不可读取，不重发网络请求猜名称，不承诺所有下载来源可用。

## 2026-10-23 浏览器传输错误码与 MIME 通配符修复

页面传输仅返回白名单错误码：脚本、Response 可读性、源类型、读流、字节预算、上传目标/accept/多选与协议等。原生只接受精确错误字段；未知代码/额外字段统一降为 page_transfer_failed，不把页面异常文本、签名网址或请求内容写入账本。页面代码属于未可信诊断提示，不作为原生事实。工具继续保留方向 phase 与 unknown/outcome，不自动重试；超时新增 transfer_timeout。

修复 Agent 文件输入 accept=*/* 被误拒绝的问题，保留 application/* 与扩展名匹配。新增通用与类型族 MIME 通配符场景；生产 JS 夹具逐一验证失败代码和精确消息形状。

本地核心201项：192通过、9 POSIX跳过；生产传输JS16子场景属于一个JUnit用例。Android Debug Kotlin编译、lint通过；没有操作手机。文件展示名/MIME元数据仍待后续，不更改下载固定磁盘名和已批准恢复协议。

## 2026-10-23 0.1.40 下载中断恢复与登记顺序

改为先SQLite登记目录再创建partial；仅对uncertain且无结果、已登记下载做有界恢复，保留完成文件、同级内容和不明条目，不重放请求，不把uncertain改成成功。schema6→7增加待回收部分索引；主机提取实际SQL验证10000条完成历史不被常态回收扫描，迁移成本及Android计划仍待设备资格。

本地核心200项（191通过/9 POSIX跳过），新5项Android仅编译。0.1.39实现d403a4f完整CI39915958302已通过；本轮0.1.40另查CI。手机仍0.1.32未操作，见BROWSER_DOWNLOAD_RECOVERY.md。

## 2026-10-23 0.1.39 原生浏览器文件选择与上传快照

接入单/多文件原生回调、会话/资源和系统SAF选择；64KiB流式快照、精确FileProvider URI、名称/MIME、数量/总量与只读属性，复用OwnedTasks协调取消与删会话，旧进程快照有界回收。为避免表单导航提前删文件，已交付URI保留到关页/删会话，待选请求导航即取消；UI有说明。

本地核心196项（188通过/8 POSIX跳过），Android新5项只编译。0.1.38实现40d96bc完整CI39913505934已成功；0.1.39另查CI，手机仍0.1.32未操作。下载 .partial崩溃回收、真实手势/Chrome读取URI/SAF/提供器阻塞与性能仍未验收，见BROWSER_CHOOSER_IMPLEMENTATION.md。

## 2026-10-23 0.1.38 浏览器 Agent 二进制传输第一阶段

用户确认组合方向。新增 browser_download/browser_upload：独立 ArrayBuffer 端口、文档/会话校验、64KiB 原生落盘 ACK、预算和临时文件完整发布；Agent 显式供给 conversation/resources 文件，真实 File 仍有整文件成本。复用账本与产物目录，浏览器事件合并显示字节，不新增网络栈或轮询。WebKit 按官方稳定发行更新 1.18.0，不启用隔离放宽。

主机核心191项（184通过/7跳过），新增传输生产JS14场景属于其中一项JUnit；Android新4项只编译。用户原生chooser/快照、崩溃遗留回收、更多错误诊断和真实Cookie/代理/表单/性能仍待继续，不能标为传输全部完成。见 BROWSER_TRANSFER_IMPLEMENTATION.md；手机未操作，仍0.1.32。

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

本地新增4项核心测试：退出与清理语义、参数拒绝、关联先于单次派发；core全套及Debug/Release R8/lint通过。上一轮CI39788741945完整成功。此次未跑真机或真实模型，不把已验证底层等同于新增Agent端到端验收。UI交互Linux终端入口、整轮版本固定、PRoot全生命周期、Root元数据GC仍待做；当前手机仍保留0.1.30已验收版。
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

## 2026-10-26 接续：构建资格、源码对应与轮转契约

完整ARM64 AGP Java/Compose/JNI资格40210689697通过，原生LLVM strip/ranlib资格40206934943通过；496份Rust源码补充许可资格40210692480通过。Python15份源码/所需21份许可、本体与官方20251010运行库逐字节对应；libgcc与Debian12精确包/源码/运行库例外本地校验通过。全部14项内嵌ELF已有精确二进制对应，静态OpenSSL闭包和产品安装接入仍未完成。

新增轮转配置/封口记录契约与独立原生配置/SHA256基础件；300核心288通过/12跳过，原生双ABI对象编译通过，Linux真实执行测试已加入CI待跟踪。实时worker/guard恢复/Root发布/Agent尚未接入，无新增手机操作或可用性宣称。详见NEXT_STEPS与evidence/0.1.40对应记录。

## 2026-10-26 轮转原生与guard资格

CI40224855665通过8组真实libpcap封口/短写/fsync/冲突和5组封印FD/连续loopback/取消/损坏配置拒绝。capture guard首次40225277685因launcher仍只允许attach-channel而拒绝capture，未误报成功；修复后40225641400完整通过，含旧Frida观察/记录/启动及24组真实cgroup guard恢复。新capture AEX2报告无伪造Frida清理位，30分钟仅capture可用；旧Frida250秒/普通命令300秒保持。

306核心294通过/12跳过。新增Root封口段读取、流式PCAP格式/计数/摘要验证、独立副本发布及原始封印配置留存；实际双ABI轮转载荷已入Debug/Release并通过16KiB/依赖/摘要审计，216项构建任务、AndroidTest和lint本地成功。配置留存分支CI与Root手机发布故障矩阵待验收；Binder/活动表迁移/Agent未接，不显示captureReady。详见CAPTURE_ROTATION_IMPLEMENTATION.md。

## 2026-10-26 轮转Binder/Agent与活动收尾整合

同一活动表迁移schema9，旧票据/工具归属逐字保留；实际SQLite DDL保留/外键/回滚3组通过。capture Binder/当前Root服务代固定loopback自检、Agent status/probe/start、准备产物/账本/有界唤醒租约已接。真实自检须封口/发布/退出全部通过才开放start，不能从主机资格自动置ready。

发布后回收Root载荷，重启依Root封口记录严格复核会话副本；失败保留同一活动意图供显式恢复。显式会话删除可在真实退出后丢弃本任务载荷，未知继续阻断。307核心295通过/12跳过、完整218任务Debug/Release/AndroidTest/lint本地通过，新增设备用例仅编译。27f2da9完整CI40226367156与配置留存专项40226368338成功，后续整合CI待跟踪。

## 2026-10-26 完整CI与发布审查包刷新

fd10377完整CI40228422302和轮转最终统计专项40228422825均通过。release-review-fd10377已生成，APK44,861,876字节，workTreeDirty=false；全部checksums独立复核，Frida离线重链接和guard源码重建通过。仍unsigned-review/releaseReady=false，未操作设备/密钥。

代理静态OpenSSL材料补齐：实际cryptography3.5.5；aioquic1.2.0精确固定上游wheel配方→vendor3.3.0-1→源码3.3.0，vendor头实际相符。两份官方源码、许可和3组只读/篡改/重复或链接头拒绝测试本地通过；静态代码全面可复现重建未建立，不扩大材料证明。新增源码审计CI待跟踪，产品共享安装/规则/Agent仍继续。
