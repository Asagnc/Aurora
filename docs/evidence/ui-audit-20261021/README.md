# 实际 UI 审计证据

日期：2026-10-20 至 21。范围与判断见 [页面审计](../../UI_AUDIT_2026-10-21.md)。截图来自实际应用/工作台，不是设计稿。包含往返与滚动的记录不能按独立页面计数。

## 代表截图

| 文件 | 实际环境与说明 |
| --- | --- |
| [Eta 外观](eta-46-appearance.png) | 官方 v3.3.1，API37 模拟器，模式/动态色/效果/手势 |
| [Eta 工具能力](eta-59-tools.png) | 当前设备能力、权限状态与说明入口；没有执行特权工具 |
| [Eta 备份说明](eta-53-backup.png) | 页面提示包含密钥；未执行导出/导入 |
| [DSHA 环境结果](dsha-07-runtime-result.png) | 官方 ARM64 APK 在 x86 模拟器的安装失败，不代表真机同样失败 |
| [DSHA 插件受阻](dsha-30-plugins.png) | 已点入口，环境恢复状态阻挡插件/终端 |
| [DSHA 设置](dsha-36-settings.png) | 原生管理页面，独立于完整聊天运行时 |
| [DSHA 虚拟屏](dsha-39-virtual-screen.png) | 未创建屏幕/未授予设备通道权限 |
| [DSH 自定义提供商](web-07-provider-protocols.png) | Windows 本地桌面 Web，不能当手机布局 |
| [DSH 插件工作台](web-11-plugin-workbench.png) | 实际列表；没有运行实验性插件 |
| [DSH 终端设置](web-13-terminal-config.png) | 预算与超限转文件说明，未作为运行结果验证 |
| [Aurora 0.1.9 主页](aurora-01-home-019.png) | 原有数据覆盖更新，B 输入区，未知用量如实显示 |
| [Aurora 配色](aurora-06-palettes-019.png) | 实际预设预览；不是新增视觉方案 |
| [Aurora 状态修正](aurora-12-capabilities-fixed.png) | 已实现后台 Agent 与尚未实现交互式终端分开显示 |

存在对应 XML 或可访问性文本时一并保留。完整原始文件哈希列于 [capture-manifest.csv](capture-manifest.csv)，InRepository=false 表示原始记录留在本任务 outputs/ui-audit-20261020；历史0.1.6截图不作为当前版证据。只保存清单与必要截图，不保存竞品安装包、运行环境、API Key 或临时本地 Web 登录 token。

## 本轮小修验证

- 仅修改 ExecutionStatusPage 的能力说明，未增加执行逻辑/依赖/数据库变更。
- `:app:assembleDebug` 成功，2026-10-21；未为静态文案添加镜像测试，也未将此前39项测试结果冒充本次重跑。
- 同一 API37 x86_64 模拟器 `install -r` 成功，保留数据；实际点击抽屉→设置→工具与 Skills，读取新文案并截图。
- 开发 APK：本任务 outputs/Aurora-0.1.9-ui-audit.apk。
- SHA256：`9f4a0fe84b0252849205d2382493d64014ce936b72e19225f1a2bbdb286f0ddd`。
- 原0.1.9后台专项的APK与证据不覆盖，见 ../0.1.9；本轮没有新增真机、Root或真实供应商验收。

2026-10-21 用户明确同意先继续，DSHA 受阻 Android 页面最后补齐。未把桌面 Web、源码说明或受限空页面算作完整手机功能验收。
