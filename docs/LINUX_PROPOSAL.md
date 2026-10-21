# Linux 执行底座方案（已确认）

研究日期：2026-10-22。用户已确认“采用推荐方案”。分阶段实现与实际验证见 LINUX_IMPLEMENTATION.md；未完成项不因方案确认而记作已实现。

## 推荐选择

采用 **Ubuntu Base 26.10，同架构执行；Root授权且能力探测通过时优先私有挂载命名空间中的chroot，普通UID按需使用Termux PRoot 5.1.107.98**。Android原生命令保持独立，不为普通文件或网络操作启动Linux。

Ubuntu26.10是本次查到的最新Ubuntu正式版本；官方Base提供arm64/amd64 tar.gz与SHA256SUMS及签名，arm64下载约35MiB。这是基础包压缩体积，不是安装后大小，也不包含Java、编译器、Python及逆向工具包。考虑用户明确偏好最新稳定版，建议默认26.10；长期支持优先者可选26.04.1 LTS，但本轮不同时安装多套发行版。

Root优先策略已获用户确认；不在运行中切换后端。未Root的PRoot只在实际Android16执行探测通过后标为可用，失败明确显示缺失能力。

## 实际比较

| 选择 | 适配考虑 | 本轮结论 |
| --- | --- | --- |
| Ubuntu Base26.10 | glibc用户态、官方ARM64基础包、当前正式发行；后续需按非LTS节奏迁移环境 | 推荐默认，环境版本固定，更新发布新代 |
| Ubuntu Base26.04.1 LTS | 同为受支持正式版，长期维护周期更长 | 可作为后续可选档，不把LTS称为过时/不安全 |
| Debian13.7 trixie | 官方stable元数据确认13.7、ARM64；维护稳健 | 合理备选；本轮集中验证一个发行版 |
| Alpine3.24.2 | 最新稳定分支，musl/BusyBox、小基础环境 | 小体积优点明显；glibc预编译工具需要额外适配，不作为全能工具默认 |
| Arch滚动版 | 可快速获得新包，但缺少固定发行快照的默认维护体验 | 暂不作为默认环境 |
| Termux Bionic工具 | 适合直接面向Android的原生程序 | tcpdump/系统操作等仍优先Android原生；不把Linux底座用于替代所有原生工具 |
| PRoot | 路径/身份仿真，适合普通UID；ptrace追踪有实际成本 | 保留普通后端，按需启动、实测，不称为零损耗 |
| Root chroot | 不需要PRoot路径跟踪；依赖真实Root权限、挂载和SELinux能力 | Root优先候选；必须有私有挂载命名空间，失败不进行全局挂载 |
| bxroot0.2.4 | DSHA已采用，进程内路径改写值得关注；支持边界与指令/ABI适配需专门矩阵 | 不凭上游限定测试取代Aurora实测；本轮不新增第三个后端 |
| proot-distro最新实现 | 已有OCI镜像/层/锁/安装恢复，依赖Python且面向其运行布局 | 学习生命周期与锁；本轮直接使用官方基础tar，不引入OCI注册表/整套Python管理器 |

PRoot与chroot继续使用手机现有内核，不安装新内核，不依赖Shizuku或第三方Termux App。ARM64手机使用ARM64环境，x86_64测试机使用同架构环境；本轮不接入QEMU跨架构模拟。

## Eta/DSHA学习与取舍

本次重新查询远程HEAD：Eta `9adcda01fb4b2e3440f1ade5ffdc91dd36e8471d`；DSHA `891405e006da2316ff593cb4a74485de6c1d7b19`。本地DSHA检出含后续本地提交，比较关键配置另从远程固定提交核对。

Eta的PRoot构造、绑定目录和环境清理、安装暂存/摘要值得借鉴；当前随包PRoot5.1.107.92。Aurora拟采用Termux当前包配方的5.1.107.98，先验证再启用。硬链接能力要实际探测，不无条件启用link2symlink；Git/包管理器/并发发布都要验证。

DSHA固定bxroot0.2.4源码及原生闭包，保留PRoot与资格探测；学习固定来源、明确失败与宿主监督进程退出屏障。其项目验证与QEMU回执不能替代Aurora的Android16/Root结论。当前选择Root chroot优先，是本产品Root用户定位与减少路径翻译成本的工程判断，不是已经测得优于DSHA的结论。

## 三类空间与版本

1. **共享读**：版本化基础环境和已发布工具环境。多个会话共用完整字节，不每会话复制完整rootfs。安装器在暂存区生成新环境并发布，普通任务不原地修改共享包。
2. **会话空间**：HOME、临时文件、运行状态、项目与默认产物；挂入固定guest路径。删除会话等待所属命令/PTY/挂载释放，再删除自身数据。
3. **共享写**：按工具/平台/版本区分的包下载和编译缓存、用户显式保留项目；会话删除不清空这些内容。

版本策略：新任务选择当前已安装环境；执行中的任务与PTY固定环境代。Linux环境的活动租约阻止清理；历史记录保留版本元数据，不让永久历史引用无限阻止大型环境回收。再次使用旧会话时追加当前环境变化说明，不重写旧历史。

新环境安装/更新只由环境安装流程改变包集合。需要复制时仅在环境更新阶段复制；先探测reflink，有效才使用，不支持时记录真实复制和临时空间成本。禁止用可写硬链接副本冒充版本隔离。新旧环境并存期间的峰值空间必须提前计算，空间不足直接失败，不能假称“更新零额外空间”。

Root下先以只读bind和会话可写目录实现基本布局；OverlayFS仅在实际能力探测、所有权/清理测试通过后作可选优化，不作为最低依赖。PRoot路径绑定和普通UID文件模式只提供协作边界，不是防止同UID任意代码修改的安全沙箱。

