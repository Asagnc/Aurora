# 0.1.25 电脑侧证据（2026-10-22）

Debug/R8 Release/测试APK/lint通过；双ABI ELF、16KiB与对应源码校验通过；核心136，Windows131通过/5项POSIX跳过。Android普通门槛73，无新增设备运行证据。

新增5项固定原生chroot挂载测试由Linux CI实跑，旧13项cgroup/恢复继续；不能把交叉编译当作运行通过。没有执行真实Ubuntu ELF、修改现有镜像属主或开放业务命令；Root/PRoot属主代方案已获确认但发布和租约仍待接。

上一版完整[CI39734462398](https://github.com/Asagnc/Aurora/actions/runs/39734462398)成功。
