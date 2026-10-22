# 0.1.23 电脑侧证据（2026-10-22）

新增cgroup固定资格、原生库双ABI构建、Agent probe_root。Android普通1+Root1新增用例只编译，未连设备，普通门槛73。核心136，无新核心源码变更。Debug/R8 Release/testAPK/lint以及ELF/16KiB/对应源码包校验见同目录日志。

真实cgroup/pidfd/父死亡6项由Linux CI执行；结果另行记录，不把Windows交叉编译等同于实跑。监督自身强杀的持久恢复、完整chroot/任意命令/PTY未实现。
