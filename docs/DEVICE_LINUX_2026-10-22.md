# Linux 小范围真机资格 — 2026-10-22

设备：真我GT6 SE（RMX3850）、Android16/API36、ARM64，内核
6.1.141-android14-11-o-gd896e1db71a8；SELinux Enforcing。
保留数据覆盖更新至0.1.21，没有卸载Aurora或清数据。FolkPatch授权仍有效，
真实Root命令结果UID0。测试辅助包在完成后移除。

## 最终通过6项

1. 普通UID执行APK内PRoot --version，确认实际启动/动态链接与固定版本。
2. 普通UID不能执行Root命名空间探测，返回root_required。
3. 硬链接受限时安装在下载前结构化失败，暂存目录清空。
4. Root创建App文件硬链接成功，inode相同且属主保持应用UID。
5. Root在自己的当前cgroup下新建唯一测试scope，将唯一sleep子进程加入，
   仅写该新scope的cgroup.kill，等待该子进程退出，读取populated 0，
   成功删除scope。没有写既有system/uid/pid组的kill文件。
6. Root创建私有挂载空间、递归private传播并核对inode变化；调用者的挂载
   空间身份保持一致。此项不依赖PID命名空间。

原始最终记录见evidence/0.1.21/phone-final-six.txt，OK (6 tests)，没有跳过。
其他同轮重跑不重复累计成更多通过项。

## 发现并保留的失败

- 首次安装在Files.getFileStore抛SecurityException，改为Android StatFs。
- 修正后完成官方镜像下载/大部分提取，但普通UID在perl硬链接处被拒绝。
  安装未发布，暂存清理；之后新增下载前能力检查，没有拷贝/软链接替代。
- 初版Root PID空间探测在namespace_identity返回errno2。真实
  /proc/self/ns包含mnt/net等入口而无pid，Root授权并未丢失。用户批准
  以任务专属cgroup v2替代必需PID空间，见LINUX_CGROUP_PROPOSAL.md。

失败原始日志在evidence/0.1.21，不改写为通过。PID空间旧专项现为可选，
在不提供该能力的内核上标注跳过，不计作PID空间验收成功。

## 仍未验证/实现

PRoot guest程序尚未实际跑到：本轮普通安装因硬链接能力失败，版本启动
不能代替guest执行。Root安装链接/权限准备尚未接入安装器；chroot和
Linux命令/PTY尚未接通。cgroup原生监督、pidfd与事件等待、监督强杀恢复、
挂载/环境租约释放仍需实施。本轮shell固定探测只证明scope基本操作与
空状态读取，不声称已验证事件监听或完整生命周期。

没有真实模型、缓存、编译工具包、逆向、抓包、耗电或性能领先结论。
原生Root/普通工具不依赖Linux环境安装。最终完整真机验收仍在后续。
