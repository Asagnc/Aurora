# Linux 环境实施记录

2026-10-22。用户已批准LINUX_PROPOSAL.md及真机后的cgroup生命周期修订，后者替代必需PID空间。当前0.1.24已接Root安装、固定cgroup监督及持久恢复，完整运行时尚未就绪。

## 0.1.24 持久恢复

Root专属记录、boot/PID启动时间/pidfd/目录inode复核、协调兜底及独立恢复已接Agent recover_root。见LINUX_SCOPE_RECOVERY.md；新增主机7故障，Android正常恢复仅编译，普通门槛73。Android强杀/权限变化、业务chroot/命令/PTY/租约仍待接，executionReady=false。

## 0.1.23 cgroup资格

新增原生scope固定资格及Agent probe_root；真实主机6项CI、Android1普通+1Root仅编译。详见LINUX_SCOPE_IMPLEMENTATION.md。监督自身强杀持久恢复、业务chroot/命令/PTY和租约仍待继续，executionReady=false。

## 0.1.22 Root辅助安装

App解压普通文件，Root一次批量补齐硬链接；固定镜像复核、服务身份票据、取消join、失联暂存保留和显式恢复已接。详见ROOT_LINUX_INSTALL.md。新增核心7项共136；Android新增5普通和1Root镜像安装，仅编译。本轮没有新增真机证据，普通门槛72，executionReady仍false。下方0.1.21未接Root安装为历史状态；当前仍待cgroup原生监督/事件恢复、chroot属主映射及命令/PTY。

## 0.1.21 真机发现（优先于下方历史）

见DEVICE_LINUX_2026-10-22.md：6项普通/Root通过，空间查询改StatFs，普通硬链接限制在下载前失败，Root硬链接保持App属主。没有发布Ubuntu安装，PRoot guest未执行。Root私有挂载和本任务cgroup创建/加入/kill/空状态/删除通过；PID命名空间本机缺失。用户确认LINUX_CGROUP_PROPOSAL.md，生产probe_root仅查挂载，不继续要求PID空间。完整cgroup原生监督/事件/pidfd/恢复、Root安装与chroot/命令/PTY仍待继续。

## 0.1.20 Root 生命周期资格入口

- 用户确认Root Linux使用私有PID+挂载空间，能力不足禁用该后端而不改全局策略。已实现随APK打包的namespace helper：固定探测、父死亡信号/竞态管道、PID1监督、脱离进程组且忽略TERM的测试子进程、外层wait屏障。没有Ubuntu挂载、chroot或任意Root Linux业务执行。见LINUX_ROOT_LIFECYCLE.md。
- Agent linux_environment新增probe_root，要求已有显式Root连接；复用现有Binder/超时/CPU租约/日志产物，不新增Root守护程序。环境安装不是前置条件，结果仍executionReady=false。
- Agent PRoot固定探测现取得真实SQLite环境代租约；选择/取租约与清理共用catalog锁，运行期间允许目录管理继续，取消/失败后在NonCancellable收尾释放。UI无会话探测仍持catalog锁。进程死亡遗留租约不盲清；完整命令/PTY尚未接租约。
- CI新增4项真实Linux Root命名空间专项（正常退出/宿主空间、父TERM+KILL、PID1强杀、普通UID/参数拒绝），核对外部proc中脱离子进程消失。必须区分Linux主机与Android实测。
- 新增普通拒绝1、Root探测/超时1、显式镜像下载/PRoot执行1项Android用例；后者通过独立注解和auroraLinuxImageTest=true选择，默认普通测试不联网安装镜像。普通门槛67；新增设备用例未执行前不记通过。

## 0.1.19 PRoot 原生构建与离线探测

- PRoot5.1.107.98、talloc2.5.0、libandroid-shmem0.7从精确SHA256源码构建，使用现有NDK30/API36，ARM64/x86_64均随APK分发PRoot与独立loader；talloc共享链接。无第三方App、可写目录可执行文件下载、旧API兼容二进制或32位guest支持。
- loader使用当前上游arch.h地址，ARM64工作入口偏移来自实际链接符号；修正Release段回收丢失pokedata入口、NDEBUG移除assert后的非void函数落空及缺少string.h声明。libandroid-shmem使用显式PROOT_TMP_DIR并修正不可写路径无限重试；原始源码保持不变、适配修改可复现。见LINUX_NATIVE_BUILD.md。
- Linux页和Agent linux_environment action=probe可对已安装镜像运行固定离线工作负载：干净环境、shell/子进程、文件写读、硬链接、HOME/tmp绑定与退出码。结果workloadPassed与executionReady分开，后者仍false；不自动执行任意业务命令。探测复用原生命令监督/限时/CPU租约，持有catalog锁防镜像清理，结果返回后清除临时产物且不留失效产物链接。
- APK内保留许可，CI交付精确源码树及独立CMake适配器ZIP。双ABI Debug/Release ELF实际检查16KiB、栈/段权限、loader静态结构和动态依赖；独立ARM64源码重建通过。
- 新增1项Android普通UID --version冒烟测试只编译，ordinary gate66；尚未在Android运行固定工作负载或新版冒烟测试。PRoot构建不是Android ptrace/SELinux/guest运行资格证明。

