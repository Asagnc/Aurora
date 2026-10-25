> 2026-10-25 更新：worker/桥/notice 已打包，Agent status/probe/attach 与会话日志已实现；旧文中的待接入描述为历史状态。资格要求固定自测的真实调用事件及独立退出证明，最终 Android Root/ART 未执行。当前验收见 FRIDA_AGENT_ACCEPTANCE.md。

# Frida发布源码材料（2026-10-25）

已实际取得65份固定源码归档，合计288,562,897字节，记录157份许可/版权文本（包含构建期和可选源码的材料，不是157项Android运行依赖）。含Core/Gum/releng与实际V8子模块、Java桥npm/仓库、发射到桥中的三项polyfill、NDK29/30运行库基础源码和Android补丁。

先按官方固定Git树盘点，再下载和重算归档摘要；已有Git notice记录复核blob SHA1，npm源归档验证SHA512。GLib、JSON-GLib、libpsl的COPYING实际是归档内链接，解析目标字节但不向宿主文件系统提取链接；缺失/越界/错误摘要拒绝。曾有一次libunwind网络EOF，已重试取得且摘要固定，没有保留.partial。

## 实际链接与嵌入资源

最终LLD map保留1,894个归档成员来源（包括调试段来源）；不再把devkit的2,598个成员全部当作最终运行时闭包。

audit-frida-resources.py从外层实际ELF映射和root_blobs描述表读取28个精确资源，记录大小/SHA/ELF机器/加载段/系统依赖。ARM64 agent39,081,712字节，16KiB加载段、无W+X，只依赖libc/libdl/liblog/libm。agent32和helper32为4KiB的兼容资源；zymbiote内部加载代码、memory-scanner和BPF可重定位对象分别记录，不把它们统一当系统动态加载库，也不声称Android16执行通过。

ARM64 agent、ARM32 agent和helper32的Android note都明确为NDK r29/14206865、API21；外层worker采用现有NDK30/API36。r29对应LLVM基础386af4a5c64ab75eaee2448dc38f2e34a40bfed0及Android补丁1dab3288f660d43a6cb2479107e2b54b3ab0a2a1；r30对应本机clang_source_info.md的2d287f51eff2a5fbf84458a33f7fb2493cf67965及9e20ac0b949feedcfe3da791189310f6ebcbc7a8。取得的是源码材料，没有安装旧NDK。

Java桥的审计源图包含20个源入口，额外的运行时shim实际是@frida/base64-js2.0.3、@frida/ieee7542.0.2和@frida/buffer7.1.0。已从固定Core编译器锁和npm integrity取得对应源码，见frida-bridge-runtime-inputs.json；不能只看桥package.json无运行依赖就漏掉这些内容。

## 离线重链接

源码材料ZIP349,673,283字节，SHA256为873ba35ea851f843b4df8dcf543abc9ab8c68e2994123ab7258e548ccf95367d。含65份源码归档、官方devkit、21份Aurora源码/头文件与构建适配。

Windows/现有NDK30实际从ZIP临时目录离线重链接通过，得到89,143,968字节worker，SHA256仍为a1486cd58c2d25ca2caa014075fb5eb9ac85623c5a135f03ba87c4b28ab49fb1。运行前检查源码、适配器与所有输入归档的当前可信摘要；没有执行下载脚本或归档内未知构建指令。临时展开/重链接产物自动回收。

这是Aurora worker的可复现重链接证据，未从源码重建官方完整SDK，也不是Android执行证据。独立workflow frida-materials.yml将复核Linux获取/重链接与上传材料，执行结果另记。

当前仍待：真实 Root/ART 与服务死亡/取消集中手机验收、正式签名发布。worker/桥/notice 已进入 APK，Agent 已提供显式探测与 attach；源码材料完成不会授予当前 Root 服务的运行资格。

## 后续接入APK与Gitiles容器修复

独立源码CI40150310929因13份Gitiles归档的原始容器摘要变化而失败，未记通过。重新取得Markupsafe归档从10,306变为10,300字节，但文件路径/Git模式/Git blob集合的内容摘要完全相同。现已将这13项固定为该内容身份，保留原始容器SHA为观察值；GitHub/npm仍固定原始归档摘要。新增容器时间戳变化可接受、正文变化必须拒绝的回归，源码故障用例累计5项。没有忽略摘要错误或把新的任意源当成旧源。

worker现由Gradle复用独立资格构建器生成，再复制固定SHA的同一89,143,968字节ARM64产物。APK strip后的摘要必须仍一致，Root从APK资产取得worker摘要。Java桥同时携带固定构建输入/包锁摘要和235,970字节产物，Root拒绝其他bridge描述。x86_64没有注入worker替代品。

APK已携带65份源码材料关联的132份去重许可文本（1,051,122字节）及160条notice/companion来源记录。材料ZIP另补桥源入口/包锁/构建说明和标准许可文本；仍不把SDK完整重建或Android执行写成通过。

本地最终 Debug APK85,751,374字节、Release unsigned APK44,475,066字节。Release内worker ZIP压缩条目36,054,208字节、桥64,748字节、notice275,069字节；这是实际APK条目和总文件大小，不是安装/RSS或设备性能测量。见local-frida-apk-payloads.json。Debug/Release/testAPK/lint通过，新增ARM64版本/桥快照用例仅编译。Agent 注入动作已接入，最终 Root/ART 和正式发布签名仍待完成；APK 携带不等于 nativeProbePassed/javaProbePassed。

依据：[固定releng依赖](https://github.com/frida/releng/blob/8429c0c9caf8b071955f64869a1b0ede58ad9f34/deps.toml)、[固定V8子模块声明](https://github.com/frida/v8/blob/f6d7f6610f2f21e2817ed180b72ea750a3f63ce8/.gitmodules)、[NDK r29官方变更记录](https://github.com/android/ndk/wiki/Changelog-r29)、[固定Android编译器来源](https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+/568b941cf0c249b9c2a1f853e94a29f0e6291c59/clang-r563880c/clang_source_info.md)。

## 18c1474 远端源码复核

独立源码 CI40155072277 已成功：完整65项输入在 Linux 重新获取、校验、打包与离线重链接通过。完整 CI40155067590 中真实 Frida 与原生监督 job 已成功，Android job 因桥输入 CRLF/LF 摘要差异失败，已固定 LF 输入重跑；桥产物摘要不变。

最终本地材料 ZIP349,797,430字节，SHA256 为1dde76320dd2c32476dacf8c250fe16ad713a088470b6822134217605f922915；对应更新后的桥构建说明及固定输入。离线重链接 worker 摘要仍为a1486cd…，详见frida-agent-offline-relink.json。不同平台重新压缩源码包的 ZIP 容器摘要可以不同，包内固定源码身份及重链接产物独立验证。

Linux NDK30 重链接 worker 为89,143,808字节、SHA256 402a241e45348d80ae8447354629d245dfef9f623feb8004a7d805815aab4d1e；Windows 同主机重链接为89,143,968字节、a1486cd… 。两者分别完成同主机离线复核，不声称跨宿主二进制完全一致；每份 APK 从自身最终载荷生成并校验可信摘要。
