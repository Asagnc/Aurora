# 0.1.10 真机与页面证据

解释、边界与APK哈希见[真机验收报告](../../DEVICE_ACCEPTANCE_2026-10-21.md)，CPU范围见[COMMAND_POWER.md](../../COMMAND_POWER.md)。最终真机41普通+1独立恢复+4Root+2亮/熄屏通过；核心45，debug/test/release构建和lint通过（既有1警告）。本轮模拟器未完成，不作为通过记录。

- ordinary-0110.txt / browser-recovery-0110.txt：最终普通与恢复。
- root-0110.txt / power-matrix-final.txt：最终Root与亮/熄屏。
- screen-root-before-wake.txt：0.1.9正式熄屏超时。
- screen-root-after-wake.txt：接入有界锁后、用户再次处理策略前仍超时。
- screen-root-policy-exempt.txt：用户处理策略后首个熄屏通过。
- idle-health.txt：任务后Aurora锁数量0和安装版本。
- core-final.txt：实际--rerun-tasks核心检查，JUnit XML合计45。

## DSHA实际手机截图

均为Android16 ARM64真实页面，裁掉顶部140像素系统状态栏，未改绘应用。原始尺寸/裁剪区域/原图哈希见screenshot-crops.json；原始系统栏不上传。对应txt仅含DSHA包的页面文字，不上传系统通知或全机层级。

| 截图 | 内容 |
| --- | --- |
| [手机聊天](phone-13-chat.png) | 未配置API Key |
| [抽屉](phone-16-drawer.png) | 手机会话目录 |
| [通用设置](phone-21-web-settings.png) | 实际Web设置 |
| [Agent预设](phone-26-web-agent-presets-edge.png) | 成功进入，初次中心点误触关闭不列成功截图 |
| [PTY页面](phone-30-native-terminal.png) | 仅页面检查 |
| [简易终端结果](phone-34-terminal-result.png) | uname -m返回aarch64 |
| [Skills](phone-37-skills.png) | 目录与导入说明 |
| [备份](phone-39-backup.png) | 范围与默认排除密钥 |
| [自动备份](phone-41-automatic-backup.png) | 说明，未实际创建/恢复 |
| [运行停止](phone-44-runtime-stopped.png) | Web停止，之后停止测试应用进程 |

页面可进入和一条只读命令成功不等于所有插件、模型、备份、虚拟屏执行通过。本轮未使用真实模型密钥、未上传用户文件、未给竞品设备控制授权。
