# Android 真机后的 Root 生命周期修订（已确认）

2026-10-22。用户已确认采用推荐修订方案。真我GT6 SE Android16、6.1.141内核实测：Root连接UID0有效，
但/proc/self/ns没有pid入口；固定探测在namespace_identity返回ENOENT。
不安装新内核，不用手机型号分支，不关闭SELinux。

初期读取系统cgroup v2布局，确认存在cgroup.kill及cgroup.events。
用户批准后已在Root会话当前组下新建唯一测试scope，验证仅本测试子进程
加入、kill、空状态及删除成功。没有写既有system/uid/pid组的kill文件。
这仍只是基本操作资格，不是完整原生事件监督实现；见DEVICE_LINUX_2026-10-22.md。

## 推荐修订

将Root Linux必须依赖PID空间改为能力选择：使用任务专属cgroup v2 scope
负责整棵任务进程树的回收，保留私有挂载命名空间。PID空间可在支持的
设备作为附加能力，不再作为所有Root设备的硬前置。

scope只包含本次新建的任务子进程，绝不迁移Aurora应用、Root服务或其他
系统进程；子进程进入scope完成握手后才运行guest。Root宿主监督在scope
外，使用child-subreaper/wait回收退出状态；取消和超时写本scope的
cgroup.kill，再由cgroup.events的populated=0事件确认已无活进程，
之后才释放挂载/环境代。使用pidfd固定监督进程身份，避免PID复用误判。

不启用/修改CPU、内存、调度或全局freezer策略；不新增常驻守护进程、
第三方应用或高频轮询。资格检查需验证目录创建、进程加入、kill及事件，
文件存在本身不算可用。记录scope路径/身份和恢复日志，仅清理能够证明
属于Aurora的任务scope，绝不向既有/system、uid或pid组写cgroup.kill。
不支持/权限拒绝就返回此Root Linux后端不可用。

## 与其他选择对比

- 必需PID namespace：Linux CI通过，但本手机缺少能力；Root本身不能
  补出未提供的内核功能，因此不继续将其作为Android硬前置。
- 仅进程组：无法覆盖setsid脱离，不选。
- 仅subreaper+逐进程追踪：可收孤儿，但并发fork和监督强杀恢复复杂；
  不将它伪装为与cgroup.kill等价的内核任务范围。
- 系统cgroup v2任务scope：本机存在对应机制，内核kill支持并发fork处理，
  事件可用于收尾。仍需实际资格检查和Android任务策略/Root撤权测试。

Root代码仍是系统级能力，不能保证抵御主动写系统cgroup或逃逸的任意Root
程序；这里是生命周期边界，不是安全沙箱。

## 文件安装的另一条实测边界

普通UID下载/解压Ubuntu时，Files.createLink创建perl硬链接被拒绝。
不自动复制116个硬链接，也不无条件打开link2symlink冒充原始文件语义。
Root安装的链接/权限准备应沿既有RootService执行并实际验证；未实现前
在下载前检查链接能力，明确报告缺失能力。普通对话、浏览器、文件工具
和Android原生命令不依赖Linux安装。

一手依据：[cgroup v2 kill与事件语义](https://docs.kernel.org/admin-guide/cgroup-v2.html)、
[child-subreaper](https://man7.org/linux/man-pages/man2/PR_SET_CHILD_SUBREAPER.2const.html)、
[pidfd身份](https://man7.org/linux/man-pages/man2/pidfd_open.2.html)。
