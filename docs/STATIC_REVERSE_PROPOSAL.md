状态：用户已确认采用推荐组合；实现与证据分阶段记录于STATIC_REVERSE_IMPLEMENTATION.md。

# 静态逆向工具包方案（用户已确认，2026-10-23）

这是新增运行时/工具安装布局的选择，不代表工具已安装。浏览器传输的已批准工作不受此方案等待影响。动态 Frida 与 APK 重建/原生构建链保持各自完整范围，不用静态反编译冒充全部逆向功能。

## 推荐组合

- JADX 1.5.6 CLI 做 DEX→Java/资源分析；Apktool 3.0.3 做 manifest/资源/smali 解码；复用共享 Root chroot 执行与任务监督。
- 官方 Temurin 27+35 ARM64 Linux HotSpot JRE 作为按需、共享、固定版本运行时；这是调用 CLI 的客体运行时，与 Aurora 电脑 Gradle 构建 JDK25 无关。先做兼容资格，不因为版本新就自动视为兼容。
- 首批仅静态分析，不额外启动 Frida 服务、代理或常驻 JVM。JVM 每任务启动；启动成本与驻留缓存的收益待测后再讨论，不预先加守护进程。

| 方案 | 收益 | 成本/取舍 |
| --- | --- | --- |
| 将 JADX API 集成 Android ART/应用进程 | 少一个 guest JVM；能按类查阅 | Java桌面依赖、ART兼容/R8、取消与OOM隔离需要独立移植资格；本轮不选 |
| 独立 Android ART worker | 可隔离部分应用内存，普通UID可能可用 | 仍需移植依赖与Dex分发，不能直接加载JAR冒充可运行；留作后续有证据的优化 |
| Root chroot + 官方 ARM64 JRE/CLI | 复用已实现任务回收与成熟上游CLI；不改反编译算法 | 按需下载约148MB压缩包总量，展开另计；启动/内存需测试；推荐首批 |
| 逐会话安装 / 可写全局 apt install | 操作简单 | 重复空间/原地变更版本影响其他任务；不选 |
| PRoot / x86模拟兜底 | 扩大部分设备覆盖 | 本机PRoot资格失败，不自动模拟或降级；明确不可用 |

## 安装与三类空间

共享只读工具版本目录保存 JRE 与 JADX/Apktool 闭包及许可、固定 manifest（组件版本/URL/SHA256/条目/大小）。安装在暂存目录校验并发布；Root工具目录原始属主经既有批量通道处理。工具目录只读绑定到 guest 固定路径，不原地修改已发布工具或现有 Linux 基础镜像。

需要新增明确工具安装目录/发布与租约元数据，因此此方案先确认。实现时复用既有原子安装、文件/Root批量通道和活动租约规则，不复制整套rootfs、不添加数据库框架。发布前检查实际下载+展开+并存临时空间，初始预算不足不下载；摘要必须固定在签名APK携带的目录，不从同次下载的任意摘要文件自动建立信任。

会话保存输入、HOME/临时文件与反编译产物；共享写目录只保存显式可共享缓存。Apktool framework 缓存按工具版本与会话隔离，不能把一个会话的厂商 framework 静默应用到其他会话。任务固定工具版本+Linux代；清理不能回收活跃租约，未知运行状态按既有规则保留。更新推荐当前稳定版本，只影响后续任务。

## Agent 接口与结果

拟提供 toolchain_status/install 与 reverse_analyze（tool=jadx/apktool，mode=java/manifest/resources/smali），由结构化参数选择已登记输入与独立输出目录；不覆盖现有输出，不自动启用 --force，不把路径拼成shell命令。底层经现有Linux命令/文件/账本/任务取消通道。

先提供有界摘要、失败阶段、exit code、工具与运行时版本、产物路径、stdout/stderr入口；大段源码只在Agent按需批量读取时进入上下文。JADX官方说明不能保证所有代码100%反编译，错误/部分产物必须保留并明确，不以产物存在判完整成功。

JVM堆与线程显式有界、可配置；初始保守值只是资格参数，手机压力测试后再定默认，不能叫安卓最优。复用截止、取消、CPU锁、cgroup回收和会话删除屏障；低内存/超时不无限重试，不让模型误以为成功。

Apktool rebuild 仍需资格通过的ARM64 aapt2；官方JAR存在不等于打包器可在手机运行。JDK javac/D8/R8/签名与从源码构建APK留后续构建链，保留待办。

## 固定来源与检索证据

2026-10-23通过官方 GitHub releases/latest API重新核实（均非pre-release）：

| 组件 | 固定产物 | 压缩字节 | SHA256 |
| --- | --- | ---: | --- |
| Temurin27+35 | OpenJDK27U-jre_aarch64_linux_hotspot_27_35.tar.gz | 60962125 | a41b54098373f1ca8f75ee344db19b93ac39eca6c529c3ee9dc50f4b3e7857de |
| JADX1.5.6 | jadx-1.5.6.zip | 72646741 | 545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974 |
| Apktool3.0.3 | apktool_3.0.3.jar | 15478013 | dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423 |

发布API摘要是准备阶段固定来源的一部分；实现仍需下载重算并验证闭包/许可，不声称此文已做字节资格。官方JADX包包含GUI闭包，首轮先核实清单；可以剔除确定无CLI依赖的GUI部分，但必须锁定经过测试的实际闭包，不能随意删JAR。

- https://github.com/skylot/jadx/releases/tag/v1.5.6
- https://github.com/skylot/jadx/wiki/Use-jadx-as-a-library
- https://github.com/iBotPeaches/Apktool/releases/tag/v3.0.3
- https://apktool.org/docs/cli-parameters/ （--jobs、--frame-path、--aapt等）
- https://github.com/adoptium/temurin27-binaries/releases/tag/jdk-27%2B35
- https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission （不把下载的桌面ELF直接在普通UID私有目录exec）

动态阶段另核实 Frida17.24.3（2026-10-19）、radare2 6.2.4（2026-10-05），不能沿用早期Frida17.23.3。Frida客户端/服务端匹配、任务归属和Android16附加资格是独立新增选择，本方案不隐式批准常驻server。

## 实施与验收门槛

1. 主机锁/摘要/损坏/取消/空间/链接/条目/版本租约与发布恢复用例；不得先跳到“安装成功”。
2. 同架构Linux主机 CLI --version + 小型自有APK/DEX解码与部分失败夹具，禁止把Windows成功当ARM64成功。
3. 最终手机上实际运行 ARM64 java -version、JADX和Apktool，测试并发、取消/超时/低内存、会话删除与恢复、产物Root属主访问。
4. 测时、CPU/RSS、产物大小与任务质量；之后再决定批量/选择类反编译等优化。全部通过前能力状态显示未资格，不广告“逆向一切”。
