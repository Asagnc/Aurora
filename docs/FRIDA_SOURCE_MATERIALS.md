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

当前仍待：最终APK携带的notice/源码对应入口、worker/桥打包及Agent注入动作、真实Root/ART与服务死亡/取消集中手机验收。现有Agent仅提供受管意图status/recover，不因源码材料完成就显示注入ready。

依据：[固定releng依赖](https://github.com/frida/releng/blob/8429c0c9caf8b071955f64869a1b0ede58ad9f34/deps.toml)、[固定V8子模块声明](https://github.com/frida/v8/blob/f6d7f6610f2f21e2817ed180b72ea750a3f63ce8/.gitmodules)、[NDK r29官方变更记录](https://github.com/android/ndk/wiki/Changelog-r29)、[固定Android编译器来源](https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+/568b941cf0c249b9c2a1f853e94a29f0e6291c59/clang-r563880c/clang_source_info.md)。
