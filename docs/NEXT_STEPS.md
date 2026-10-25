# 当前接续清单（2026-10-25，0.1.40）

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
| 发布审查打包 | package-release.py核对APK身份/SDK/非debuggable、16KiB zipalign、载荷/源码/重建对应、签名模式与checksums；release-review-145a1df实际成功。 | 当前仍unsigned-review，releaseReady=false。 |
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
- LLVM/Clang/LLD原5份源码实验CI40178609738在3068/3153阶段因缺同基线libunwind的Mach-O头失败。现补第6份libunwind固定归档168,152字节和构建前检查，保留失败证据；同时保留clang资源头/完整目录、增加迁移后C/C++实际执行。新实验40183812668成功，228,341,810字节/282项的完整候选与资源头已实际保留，迁移后C/C++夹具运行通过。尚未等同应用全部Android补丁的NDK，未完成Android JNI资格。
- 完整AGP Java/Kotlin Compose实验入口已实现（apk-agp-qualification.yml、qualify-apk-agp.py）；已引用CI40180937353的精确成功资源workflow。官方SDK只复用Java/元数据，39个外来ELF替换为明确失败入口，仅已验证ARM64资源工具可执行。首轮CI40181353310因夹具遗漏显式Kotlin版本而使用AGP默认旧Kotlin，不支持JVM25；已按Aurora主工程锁定2.4.21后CI40181504510已越过该问题，但手工解包缺sdkmanager生成的package.xml导致API37.0未注册；已补现有官方SDK精确元数据后，CI40181701787真实ARM64 Java/Compose两种APK构建整体通过（43/48项任务实际执行），自有类已进入DEX；JNI和手机运行资格仍未完成。
- 后续：追踪失败并修复；完成AGP/Compose/JNI与运行闭包，再接共享安装/版本资格和Agent构建入口；未完成资格不安装手机、不显示buildReady。

## 正在推进：网络进阶

用户已批准NETWORK_ADVANCED_PROPOSAL组合：原始发包沿用libpcap，单次HTTP重放沿用OkHttp，按需TLS/HTTP修改使用mitmproxy12.3.0官方ARM64 standalone，先验证后接入。

- 官方归档98,927,179字节与3个成员已固定摘要，仅选择mitmdump；只读审计121条目、14个ARM64 ELF、1176个冻结Python模块。冻结模块索引禁止代码对象，不导入执行冻结代码。
- 真实ARM64 HTTP/HTTPS请求响应修改、严格信任/未信任CA拒绝、WebSocket修改、完成流离线回读在CI40180289970成功；新增索引审计CI40180852638也成功。
- 仍未完成内嵌Python/原生库/第三方源码许可闭包、手机chroot运行闭包、共享安装/持久租约/Agent规则与产物入口；不显示proxyReady。
- 首版明确regular loopback代理，不自动改全局路由或安装系统CA；只导出公有CA，私钥留所有者私有。规则固定结构化数据，不把任意Python脚本当产品规则接口。
- 单次HTTP构造/重放 `http_request` 已接Agent/账本/会话产物，显式路由、TLS验证、禁止自动重试、取消/超时/解压预算与11项主机回归通过；2项Android已编译未执行，见HTTP_REPLAY_IMPLEMENTATION.md。原始帧packet_inject_root已接Root Binder/受管worker/账本，双ABI构建与4项核心证明回归通过，7项私有veth/短写实验待CI；PCAP片段重放仍待接，见PACKET_INJECTION_IMPLEMENTATION.md。
- UID精确归属、长时分段轮转/空间预算/崩溃恢复仍待核实选型；涉及新增持久格式时先比较确认，不能猜UID或无限常驻。

## 其余尚未完成

- Skills私有认证/签名、更多结构化系统API、浏览器跨站frame/renderer完整矩阵、正式发布图标检查：先核实代码，再做必要增强。
- 固定Release性能工作负载：Agent正确率、TTFT/整轮/工具时间、CPU/RSS/耗电、抓包吞吐与内核丢包。尚未测量，不宣称领先。
- 普通PRoot在用户Android16的完整资格不足，已有硬链接探测失败则executionReady=false；不隐式加入link2symlink/QEMU。

## 最后集中验收

Root/ART/自有spawn、静态逆向/JVM、APK构建、网络代理、抓包、浏览器WebView/SAF、管理器撤权、未知恢复、取消/删除/服务死亡、熄屏/后台/Thanox与长时压力；镜像真实联网/提供器/崩溃边界均需实际设备证据。正式签名在手机密钥可用时进行，验证覆盖安装与数据保留。当前不因看到此清单就请求连接手机。

最新完整代码CI成功基线：a6aa95a的40182008579；后续main实验脚本变更跟踪最新core.yml，取消不记通过。历史精确结果和失败日志保留在PROGRESS/IMPLEMENTATION和evidence，不在本文件重复展开。
