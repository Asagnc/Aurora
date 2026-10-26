# 当前接续清单（2026-10-26，0.1.40）

此文件仅描述当前状态。历史阶段和失败证据见PROGRESS.md、IMPLEMENTATION.md及docs/evidence；不能把其中早期“尚未接入”当成当前缺失。

## 工作约定

- 连续实现、验证并推送main；例行修复直接做。新底层/依赖/持久化选型须检索比较并确认；已批准的同一方案不重复确认。
- 当前不连接、更新或操作手机/模拟器，不重装Android Studio或WSL，不调用真实付费模型。
- 最后集中真机验收；保留Aurora安装和数据，自有辅助包验收后清理。手机现有已验收版本0.1.32；代码0.1.40。用户已在FolkPatch内授权，不等待Magisk式弹窗。
- 长期发布密钥在手机公共Downloads，最终验收时处理；当前保留unsigned审查包，不生成替代发布密钥。
- 成功才发布工具版本，失败/取消清理自己的暂存；未知占用/清理失败保留并报告。运行版本按持久租约与真实退出证明回收。
- 不从编译/主机测试推断Android性能、全部模型缓存最优、任意网站访问或全协议解密。

## 已实现，不重复建设

| 范围 | 当前代码与证据 | 最终手机资格 |
| --- | --- | --- |
| Frida Root Binder/活动账本/Agent | schema8不可变票据、封印memfd/FD交付、独立guard/cgroup/journal、严格退出证明、恢复/删除屏障已接；Agent status/recover/probe/attach/spawn已接。 | Root/ART、真实包spawn、服务死亡/取消等矩阵未执行。 |
| Frida spawn持久恢复 | 已批准protocol2两阶段目标记录；捕获前unknown不猜测/重放；仅精确同boot且无resume意图目标可回收。真实Frida28项、spawn记录5项在CI40158211528通过。 | 自有testOnly fixture APK和设备用例已编译，未安装/执行。 |
| Frida发布材料 | ARM64 worker/固定Java桥/许可进入APK；固定源码材料、离线重链接及guard源码重建已接。 | 最终Root运行和正式签名未验收。 |
| 发布审查打包 | package-release.py核对APK身份/SDK/非debuggable、16KiB zipalign、载荷/源码/重建对应、签名模式与checksums；release-review-fd10377已刷新成功，APK44,861,876字节，工作树clean；所有checksums、离线重链接/重建已复核。 | 当前仍unsigned-review，releaseReady=false。 |
| 浏览器传输/恢复 | 每调用独立WebMessagePort、Agent二进制上传/页面流式下载、原生chooser/精确URI快照、背压/归属/取消/账本恢复均已接。 | 实际WebView/SAF/导航/renderer矩阵待集中回归。 |
| 静态逆向 | 固定JADX/Apktool/JRE、共享版本安装管理、租约/只读挂载、当前Root身份/环境代/bundle资格及Agent分析已接。 | JRE27/真实CLI、自有APK和故障矩阵尚未手机执行。 |
| Root抓包 | 固定libpcap独立worker、有界预算/截止/取消、Root发布PCAP、Agent离线tcpdump已接；无VPN。 | 7项设备用例及性能尚未执行。 |
| 既有基础功能 | Root命令/PTY、Root Linux/Agent、会话文件/图片/日志、Skills版本管理、后台Agent宿主已实现。 | 增量设备/长期压力待集中回归。 |
| 模型目录 | 10家官方公开资料复核至10-25；新增Kimi K3.1 Pro/高速、MiniMax M3.1 Flash与严格effort编码。核心269项257通过/12跳过。 | 真实账号路由/缓存/费用/延迟未测；不调用付费模型。 |

实现与验收入口：ROOT_MANAGED_BINDER.md、ROOT_MANAGED_TASKS_IMPLEMENTATION.md、FRIDA_SPAWN_PROPOSAL.md、FRIDA_AGENT_ACCEPTANCE.md、FRIDA_SOURCE_MATERIALS.md、RELEASE_PACKAGING.md、STATIC_REVERSE_IMPLEMENTATION.md、STATIC_REVERSE_ACCEPTANCE.md、BROWSER_TRANSFER_IMPLEMENTATION.md、BROWSER_CHOOSER_IMPLEMENTATION.md、BROWSER_DOWNLOAD_RECOVERY.md、BROWSER_ASYNC_IMPLEMENTATION.md、ROOT_CAPTURE_IMPLEMENTATION.md、ROOT_CAPTURE_ACCEPTANCE.md、PROVIDER_REVIEW_20261025.md。

