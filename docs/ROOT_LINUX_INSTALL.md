# Root 辅助 Linux 安装（0.1.22）

2026-10-22。实现已批准的 Root 文件准备方向，不引入新发行版、守护进程或回退模拟。它只解决安装时硬链接权限，不代表 chroot/PRoot guest 或任务 cgroup 监督已验收。

## 工作路径

App 下载固定摘要的官方镜像、扫描计划并解压普通文件/目录，使用原有 UID 和 Android 文件标签；Root 不重复写整套文件。普通进程无法创建的硬链接，在普通文件写完、符号链接创建前，通过一次 Binder 请求批量补齐。Root 独立校验固定镜像摘要和 TAR 计划，检查固定暂存路径、App 属主和无符号链接祖先；不接受任意目标目录。App 复核硬链接 inode、ELF 和元数据后，按原有事务原子发布版本。无需逐文件 Binder 或 shell 命令。

App 未连接 Root 时仍先探测普通硬链接能力，失败在下载前返回明确错误；不复制文件或用软链接伪造硬链接。Root 协议是可选扩展，旧服务仍可用普通 Root 功能，但 Linux 安装需断开重连取得新能力。

## 取消和恢复

Root 使用已有有界任务会话、回调死亡取消和 cancelAndWait；App 使用有界 CPU 租约，不新增常驻任务。发送请求前原子落盘 ticket，包含操作 UUID、镜像摘要、App UID、Root PID/starttime、boot ID 和服务 epoch。只有同步确认工作线程停止，才移除 ticket 并允许普通清理。

Binder 失败而无法确认停止时保留暂存。后续显式连接 Root 清理：同服务先取消并 join；旧服务须通过 boot ID 或 PID/starttime/进程不存在证明已退出。身份不确定或旧进程仍在则保留；不杀旧进程、不重放请求。删除前拒绝暂存树内挂载。恢复与安装/清理共用 installer 锁；不会启动即盲删未确认暂存。

Root 不恢复 Linux UID/GID：原始值保留在 entries.json；chroot 的属主和运行映射需后续独立实现，当前 executionReady=false。Root 的公开镜像复核会增加一次固定扫描，这是低频安装成本，不加入每个 Agent 工具调用。

## 验证边界

核心新增7项：进程身份解析和票据、链接批次阶段/失败/不完整/符号链接拒绝。Android 新增5项普通协议测试（含跨进程普通服务拒绝 Root 文件操作）和1项显式 Root+镜像安装测试。普通门槛由67改72；新 Android 用例本轮仅编译，不能写成手机通过。

真机安装测试必须显式设置 auroraRootTest=true、auroraLinuxImageTest=true，使用 RootLinuxInstallDeviceTest；仅在尚未安装基础环境时运行，已有环境会跳过，避免缓存命中冒充新安装验证。通过后保留环境和用户数据。后续还需真实取消/连接丢失、guest 执行及 cgroup 监督闭环。

RootfsArchive.extract 和 LinuxEnvironmentManager.install 保留超过60行的事务流程：校验、阶段顺序、原子发布与失败收尾必须一起审查；新增 Root 客户端、批处理、票据恢复和服务操作分别拆文件，均在300行目标内。