## 已实现

- 受信任的Ubuntu Base26.10 arm64/amd64目录与精确摘要。官方SHA256SUMS签名使用固定Ubuntu CD Image指纹 `843938DF228D22F7B3742BC0D94AA3F0EFE21092` 核对；应用使用随APK固定的摘要，不在线盲信同源摘要。公开校验公钥在config/ubuntu-cdimage-2012.asc。
- Rootfs专用tar.gz扫描/解压：Commons Compress解析头，扩展头在解析分配前限制64KiB/连续16个/累计8MiB；限制条目50000、压缩512MiB、单文件256MiB、展开2GiB，校验gzip CRC和TAR结束记录。当前公共下载器另有64MiB上限，已选基础包约35MiB在其内，不声称支持任意大小Linux包。
- Linux合法多架构包名冒号、相对/绝对guest符号链接、硬链接均保留。拒绝穿越、设备/稀疏/特殊文件、重复路径、链接下写文件、指向非普通文件的硬链接。普通文件先落盘，链接最后创建；不沿guest绝对链接访问宿主根。
- 同架构64位小端ELF、program header预算和guest动态解释器检查。Ubuntu原始权限保留rwx及sticky、去除setuid/setgid；Android使用Os.chmod。扫描可在Windows验证；实际rootfs解压要求Linux/Android文件系统。
- SQLite v5非破坏迁移，环境版本/当前代/安装恢复记录/活动租约；共享资源版本目录，下载/暂存/校验/原子移动/索引提交，空间预算和取消清理。清理拒绝当前代和有活动租约的代。没有在启动时无条件清空租约来伪造“已清理进程”。
- 原生Linux环境页、取消和状态/版本信息；Agent linux_environment可以status/install/cleanup。安装不触碰会话文件、模型凭据或手机系统挂载。管理页安装属于页面协程，离开取消未完成操作；Agent安装随已有后台任务宿主。

## 尚未完成，继续实施

2026-10-22 执行层准备修正：扫描计划现保留数值 UID/GID（拒绝负数及 Linux chown 的 -1 哨兵），新安装在版本目录原子发布 entries.json，包含路径、类型、尺寸、去特权位后的权限、链接目标与原始属主。不会在普通 UID 解包时尝试 chown，也没有更改正在使用的环境属主。解包重新核对属主、权限和精确 TAR 类型，防止扫描与提取间元数据改变。硬链接仍由原始文件 inode 决定属主/权限，不用链接头覆盖。

旧0.1.18安装缺少 entries.json，当前仍可查看基础环境；后续执行资格必须处理缺失元数据的重新校验/补全，不能默认为 root 属主或直接标记就绪。尚未接入 chroot 属主准备及运行工具。

新增3项核心测试，累计129：属主/去特权位/JSON往返、非法属主计划、POSIX扫描后元数据改变与失败清理。Windows本轮127通过、2项POSIX跳过，固定官方ARM64包SHA与扫描通过；Debug构建与lint通过。POSIX失败清理由Linux CI执行，不是手机验收。

1. PRoot源码锁/构建/loader打包和固定探测入口已接；Android16实际资格与超时/取消/强杀/PTY/并发清理矩阵待验证。
2. Root私有挂载、进入环境、宿主监督/退出屏障；活动租约接命令及PTY执行，进程死亡/挂载恢复。不能把数据库表存在当作活动租约已接入执行。
3. 会话HOME/tmp/run和共享工具缓存映射、受管理的包集合更新/新环境代/复制成本与回收。
4. 任意Linux命令与PTY Agent执行工具；界面观看实际能力与持久运行日志。目前executionReady始终false，probe只执行固定离线夹具，不把下载/ELF/基础夹具通过当成完整运行时可用。
5. 真机Android16普通/Root资格、SELinux、清理和实际性能；完整手机APK构建及逆向/抓包工具包依旧是后续工作。

官方基础包只扫描/校验ELF不执行guest程序。Host POSIX提取不是Root chroot、PRoot或Android16运行验收。chroot不被描述为安全沙箱。

## 验证与已知边界

当前新增核心11项：rootfs规则/损坏/扩展头/真实官方样本/同架构选择9，ELF结构2。Windows有1项POSIX提取跳过；官方ARM64样本的SHA/布局已实际核对，未在Windows伪造Linux符号链接行为。CI新增verify-linux-image.sh，验证固定GPG签名/摘要后，在Linux重跑所有核心用例，并真实提取官方包、检查guest ELF。结果随对应提交记录。

新增LinuxRegistryTest两项Android用例：v4→v5保留消息与Skills；活动租约保护/移除恢复记录。原v3迁移测试预期更新为schema5。只编译，设备尚未执行；ordinary gate65。

RootfsArchive.extract与LinuxEnvironmentManager.install较长流程保留同一事务/取消收尾范围，文件均在300行内。断电fsync目录持久性、外部同UID/Root主动篡改、真实提供器/撤权/强杀矩阵未验收；摘要不等同防Root沙箱。
