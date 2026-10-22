# 任务 scope 持久恢复（0.1.24）

2026-10-22。继续已批准 cgroup 生命周期与恢复记录设计，使用现有原生 helper，不增加服务、依赖或全局策略。当前仅固定资格/恢复，不开放任意 Linux/chroot/PTY 业务执行。

## 落盘与身份

Root helper 在固定共享资源目录 linux-task-state 创建 Root 专属0700状态目录、0600记录。路径逐段无符号链接打开，Android调用方先把既有资源目录解析成真实路径。记录固定版本/长度，含 boot ID、监督 PID/starttime、scope 随机名称、父组与scope的设备号/inode和父组路径；FNV64仅检测损坏，不是加密认证，信任来自Root属主/权限。拒绝未知格式、非Root文件、硬链接记录、目录替身及身份不符。

先写临时文件、fsync文件、原子rename、fsync状态目录，之后才fork工作进程。状态目录自身的父目录也fsync。普通完成在组清空/后代回收/目录删除之后，再删除记录并fsync。恢复使用Root专属锁，串行处理最多1024记录；失败保留记录，绝不按名称扫描系统组或盲写cgroup.kill。未发布前崩溃可能留下未入组的空scope和.tmp；当前不按名字盲清它们，不会在没有已发布记录时启动工作负载。

恢复必须同boot且证明监督身份已经退出/被复用：proc starttime配合pidfd及退出事件，proc缺失还须kill(pid,0)返回ESRCH；权限不明则保留。监督仍活着返回忙，不重放。旧boot只退役记录，不对当前同名路径写kill。重新打开记录中的父组，核对cgroup2文件系统与父/scope设备号、inode后才回收；scope已不存在时只退役记录。只有populated=0并删除scope成功才退役记录。

## 两层收尾与执行入口

协调进程也设subreaper；监督被SIGKILL后，协调进程根据记录恢复并回收被收养后代，返回失败而非资格成功。工作进程入组前设置父死亡SIGKILL并核对父身份，关闭fork后尚未入组的竞态；入组后的脱离后代由cgroup管理。协调与监督均被杀时，新helper可独立恢复，外部进程不宣称wait回收非自己的子进程，确认的是内核无活进程与scope删除。

Agent linux_environment新增recover_root，要求已有显式Root连接，使用原有命令日志和CPU租约。probe_root会先恢复既有记录，拒绝活跃/不明确残留；两者均保持executionReady=false。它不会申请Root、改系统调度或恢复业务任务重放。现有Root协议无需变更。

## 验证和接续

新增7项Linux主机故障注入：监督KILL协调回收、两层KILL后新进程恢复、活监督不干扰、损坏记录保留、有效校验但错误inode拒绝、旧boot不写当前组、非Root/不私有/符号链接状态目录拒绝；原6项生命周期继续运行。Android现有Root用例补正常持久记录与recover，仍仅编译，普通门槛73；无新真机证据。

本机Debug/R8 Release/testAPK/lint及双ABI ELF/16KiB/对应源码校验见evidence/0.1.24；Linux真实结果以CI为准。上一版8fe33c4完整CI39732417088已成功。

仍待：Android实际监督强杀/Root权限变化矩阵、chroot属主与挂载/命令/PTY、业务环境代租约与任务恢复联动、普通PRoot guest资格。当前恢复不能替代那些验收；权限撤回或内核不可中断任务导致失败时保留记录，不释放业务资源冒充成功。

一手依据：[cgroup v2](https://docs.kernel.org/admin-guide/cgroup-v2.html)、[父死亡信号与fork继承边界](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html)、[文件与目录fsync](https://man7.org/linux/man-pages/man2/fsync.2.html)。新增C源均低于300行。
