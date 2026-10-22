# 后端环境代发布（0.1.26）

2026-10-22。实施用户已确认的LINUX_OWNERSHIP_PROPOSAL.md，不新增发行版、全局策略或常驻进程。Root环境发布/清理已接，真实guest执行和命令/PTY的运行租约仍未接；executionReady=false。

## 版本和界面

旧manifest缺少backend时按PRoot/App属主解释，普通代继续用原镜像SHA作为ID；Root代ID取SHA256(固定ownership-v1域+镜像SHA)，原始字节及属主策略共同固定环境身份。新字段随现有JSON manifest存储，不改SQLite v5表结构，不原地chown旧普通代或更改活动代。发布检查ID/manifest一致，Root与普通代按现有SQLite当前代/活动租约独立保护。

安装默认在已有显式Root连接且新Linux协议可用时准备Root代，普通模式准备PRoot代；旧Root服务须断开重连，不自动降级或申请Root。Agent install可指定backend=auto/root/proot；UI按需另备普通环境、分列后端/文件内容大小，并提示已有代保留和增加空间。不会默认装两套或逐会话复制。安装继续用StatFs预算暂存空间；显示逻辑文件字节，不冒充实际分配块大小。

当前Root代不能交给普通PRoot probe，需显式准备普通代；管理页禁用该错误组合。Root环境准备完成仍不显示完整执行可用。

## Root属主准备

App解压普通文件/目录并保留Android文件标签，Root一次批量补硬链接；链接完成后App写元数据，再发第二个固定镜像ownership请求。Root复核官方固定摘要/TAR计划，在所有路径/类型/尺寸/符号链接和硬链接身份预检通过后才修改。额外核对预期链接数，拒绝意外的外部inode别名；不把硬链接头的属主覆盖到原始文件inode。

恢复原始UID/GID、去特权位后的rwx/sticky；symlink只lchown不chmod，不沿guest链接操作宿主文件。普通文件和symlink先处理，目录自底向上，根锚点固定0:0/0755最后处理；缺省父目录也恢复。每次修改后核对属主/模式，Root端再次检查ELF布局。中途失败不声称回滚；待Root确认停止后清理整个未发布暂存。所有权调整只在准备阶段发生，不加入每次模型请求。

## 发布、删除和恢复

linuxPrepareProtocol提升为2；继续复用已有prepareLinux/Binder异步会话与取消join，不新增AIDL交易方法。票据protocol2明确root_ownership或root_removal，记录操作/镜像/App UID/Root进程身份；旧hardlinks票据保持protocol1兼容。

Root ownership即使完成并join，也保留票据，因为暂存内已经有Root文件；只有移入版本目录并完成索引发布，或由Root删除暂存后才能撤销。写票据/元数据、rename父目录和清理状态使用目录fsync；Root清理保留票据/manifest到特权内容移除并同步之后，避免无记录的Root暂存。版本发布沿原事务记录/原子移动/索引提交，失败保留或清理未发布代；SQLite与文件系统不是一个原子事务，不声称断电后所有元数据绝不丢失。

删除也通过Root任务票据与取消确认，不能发送无跟踪的Root删除后立即重建同ID目录。启动/安装/清理先处理暂存票据，确认旧工作停止或原Root进程已退出，再恢复版本操作。未确认票据存在时保留版本恢复记录；预检发现Root缺失时不移除Root代索引，中途失联保留恢复记录，不盲清Root文件。部分删除保留manifest便于重试；若最后只剩空版本目录，可普通删除。未知/损坏且非空的无manifest目录保留，不猜测。

删除前仍由catalog锁和SQLite当前代/lease检查保护；Root服务只接受私有版本目录和一致的Root代manifest，拒绝可见挂载和路径替身。Root可任意改系统的程序不是这个生命周期协议的安全沙箱。未来真实Root命令/PTY必须把scope记录、环境代租约和强杀恢复对应起来，当前没有假装已经接好这一步。

## 验证与接续

核心新增7项共143，Windows136通过/7项POSIX跳过；包括后端分代/旧manifest、票据、目录/硬链接/链接语义、预检零修改、部分失败与取消。新增Android普通5项仅编译（ownership/删除ack、旧协议拒绝、Root缺失清理、后端独立租约），普通门槛78。Root镜像专项改为验证原始属主/硬链接、独立ID、旧普通代保留；本轮只编译，无新设备运行证据。

本地Debug/R8Release/testAPK/lint、ELF/16KiB/对应源码校验见evidence/0.1.26；真实Linux CI重跑POSIX及旧18项生命周期。上一版4411fe7完整CI39736472325已成功。Root真实属主/清理和目录fsync行为仍需Android实际验证；不能用策略回调测试冒充Root系统调用通过。

下一步接真实guest ELF、Root命令/PTY、会话可写目录的Root文件管理与删除、持久scope—版本租约对应和进程死亡恢复，然后做底座闭环真机测试。PRoot guest仍待实跑，不把--version等同于guest可用。

源码目标300行：新增属主预检、Root适配、ownership/removal批次、版本清理和目录同步分别成文件。install事务较长，按既有例外整体审查阶段顺序、锁与失败收尾，不按行数机械拆散事务。

一手依据：[lchown不沿symlink和chown的权限位影响](https://man7.org/linux/man-pages/man2/chown.2.html)、[rename语义](https://man7.org/linux/man-pages/man2/rename.2.html)、[目录fsync](https://man7.org/linux/man-pages/man2/fsync.2.html)。
