# 0.1.15 本地验证

日期：2026-10-21。实现提交：684b6fb。

`local-build.txt`：核心测试、Debug、R8 Release、AndroidTest APK、lintDebug 全部通过。核心共 87 项，新增 15 项 Skills 用例的原始 XML 同目录保留。lint 为 0 错误、既有 MiMo VectorPath 警告 1 条。

Debug APK SHA256：`e91df1d19b594303014b13b19cf7af844c553a8641ed7e8dff0c36c37d8e835e`。

新增两项 Android SkillRegistryTest 仅编译：v3→v4 消息保留迁移；更新/固定引用/删除引用/清理日志。普通设备门槛从 61 更新为 63，本次未启动设备，不把编译当执行通过。没有真实供应商、Root、新 UI 实机、耗电或性能对比结论。

完整实现与待办见 ../../SKILLS_IMPLEMENTATION.md、../../PROGRESS.md。CI 独立状态以 GitHub Actions 当前记录为准。