## 正在推进：手机APK构建链

用户已批准APK_BUILD_PROPOSAL的现有Root chroot/共享版本/租约路线：ARM64 JDK25＋真实Gradle9.8.2/AGP9.4.1；资格通过再安装。无x86仿真或另一套IDE。

- 真实ARM64 JVM/Gradle自有Java编译与执行已通过CI40177012737，并在后续运行重复通过。
- 固定15份资源源码59,443,068字节，aapt2/aidl/zipalign在ubuntu-26.04-arm实际构建/版本检查通过CI40178993015、40180293564；后者保留bin和源码归档。
- CI40180474337实际通过API37 compile/link/R.java/16KiB对齐和坏XML拒绝；AIDL夹具漏-I import根目录失败，已修复。修正后CI40180937353两job整体通过，包含完整AIDL生成。
- LLVM/Clang/LLD原5份源码实验CI40178609738在3068/3153阶段因缺同基线libunwind的Mach-O头失败。现补第6份libunwind固定归档168,152字节和构建前检查，保留失败证据；同时保留clang资源头/完整目录、增加迁移后C/C++实际执行。新实验40183812668成功，228,341,810字节/282项的完整候选与资源头已实际保留，迁移后C/C++夹具运行通过。尚未等同应用全部Android补丁的NDK。CI40202399728已在真实ARM64主机编译并链接API36双ABI C++ JNI，目标库/导出/16KiB对齐通过；完整AGP/CMake JNI和Android加载仍未验证。
- 完整AGP Java/Kotlin Compose实验入口已实现（apk-agp-qualification.yml、qualify-apk-agp.py）；已引用CI40180937353的精确成功资源workflow。官方SDK只复用Java/元数据，39个外来ELF替换为明确失败入口，仅已验证ARM64资源工具可执行。首轮CI40181353310因夹具遗漏显式Kotlin版本而使用AGP默认旧Kotlin，不支持JVM25；已按Aurora主工程锁定2.4.21后CI40181504510已越过该问题，但手工解包缺sdkmanager生成的package.xml导致API37.0未注册；已补现有官方SDK精确元数据后，CI40181701787真实ARM64 Java/Compose两种APK构建整体通过（43/48项任务实际执行），自有类已进入DEX；JNI和手机运行资格仍未完成。
- 新增固定CMake4.1.2官方ARM64归档与Ninja1.13.2 ARM64，准备NDK官方CMake数据（拒绝外来host ELF）与已资格编译器组合的双ABI真实实验；5项元数据边界测试本地通过，首次CMake实验40204855557配置/编译已通过，链接因目标libatomic.a遗漏失败；已补同固定NDK30的双ABI库后CI40205203214整体通过，真实CMake/Ninja构建双ABI JNI及16KiB/导出/依赖审计成功。AGP/Compose/JNI首轮40205366822因NDK HTTP短体下载失败，校验按预期阻止构建；已将短EOF/IncompleteRead明确归为有界可重试传输失败，完整长度摘要不匹配仍立即失败。主工程wrapper同步已资格的9.8.2，实际218任务全量执行通过。第二轮40205753544已越过下载进入AGP配置，夹具遗漏ndkVersion导致AGP默认28.2与显式NDK30不符，已补与主工程一致的30.0.16248370后继续。第三轮40206074783已真实生成Java/JNI APK，但检测到strip包装未兼容AGP的-o导致退回原库；已改LLVM原生别名并补真实strip/ranlib资格，原生别名/strip-o/ranlib索引已通过40206934943；AGP40206938845两APK均生成，最终检测器只看首DEX导致误判，已按真实class_defs遍历全部DEX并在现有APK验证Owned位于classes2.dex，新增4项拒绝/多DEX回归后重跑。仍不显示buildReady。
- CI40210689697已完整通过真实ARM64 Java/Compose/双ABI JNI构建：验证所有DEX的真实类定义、4/6个JNI库、Compose graphics-path固定AAR逐字节对应、NDK目标运行库和16KiB对齐；CI40206934943通过原生strip -o与ranlib索引。上述早期失败均保留，已修复。
- 便携SDK实验CI40282040597通过：SDK自动发现CMake/NDK，迁移后再构建Java/Compose/双ABI JNI；7个非系统动态库实际从包内加载。原生包180,242,303字节/SHA256 f899eda7aee47fe3edc11546f26d7853aa6f8962f08bb2225f59a327b2327042，不含另行直接下载的官方SDK/JDK/Gradle分发。
- 同一共享安装器已接android-build家族、固定输入/元数据/ARM64校验、外来SDK ELF拒绝入口和readonly SDK布局；保留旧静态逆向bundle身份/租约/清理。Agent shared_toolchain family=android-build install/probe和apk_build已接，只有当前Root身份/环境代/bundle实际自检通过才开放构建。原生包当前使用所有者命名空间内显式选择的精确本地归档，未虚构公开下载URL；不另建安装服务。
- 本地319核心305通过/14跳过，Debug/Release/AndroidTest/lint共219任务成功；产品Kotlin安装器/readonly SDK/实际Java＋Compose＋双ABI JNI/16KiB/真实DEX类定义和源码对应专项CI40287903059已全部通过；同提交完整CI40287902114通过。7项私有ELF与6组Ubuntu源码包/18份119,568,016字节材料及13份许可本地采集校验成功，专项同步审计；不冒充完整NDK或全部目标可复现重建。每次构建仍受现有270秒Linux任务预算和真实退出/清理证明约束，不扩大普通命令时限。新增固定目录工具专用下载覆盖JDK/Gradle超过128MiB的归档，普通Skills原上限保留；拒绝非编译目录输入，大文件真实下载专项继续。手机运行未验证，不显示buildReady。

