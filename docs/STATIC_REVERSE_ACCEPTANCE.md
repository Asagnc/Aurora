# 静态逆向集中设备验收（待执行）

目前只编译，不能标成真机通过。不要现在连接或更新手机。本组要求最终专门验收时无其他Agent/终端任务，先恢复所有未确认Linux任务；保持已有用户数据与工具安装。

## 准备

- ARM64 Android16+，更新当前Aurora并重新连接当前Root服务，FolkPatch软件内已有授权无需清除。
- 用户显式准备并选择Root Linux共享代；通过shared_toolchain install安装固定可信工具包。测试本身绝不联网下载、不替用户更新共享工具。
- 确认Linux cgroup/私有挂载资格通过且没有未确认任务。普通非Root/PRoot不降级替代。
- 仅测试类显式运行，并传auroraRootTest=true、auroraLinuxImageTest=true、auroraReverseTest=true。测试类：`io.github.asagnc.aurora.reverse.StaticReverseDeviceTest`。普通测试默认排除RootRequired/LinuxImageRequired。

## 已编译5项用例

1. 三个真实CLI启动资格与只读工具挂载：确认工具存在，并在固定随机探针路径尝试写入；若意外成功即删除本探针并判失败，不能修改现有共享文件。
2. 732字节自有DEX：JADX全量与className两种路径实际解码，再从guest读取输出并核对标记，不只看exit0。
3. Aurora自身APK manifest解码：复制已安装base APK到本测试会话，再读取decoded manifest核对包名；资源警告要保留，不证明全部资源/源码完整。
4. 缺失DEX输入：确认非零失败与持久任务引用回收，不把输出目录存在当成功。
5. 带工具挂载的任务取消：观察持久bundle引用存在，取消并等待真实清理后确认引用释放、已安装bundle保留。此测试不删除共享工具。

每个用例建立自己的临时会话，使用真实Root文件/命令通道，finally关闭环境租约并经原SpaceManager删除会话；清理失败时保留错误/未知状态供恢复，不绕过删除屏障。测试不调用真实模型。

本组不覆盖低内存、手机断电、服务被杀、双代并行更新、损坏安装后remove/reinstall与长期性能；这些仍是最终验收的独立故障/压力矩阵，不能用本5项冒充全部通过。原有LinuxAgentEnvironmentTest覆盖的版本切换断言也需设备运行。

## 自有夹具与主机证据

源码：app/src/androidTest/fixtures/AuroraReverseProbe.java。脚本scripts/build-reverse-fixture.py接受--jdk、--build-tools与--android-jar，使用javac --release17和D8 --min-api36生成；不需要网络/设备。当前生成工具JDK25、现有Build Tools36.0.0，platform android-37.0。夹具不用平台API，Java17字节码仅为固定分析输入，不改变Aurora的构建JDK/最低Android版本。

assets/reverse/aurora-probe.dex：732字节，SHA256 `79f24e09766c7347a9afacd52ddc4849ad2cedecf77eb3c7f0bd97e1387bd91d`。WindowsJDK25+官方JADX1.5.6已实测全量和单类解码，读取源码标记成功；不是AndroidARM64 JRE27资格。

按类使用官方JADX --single-class与--single-class-output、跳过资源；只生成选定源码仍需输入解析和依赖解析，不承诺固定延迟或按输出大小等比省内存。输出目录唯一，selectedSource是计划路径，错误时不能推断文件已生成。

## 完整验收矩阵（保留原约定）


保留数据覆盖更新；暂不连接手机。最终先在Aurora断开并重连Root，服务linuxRunProtocol需3，不依赖FolkPatch弹窗或重置已有授权。

1. Linux Root基础镜像已安装与原task cgroup/private mount资格通过；共享工具install下载/校验/发布，确认约149MB源归档不会安装后残留，不重复下载同bundle。
2. shared_toolchain probe，逐项看到ARM64 JRE27/JADX1.5.6/Apktool3.0.3成功；失败保留真实缺库/SELinux/版本/挂载等输出，不改全局策略补救。重启App/换Root身份要重新probe。
3. 用自有小APK/DEX分别做java/manifest/resources/smali，检查清单、方法、字面值及实际输出；保留工具警告，资源缺失不伪装完整解码。输入显式进入本会话.aurora-linux/project，产物不进入共享区。
4. 运行中remove同bundle须被拒绝，另一会话也不能绕过；停止/清理证明后可删除重装。App进程退出/Root断连后SQL引用须保留，recover_runs确认结束后才能回收。
5. 较大自有样本测超时、取消、熄屏、低内存/空间不足、删除会话；核实Java线程/子进程、工具挂载、cgroup和租约没有泄漏。不自动重放未知结果。
6. 记录Release时延、CPU/RSS、产物大小与分析质量；默认heap/thread是待测值，不能从编译或WindowsCLI结果宣称安卓极限。

Linux CI新3项验证只读映射、宿主源不变、源/目标链接拒绝、相对路径拒绝；不代替Android SELinux和实际ARM64 Java。
