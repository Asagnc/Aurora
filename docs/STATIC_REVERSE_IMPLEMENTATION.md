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