## 正在推进：网络进阶

用户已批准NETWORK_ADVANCED_PROPOSAL组合：原始发包沿用libpcap，单次HTTP重放沿用OkHttp，按需TLS/HTTP修改使用mitmproxy12.3.0官方ARM64 standalone，先验证后接入。

- 官方归档98,927,179字节与3个成员已固定摘要，仅选择mitmdump；只读审计121条目、14个ARM64 ELF、1176个冻结Python模块。冻结模块索引禁止代码对象，不导入执行冻结代码。
- 真实ARM64 HTTP/HTTPS请求响应修改、严格信任/未信任CA拒绝、WebSocket修改、完成流离线回读在CI40180289970成功；新增索引审计CI40180852638也成功。
- 已取上游精确commit/UV锁和43份源码包69,723,152字节，12个内嵌原生组件逐字节匹配固定ARM64 wheel，许可材料已提取；新增ARM64运行身份/源码材料CI40206942052通过；496个Cargo固定crate共91,852,188字节已校验采集，补充项目许可后CI40210692480已通过（没有缺失的独立许可项）；Python3.14.0已逐字节匹配官方20251010 ARM64归档，15份源码和21份所需许可本地采集通过；libgcc已逐字节匹配Debian12.2.0-14+deb12u1并取得对应源码、Debian修改和运行库例外。14个内嵌原生ELF均已有精确二进制对应；静态OpenSSL已补两份官方源码：cryptography实际观测3.5.5，aioquic精确wheel上游固定配方和vendor头均指向3.3.0；对应源码/许可本地采集与3组只读/篡改边界通过，不冒充全部静态目标可复现重建，Python/GCC源码采集与只读边界已在CI40224295518整体通过。手机chroot运行闭包、共享安装/持久租约/Agent规则与产物入口仍待接；不显示proxyReady。
- 首版明确regular loopback代理，不自动改全局路由或安装系统CA；只导出公有CA，私钥留所有者私有。规则固定结构化数据，不把任意Python脚本当产品规则接口。
- 单次HTTP构造/重放 `http_request` 已接Agent/账本/会话产物，显式路由、TLS验证、禁止自动重试、取消/超时/解压预算与11项主机回归通过；2项Android已编译未执行，见HTTP_REPLAY_IMPLEMENTATION.md。原始帧packet_inject_root已接Root Binder/受管worker/账本，双ABI构建与4项核心证明回归通过，7项私有veth/短写实验已通过CI40185946093；新增classic PCAP精确片段回放，核心参数/选择/多帧证明与完整本地构建通过，扩展13项Linux实验及整体CI40202879875已通过，见PACKET_INJECTION_IMPLEMENTATION.md。
- UID精确socket快照和复用现有受管引擎的长时分段轮转均获用户确认，见NETWORK_UID_ROTATION_PROPOSAL；socket_owner_root已接Agent/账本和有界原生查询，本地291核心及完整构建通过，Linux内核7项已通过CI40204214212专属job，整体CI另跟踪，Android2项仅编译。轮转继续按验证后开放推进，不把快照当历史逐包归属。
- 轮转配置/封口记录已实现独立有界契约，AURC封印memfd解码、固定小端黄金向量、Root身份/票据/顺序/物理inode/总预算验证、原始内核计数未知处理和流式SHA256；300项核心288通过/12跳过。原生配置/摘要双ABI对象编译通过，CI40224294297的capture-primitives已实际执行8组Linux测试通过（完整构建另跟踪）；新增单句柄分段写入/封口存储层：整包预算、Root私有目录、不覆盖/不跟随链接、关闭写句柄、fsync、物理inode/SHA256和原子封口记录；双ABI对象编译通过，CI40224855665实际通过8组封口/短写/fsync/冲突边界和5组封印FD/连续loopback/取消/拒绝损坏配置。新增单句柄实时worker、独立AEX2退出报告，主机资格通过。capture protocol3票据及guard类型/BOOTTIME/持久kind/恢复已通过CI40225641400，含24组真实cgroup guard回归；首次40225277685因launcher仍限Frida而拒绝capture，已修正并保留失败证据。306核心294通过/12跳过。原始封印配置持久封口、Root段读取/PCAP格式计数校验和幂等发布实现已加入；双ABI实际打包、摘要/16KiB/ELF依赖审计、Debug/Release/AndroidTest/lint本地通过。配置留存CI40226368338已通过，完整代码CI40226367156也通过。Root Binder capture调度/当前服务代loopback自检、schema9同表迁移、Agent packet_capture_rotate_root status/probe/start、活动账本/产物归属/删除屏障已接；仅实际服务自检通过后允许start。308核心296通过/12跳过；SQLite迁移保留/外键/回滚3组通过；完整Debug/Release/AndroidTest/lint本地通过。副本持久后回收Root载荷，缺失原载荷时凭Root封口记录严格复核副本；发布失败保留同一活动意图，显式会话删除仍须先确认退出再丢弃载荷，未知阻断删除。新增Android迁移/账本/Root loopback用例仅编译，原生完整专项40227803549通过。Agent/Binder和产物清单新增Root封口的结束原因/原始内核累计计数，缺失/不一致保持未知；新增统计分支专项40228422825与完整CI40228422302均已通过。详见CAPTURE_ROTATION_IMPLEMENTATION.md。

## 其余尚未完成

- Skills私有认证/签名、更多结构化系统API、浏览器跨站frame/renderer完整矩阵、正式发布图标检查：先核实代码，再做必要增强。
- 固定Release性能工作负载：Agent正确率、TTFT/整轮/工具时间、CPU/RSS/耗电、抓包吞吐与内核丢包。尚未测量，不宣称领先。
- 普通PRoot在用户Android16的完整资格不足，已有硬链接探测失败则executionReady=false；不隐式加入link2symlink/QEMU。

## 最后集中验收

Root/ART/自有spawn、静态逆向/JVM、APK构建、网络代理、抓包、浏览器WebView/SAF、管理器撤权、未知恢复、取消/删除/服务死亡、熄屏/后台/Thanox与长时压力；镜像真实联网/提供器/崩溃边界均需实际设备证据。正式签名在手机密钥可用时进行，验证覆盖安装与数据保留。当前不因看到此清单就请求连接手机。

最新完整代码CI成功基线：87149fc的40287902114；后续main实验脚本变更跟踪最新core.yml，取消不记通过。历史精确结果和失败日志保留在PROGRESS/IMPLEMENTATION和evidence，不在本文件重复展开。
