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

验证日志：outputs/static-reverse-official-qualified.txt、static-reverse-foundation-release.txt；来源/CLI证据见evidence/0.1.40/static-reverse-*。