## 实施顺序与新增组件

1. 先实现独立Linux环境元数据、安装日志/回收/活动租约，复用现有SQLite WAL与任务宿主。表结构与迁移归此方案审批，具体schema随实现记录。
2. 复用Commons Compress的tar/gzip读取，但使用独立rootfs规则：Linux需要合法符号链接/硬链接，不能直接套用Skills的“全部拒绝链接”规则。解压过程不跟随已生成链接写入；普通文件先校验并落盘，再处理有界链接，处理目录/文件冲突、重复、扩展头、大小/条目/空间预算和取消。
3. 官方源/签名校验在发布准备阶段完成，应用使用随APK锁定的可信来源清单与SHA256校验下载。仅从同一不可信连接拿摘要不算真实性验证；后续动态签名目录是另一项设计，不在本轮默默增加。
4. PRoot及loader随签名APK的nativeLibraryDir分发，16KiB对齐。当前核到配套talloc2.5.0、libandroid-shmem0.7，保留来源锁与许可；用现有NDK/CMake/构建工具集成，不下载可写路径中的PRoot直接exec。
5. Root launcher通过已批准的RootService/Binder执行；先unshare挂载命名空间并设置私有传播，再绑定/进入环境，回收由宿主监督。缺少CAP_SYS_ADMIN、SELinux拒绝或系统限制时返回真实能力失败；不改全局SELinux、不装Root模块、不全局挂载补救。
6. 复用现有命令日志、PTY、前台服务、取消与有界CPU租约；没有独立常驻Linux守护进程。命令退出不自动重放；后端/环境版本写入工具结果，UI展示实际环境与任务。
7. Agent可查询/安装环境、执行命令/打开PTY，用户可查看安装阶段、环境体积、版本、运行日志与产物。不会把未安装Python/JDK显示为可用。

Android16仍为最低版本，compile/target继续现有最新稳定SDK。Android已限制从可写应用目录直接execve；PRoot loader与实际guest执行必须实测，不能以降低targetSdk处理。Windows/Linux编译成功不等于普通Android UID可运行。

## 与APK/逆向工具的边界

本方案只批准通用Linux环境底座。后续JDK/Gradle/AAPT2/NDK、JADX/Apktool/Frida/radare2等分别按当前稳定版本和ARM64实际产物核查。官方Linux NDK host是x86_64，不能因为rootfs为ARM64就声称其官方Linux包可以在手机运行；不默默加入x86模拟来“完成编译”。

Root抓包仍走Android原生后端，不能用Linux环境是否可用来限制它。不会把chroot称为完整Docker、系统安全沙箱或自动解密TLS。

## 可验收标准

- 基础包来源与摘要、ARM64/amd64识别、ELF解释器、链接/路径处理、损坏/空间不足/取消/发布恢复都有真实失败用例。
- 先做不依赖联网包安装的shell/子进程/文件/硬链接/退出码/PTY与两会话隔离。再做真实包安装、更新期间旧任务继续、失败回滚和缓存保留。
- Root私有挂载不传播到宿主；超时/取消/会话删除/应用进程死亡后，核对宿主进程树、挂载和文件句柄。需要设备/Root才能证明的项不以主机模拟冒充通过。
- 真机最终分别比较Android原生、PRoot、chroot的启动、文件密集工作、真实编译小项目、RSS/CPU与清理耗时，固定相同输入和实际工具版本，记录失败和不支持项。
- 首个阶段交付环境基础和执行；手机完整APK构建、逆向与抓包工具链仍各有后续验收，不能把环境装好就写成全功能完成。

## 一手资料

- [Ubuntu26.10正式目录](https://releases.ubuntu.com/26.10/)、[官方Base arm64与摘要/签名](https://cdimage.ubuntu.com/ubuntu-base/releases/26.10/release/)、[26.04.1 LTS Base](https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/)。
- [Debian stable Release元数据13.7](https://deb.debian.org/debian/dists/stable/Release)、[Alpine稳定分支3.24.2](https://alpinelinux.org/releases/)、[Alpine musl说明](https://alpinelinux.org/about/)。
- [Termux PRoot当前包配方](https://github.com/termux/termux-packages/blob/9aedff9c28af3ca93c42eb2c3074221893232ae9/packages/proot/build.sh)、[PRoot5.1.107.98源码](https://github.com/termux/proot/tree/e2d843ee6dab076ea0ed1ffd5d0ea12232185499)。
- [talloc正式源码目录](https://www.samba.org/ftp/talloc/)、[libandroid-shmem0.7正式发布](https://github.com/termux/libandroid-shmem/releases/tag/v0.7)。
- [PRoot-Distro实现及限制](https://github.com/termux/proot-distro)、[bxroot0.2.4](https://github.com/qiannianhuanxiang/bxroot/releases/tag/v0.2.4)、[DSHA固定bxroot清单](https://github.com/DSH-APP/DSHA/blob/891405e006da2316ff593cb4a74485de6c1d7b19/tools/bxroot/lock.json)、[Eta PRoot构造](https://github.com/Mangi-11/Eta/blob/9adcda01fb4b2e3440f1ade5ffdc91dd36e8471d/app/src/main/kotlin/io/github/mangi/eta/agent/terminal/ProotCommandBuilder.kt)。
- [Android可写目录执行限制](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)、[挂载命名空间](https://man7.org/linux/man-pages/man7/mount_namespaces.7.html)、[OverlayFS要求](https://docs.kernel.org/filesystems/overlayfs.html)、[NDK主机工具链](https://developer.android.com/ndk/guides/other_build_systems)。
