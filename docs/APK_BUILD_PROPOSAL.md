# 手机 APK 构建链：推荐实施路线（已确认）

2026-10-25：用户确认“采用推荐路线，资格通过再安装”。目标包含 Java/Kotlin、Compose、C/C++/JNI、签名与显式安装，轻量 Java→DEX→APK 流水线不能独自满足这些要求。

| 路线 | 覆盖与成本 | 选择 |
| --- | --- | --- |
| 手写轻量流水线 | 小项目可能启动更快，但需重新实现资源合并、插件、Compose、JNI与变体语义 | 不作为主构建引擎 |
| 将手机 IDE 的完整 bootstrap 作为依赖 | 有现成移植经验，但引入另一套运行环境/路径约定；CoGo terminal-packages 最新 GitHub release 仍是2025.08.11，ARM64 assets约1.05GB，不能当作2026当前工具链 | 不直接安装整套 IDE/bootstrap |
| 已有 Root chroot 内运行真实 Gradle/AGP，固定共享 ARM64 JDK 与自建原生 host 工具 | 复用已有监督、只读工具挂载、租约与会话空间；需解决官方 SDK/NDK host 工具没有 Linux ARM64 发布的问题 | 推荐，先主机资格，后集中设备资格 |

推荐组合：Temurin25.0.5+8 Linux AArch64 JDK（不是现有 JRE）、Gradle9.8.2、AGP9.4.1、Kotlin2.4.21，平台37/Build Tools36及NDK30对应 API/sysroot。JDK25已符合 Gradle运行要求，且与 Aurora 现有 Java25 构建一致；不为追求主版本数字替换成未经本项目验证的组合。

实际查询到 JDK 归档140,178,170字节，SHA256 bb5b2ded252f21c6090cf780b0a408ad0d83bbb557e702955643557f13231880。已下载校验，并在真实ARM64 CI40177012737及后续运行实际完成JVM/Gradle自有Java编译与执行；这还不是Android完整构建或手机运行闭包，不显示buildReady。

资源工具优先以 Termux android-build-tools 固定源码 c4edf8539a34a8600538e6642c1ecb170452a79e 为可审查移植候选，在 Linux ARM64/glibc 构建验证 aapt2/aidl/zipalign；不直接运行依赖 Termux 固定前缀的包。其16.0.0.4配方关联 Android16源码，不等于 SDK Build Tools36 的逐组件等价，平台37资源和AGP daemon调用均须单独验证。若不能满足兼容/最新稳定要求，呈现实际失败与 AOSP替代方案后再调整，不静默换库。

C/C++ 必须使用真正的 ARM64 host clang/lld/CMake/Ninja 与固定 Android sysroot/compiler-rt，不运行桌面 x86 NDK 程序或引入 QEMU。先核实 NDK30 LLVM固定源码重建 ARM64 host 工具的闭包和耗时，资格前不声称支持JNI。官方 r30 Linux包明确为x86，单纯下载它不能解决本项。

布局沿用共享不可变工具版本、只读guest挂载、现有持久租约和退出屏障；新增build工具种类与显式 build/probe/status。可写Gradle缓存按工具/仓库版本管理，按所有者隔离不可信项目缓存和init脚本，禁止跨会话复用可执行项目产物。会话保存工程、构建日志和APK产物；不每会话复制SDK，不常驻Gradle daemon，默认受管 --no-daemon。

第一阶段仅来源/闭包/ARM64 ELF/许可与固定构建实验，成功才发布共享工具版本，失败清理自身暂存。第二阶段真实最小 Java、Kotlin/Compose、JNI 工程及错误/取消/磁盘预算/会话删除测试；第三阶段集中手机执行并核验产物签名和安装。构建成功不自动安装；用户release密钥与测试debug密钥分开，不将私钥写入仓库或日志。缓存收益与性能以实际测量为准。

依据：

- [Gradle兼容表](https://docs.gradle.org/current/userguide/compatibility.html)：JDK25从Gradle9.1起可运行；当前文档9.8.2。
- [AGP9.4官方兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)：API37、Gradle最低9.6、默认Build Tools36。
- [官方NDK下载](https://developer.android.com/ndk/downloads)：r30 Linux发布为x86。
- [固定资源工具CMake](https://github.com/termux/android-build-tools/blob/c4edf8539a34a8600538e6642c1ecb170452a79e/CMakeLists.txt)、[当前Termux配方](https://github.com/termux/termux-packages/blob/master/packages/aapt/build.sh)。
- [CoGo固定Gradle设计](https://github.com/appdevforall/CodeOnTheGo/blob/97abdc35381256d6f38bba46650c2f987075187b/docs/adr/0002-on-device-builds-via-gradle-tooling-api.md)与[编辑器工具区别](https://github.com/appdevforall/CodeOnTheGo/blob/97abdc35381256d6f38bba46650c2f987075187b/docs/adr/0003-vendored-forked-desktop-toolchain.md)：文档状态为 Proposed，作为源代码研究参考，不视为其设备验收证明。
- [Temurin25固定发布](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5%2B8)。
