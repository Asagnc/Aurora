# chroot 挂载资格（0.1.25）

2026-10-22。用户已确认LINUX_OWNERSHIP_PROPOSAL.md：Root原始Linux属主代、普通App UID代按需各自共享；Root默认只准备Root代。本阶段先完成不改既有镜像的原生挂载/退出资格，属主代发布与业务租约仍需后续接入，未因审批而记作已实现。

## 固定原生检查

沿已批准私有mount+cgroup监督/恢复工作，在工作进程入组并unshare/private后运行可选--chroot-fixture。输入为专用测试树和工作目录；只执行编译进helper的固定C检查，不接受guest命令或exec镜像ELF，不接Agent动作、不改用户已装环境。scope状态记录仍在fork前完成，失败退出不能报告成功。

使用open_tree克隆固定FD指向的挂载，mount_setattr在未挂接时设置属性，move_mount以空路径FD挂到已固定目标。源/目标逐段无符号链接；不以字符串拼接mount选项，不先全局挂载再补remount，不修改原source挂载。不支持这些系统调用或SELinux拒绝则失败，不做全局挂载回退。

底座为只读/nosuid/nodev；独立home/tmp/project可写绑定到/root、/tmp、/mnt；/proc只读/nosuid/nodev/noexec，/dev递归只读/nosuid（字符设备IO仍单独检查）。设备/PTY业务语义仍须Android矩阵，当前仅检查/dev/null。根以FD进入，chroot后chdir('/')并关闭host目录FD。

固定检查：根目录正确、已知fixture标记、向底座创建文件确实返回EROFS、HOME/项目/临时文件写读、临时硬链接、proc读取、字符设备写。随后创建脱离会话且忽略TERM的后代；原监督流程kill/空事件/wait后释放任务。外层不持有工作进程的mount namespace或树FD；随所有任务进程消失，私有namespace随之销毁，不将全局lazy unmount当作确认收尾。

这是Root生命周期/布局边界，不是阻挡任意Root程序逃逸的安全沙箱，也不保证外部Root程序不会主动持有该命名空间引用。

## 证据与未完成

新增5项Linux主机实际测试：只读底座/可写绑定/源属主及宿主挂载不变；监督KILL后清理；两层KILL后独立恢复保留产物；guest挂载目标符号链接拒绝；两私有视图共享一个底座而可写HOME隔离。旧13项生命周期/恢复继续。CI结果以对应run为准；本地只有Android交叉构建与打包校验，不冒充手机运行。Android普通门槛仍73，无新增设备运行证据。

待接：按已确认方案发布Root/PRoot后端代、Root清理/空间展示，生产元数据与活动租约，真实Ubuntu ELF/解释器执行、任意命令/PTY、会话删除与强杀/撤权完整矩阵。现有Agent executionReady仍false；未更新现有镜像属主。上一版5ff4b8a完整CI39734462398成功。

一手依据：[open_tree固定FD克隆](https://man7.org/linux/man-pages/man2/open_tree.2.html)、[mount_setattr属性](https://man7.org/linux/man-pages/man2/mount_setattr.2.html)、[move_mount固定FD目标](https://man7.org/linux/man-pages/man2/move_mount.2.html)、[chroot工作目录/FD边界](https://man7.org/linux/man-pages/man2/chroot.2.html)。新增文件低于300行；原guard/main生命周期事务按既有职责保持整体审查。
