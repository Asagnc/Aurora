# 静态逆向集中真机验收（尚未执行）

保留数据覆盖更新；暂不连接手机。最终先在Aurora断开并重连Root，服务linuxRunProtocol需3，不依赖FolkPatch弹窗或重置已有授权。

1. Linux Root基础镜像已安装与原task cgroup/private mount资格通过；共享工具install下载/校验/发布，确认约149MB源归档不会安装后残留，不重复下载同bundle。
2. shared_toolchain probe，逐项看到ARM64 JRE27/JADX1.5.6/Apktool3.0.3成功；失败保留真实缺库/SELinux/版本/挂载等输出，不改全局策略补救。重启App/换Root身份要重新probe。
3. 用自有小APK/DEX分别做java/manifest/resources/smali，检查清单、方法、字面值及实际输出；保留工具警告，资源缺失不伪装完整解码。输入显式进入本会话.aurora-linux/project，产物不进入共享区。
4. 运行中remove同bundle须被拒绝，另一会话也不能绕过；停止/清理证明后可删除重装。App进程退出/Root断连后SQL引用须保留，recover_runs确认结束后才能回收。
5. 较大自有样本测超时、取消、熄屏、低内存/空间不足、删除会话；核实Java线程/子进程、工具挂载、cgroup和租约没有泄漏。不自动重放未知结果。
6. 记录Release时延、CPU/RSS、产物大小与分析质量；默认heap/thread是待测值，不能从编译或WindowsCLI结果宣称安卓极限。

Linux CI新3项验证只读映射、宿主源不变、源/目标链接拒绝、相对路径拒绝；不代替Android SELinux和实际ARM64 Java。
