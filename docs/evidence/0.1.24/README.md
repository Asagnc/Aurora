# 0.1.24 电脑侧证据（2026-10-22）

Debug/R8 Release/测试APK/lint通过；双ABI ELF、16KiB与对应源码包通过；核心136，Windows131通过/5项POSIX跳过，无新核心源码变更。Android用例只编译，无新真机证据。

新增7项scope恢复故障在Linux CI实际执行，原6项生命周期继续执行；以对应CI日志为准，交叉编译不是运行验收。上一版完整[CI39732417088](https://github.com/Asagnc/Aurora/actions/runs/39732417088)成功。当前完整chroot/命令/PTY及业务租约仍未就绪，Android权限撤回矩阵待实测。
