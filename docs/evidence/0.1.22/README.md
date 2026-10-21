# 0.1.22 电脑侧证据（2026-10-22）

- core-summary.txt：核心136，Windows131通过/5项POSIX跳过，无失败；固定官方ARM64样本扫描在内。
- android-build.txt：Debug、R8 Release、androidTest APK和lint通过。测试辅助包仅编译，新增Android用例未在设备执行。
- native-packaging.txt：Debug/Release双ABI原生ELF/依赖/16KiB及对应源码包检查。
- 本轮不连接手机、不安装或修改设备，不涉及付费模型连接。前版0.1.21完整CI已成功：[39646438332](https://github.com/Asagnc/Aurora/actions/runs/39646438332)。
- Root批量安装/取消/恢复的真实手机证据待补；PRoot guest/chroot/完整cgroup执行均不声明通过。
