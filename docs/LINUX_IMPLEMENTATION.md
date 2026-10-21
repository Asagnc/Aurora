# Linux 环境实施记录

2026-10-22。用户已批准LINUX_PROPOSAL.md，当前0.1.18是基础环境安装阶段。

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

1. PRoot5.1.107.98/talloc2.5.0/libandroid-shmem0.7源码锁、当前NDK构建、loader随APK打包与Android16资格探测。
2. Root私有挂载、进入环境、宿主监督/退出屏障；活动租约接命令及PTY执行，进程死亡/挂载恢复。不能把数据库表存在当作活动租约已接入执行。
3. 会话HOME/tmp/run和共享工具缓存映射、受管理的包集合更新/新环境代/复制成本与回收。
4. Linux命令与PTY Agent执行工具；界面观看实际能力与运行日志。目前executionReady始终false，不把下载或ELF检查当成可以执行。
5. 真机Android16普通/Root资格、SELinux、清理和实际性能；完整手机APK构建及逆向/抓包工具包依旧是后续工作。

官方基础包只扫描/校验ELF不执行guest程序。Host POSIX提取不是Root chroot、PRoot或Android16运行验收。chroot不被描述为安全沙箱。

## 验证与已知边界

当前新增核心11项：rootfs规则/损坏/扩展头/真实官方样本/同架构选择9，ELF结构2。Windows有1项POSIX提取跳过；官方ARM64样本的SHA/布局已实际核对，未在Windows伪造Linux符号链接行为。CI新增verify-linux-image.sh，验证固定GPG签名/摘要后，在Linux重跑所有核心用例，并真实提取官方包、检查guest ELF。结果随对应提交记录。

新增LinuxRegistryTest两项Android用例：v4→v5保留消息与Skills；活动租约保护/移除恢复记录。原v3迁移测试预期更新为schema5。只编译，设备尚未执行；ordinary gate65。

RootfsArchive.extract与LinuxEnvironmentManager.install较长流程保留同一事务/取消收尾范围，文件均在300行内。断电fsync目录持久性、外部同UID/Root主动篡改、真实提供器/撤权/强杀矩阵未验收；摘要不等同防Root沙箱。
