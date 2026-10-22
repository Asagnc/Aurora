# cgroup 任务监督资格（0.1.23）

2026-10-22。沿已批准 LINUX_CGROUP_PROPOSAL.md 实施，无新增守护进程、发行版或调度策略。新增双ABI原生 aurora_linux_scope 固定离线资格程序；不接受任意命令、PID或scope路径，不开放业务执行。

## 已实现

从/proc/self/cgroup和实际cgroup2 mountinfo定位当前组，逐段无符号链接打开目录并核对文件系统；只新建带128位随机名的子scope，不接管既有组。以目录FD操作自己创建的cgroup.procs/events/kill；删除核对目录inode。没有CPU、内存、subtree_control或freezer写入。

协调进程创建指向自身的pidfd，再fork本任务监督进程；后者独立会话，位于scope外，设subreaper。工作子进程自入scope，之后才建立私有mount namespace及递归private传播、创建脱离会话且忽略TERM的后代。监督进程通过pidfd/管道阻塞等待协调进程死亡或工作完成。正常完成也清理后代；取消或超时只写本scope的kill，并以cgroup.events的populated=0事件确认无活进程，wait回收后代后才移除scope。资格成功必须实际观察populated=1再到0，而非仅看到文件存在。

协调进程退出或原生命令监督杀进程组时，独立会话的本任务监督进程仍有机会按pidfd事件清理，并在有限探测和收尾时间内自行退出。不是常驻服务。工作进程若尚未入组就失败，仅额外终止本监督进程未回收的直接子进程，PID不能在wait前复用；不按用户输入PID发信号。

Agent linux_environment probe_root已改用此固定完整资格，结果backend=root-task-scope且executionReady=false，任何缺失能力返回失败，不自动回退进程组冒充。原有namespace helper保留作独立mount/可选PID资格。

## 验证与未完成项

新增6项真实Linux主机测试：正常退出与隔离、协调进程TERM/KILL、整组KILL且组外哨兵存活、工作进程KILL无成功回复、重复scope独立删除、普通UID/任意路径参数拒绝。CI需要Root及当前cgroup可写，不通过就修复，不能把跳过视为通过。Android新增普通拒绝1和Root成功资格1；仅编译，普通门槛73。

本版本还没有Android运行证据；Linux主机成功不能覆盖Android SELinux或cgroup策略。监督进程本身SIGKILL/Root撤权、进程或系统整体异常后的持久恢复记录尚未实现，因此不开放chroot/任意Linux任务，不释放业务环境租约来假装清理成功。内核长时间不可中断任务或权限变化可能阻止清理，资格会失败并记录unconfirmedTaskScope，不将其称为已回收。

后续：带boot/process/scope身份的持久恢复、Root撤权/监督强杀闭环，环境属主映射、chroot挂载/执行/PTY及租约，最终Android真机矩阵。PRoot guest也仍须单独实跑。

一手语义依据：[cgroup v2](https://docs.kernel.org/admin-guide/cgroup-v2.html)、[pidfd_open](https://man7.org/linux/man-pages/man2/pidfd_open.2.html)、[subreaper](https://man7.org/linux/man-pages/man2/PR_SET_CHILD_SUBREAPER.2const.html)。新增C源文件均小于300行，普通函数按职责拆分；系统调用失败与取消不能报告成功。
