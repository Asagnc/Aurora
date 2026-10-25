> 2026-10-25：最新比较与具体推荐路线见 APK_BUILD_PROPOSAL.md。仍未安装或宣称可手机编译。

# 手机APK构建链：当前检索与待定项（2026-10-23）

这是研究记录，不是已批准选型，也不是已支持手机编译APK。不得因为JRE、Apktool或Linux存在就显示buildReady。

## 当前一手证据

- Android官方AAPT2说明资源compile/link与Java/DEX/签名是不同步骤，产物不是单靠aapt2即可安装的完整APK。Google Maven文档按windows/linux/osx列出产物，这个OS名称不足以证明其中ELF适用于ARM64手机，必须实际核实架构/动态装载器及运行闭包。https://developer.android.com/tools/aapt2
- AOSP当前aapt2 Android.bp（检索blob f43cf521edf5dae56fda410c9193fb463418b0a1）包含libandroidfw、protobuf等依赖并以静态库组合工具；不能把一个孤立cpp用NDK编译当完整同版构建链。https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/tools/aapt2/Android.bp
- AndroidIDE官方组织与项目已归档，不能使用其旧“latest”资产当当前稳定构建链。官方网页指向Code on the Go作为后续项目。https://androidide.com/ 与 https://github.com/AndroidIDEOfficial/AndroidIDE
- CodeOnTheGo官方最新非预发布26.43，2026-10-20发布，tag commit 97abdc35381256d6f38bba46650c2f987075187b。其目标是手机离线构建；还需读取实际工具生成/版本/补丁与许可证，不能把IDE版本号当aapt2/AGP版本。https://github.com/appdevforall/CodeOnTheGo
- Termux官方aapt构建脚本当前指向android-build-tools16.0.0.4，revision4，依赖fmt/libc++/expat/png/zopfli/zlib等；源码tag对应c4edf8539a34a8600538e6642c1ecb170452a79e。源配方使用git标签及SKIP_CHECKSUM；Aurora若参考应自行固定commit/摘要/补丁，不直接继承动态分支或Termux固定前缀。版本号与SDK Build Tools不是同一编号，不能只比较数字大小。https://github.com/termux/termux-packages/blob/master/packages/aapt/build.sh 与 https://github.com/termux/android-build-tools

## 仍须比较后确认

1. 完整Gradle/AGP共享ARM64 JDK与受控构建缓存，还是轻量Java/Kotlin+aapt2+D8/R8+签名流水线，或两者组合。前者覆盖生态更广，后者小型工程启动链可更短，均须用真实项目测量而不是主观宣称最快。
2. AAPT2/zipalign/aidl等使用AOSP可复现ARM64构建还是成熟移植配方；Android bionic与Root chroot glibc不能混为相同ELF环境，AGP调用路径也须资格。
3. JDK与AGP/Gradle/Kotlin兼容矩阵：当前逆向JRE27不能javac；不能把“最新JDK”直接代入不支持它的AGP。需要最新稳定的兼容组合与任务版本固定。
4. SDK平台许可、固定来源、共享只读工具/可写缓存/会话工程隔离、离线缓存失效与空间预算；禁止每会话复制完整SDK。
5. 签名私钥保管、debug与用户release签名的区分、可核验签名产物；安装普通PackageInstaller与Root安装是显式动作，不因build成功自动替换手机应用。

后续给出具体组件/来源/闭包/体积/任务与缓存方案再确认，不能在这份研究笔记阶段安装任意半成品SDK或承诺任意Gradle工程可编译。当前不下载额外IDE、不重装Android Studio/WSL/模拟器。
