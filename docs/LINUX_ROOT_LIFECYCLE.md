# Root Linux 生命周期（已确认，2026-10-22）

**真机后的修订优先**：当前Android内核没有pid命名空间入口，用户已确认
改用任务专属cgroup v2+私有挂载空间。以下PID方案是此前实现/验证记录，
不再是Root Linux硬前置。见LINUX_CGROUP_PROPOSAL.md及DEVICE_LINUX_2026-10-22.md。
probe_root现只检查私有挂载空间，完整cgroup生命周期仍待接入。

用户已确认增加私有PID命名空间，配合已批准的私有挂载空间。
设备不支持则禁用该Root Linux后端，保留普通/Root Android原生命令。
这不是另装内核、常驻Root守护程序或修改全局SELinux策略。

## 选择与边界

仅用进程组无法回收主动setsid创建新会话的子进程；子收割器可接管孤儿，
但不独立提供完整挂载/进程收尾屏障。Root Linux采用私有PID空间，
由任务级PID1监督主命令。PID1退出时，内核终止并回收其域内进程；
正常退出时外层监督进程等待PID1退出后才结束本次探测。

每次任务先unshare挂载与PID空间，再将挂载传播递归设为private，
防止挂载传播回宿主。父进程死亡通过PDEATHSIG=SIGKILL传递到PID1；
专用生命期管道补上fork到设置死亡信号之间的竞态。PID1不能用
getppid==0判断父进程存活，因为其父进程原本就在外层PID空间。
业务退出不切换后端、不自动重放。

命名空间只用于生命周期管理；Root代码仍具备系统级能力，不将chroot
或该设计描述为抵御任意Root程序逃逸的安全沙箱。

## 当前实现

0.1.20仅实现固定Root命名空间探测，不挂Ubuntu、不chroot、不执行任意
业务指令。原生helper随APK打包；显式Root连接后的Agent可调用
linux_environment action=probe_root。使用现有Root Binder、超时、CPU
租约、stdout/stderr与持久产物入口，不新增逐行Binder传输。

探测核对挂载空间inode变化、PID1/PID空间变化，启动setsid且忽略SIGTERM
的子进程，在主工作进程退出后结束PID1；外层waitpid构成内核收尾屏障。
JSON只报告观察到的命名空间探测结果，executionReady仍false。

Linux主机脚本另从外层proc核对脱离进程确实消失，并测试父进程TERM/KILL、
直接KILL PID1、普通UID拒绝、错误参数及宿主挂载空间保持不变。
主机成功不能替代Android Root/SELinux实测。Android新增普通拒绝和Root
探测/超时两项，仅编译；设备测试不支持时须记录为不可用，不伪造通过。

后续：实际只读rootfs/会话挂载、chroot入口、DNS、属主准备、命令/PTY
环境代租约、父进程死亡与挂载释放的Android验证、强杀后的恢复记录。
父进程被强杀时本身不能继续wait；当前主机专项检查子进程随后消失，
不把它等同于业务取消路径已具备完整同步等待屏障。接任意命令前还需
实现优雅停止与外层监督/恢复检查，不能据本探测提前释放业务挂载租约。
当前Agent PRoot固定探测已取得/释放SQLite版本租约，UI探测仍持catalog锁；
其他命令/PTY尚未接租约，崩溃后不盲目删除遗留租约。

一手依据：[PID命名空间退出语义](https://man7.org/linux/man-pages/man7/pid_namespaces.7.html)、
[挂载传播](https://man7.org/linux/man-pages/man7/mount_namespaces.7.html)、
[父进程死亡信号](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)。
