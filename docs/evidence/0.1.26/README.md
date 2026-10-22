# 0.1.26 电脑侧证据（2026-10-22）

Debug/R8 Release/测试APK/lint通过；双ABI ELF、16KiB与对应源码通过。核心143，Windows136通过/7项POSIX跳过；7项新增中5通过/2 POSIX跳过，由Linux CI补跑。Android新增普通5项仅编译，普通门槛78，Root镜像专项已更新但未运行。

没有新增真机、真实guest执行或性能证据；Root属主恢复、清理和目录fsync仍需实际Android验证。现有SQLite lease已覆盖Root代索引，Root业务命令/PTY实际运行租约尚未接。上一版完整[CI39736472325](https://github.com/Asagnc/Aurora/actions/runs/39736472325)成功。
