# Aurora

Aurora 是面向 **Android 16 及以上（API 36+）** 的手机 Agent 应用，当前开发版本为 **0.1.40**。普通功能不需要 Root；系统级操作在 Root 管理器授权、应用连接和当前运行资格通过后使用。

应用包名：`io.github.asagnc.aurora`。项目仍在开发和验收中，未正式发布。

## 当前能力

- **对话与 Agent**：流式模型协议、自定义网关、加密凭据、SQLite 对话、任务前台服务、执行记录和中断恢复。
- **文件与浏览器**：对话独立文件空间、共享文件、批量文件操作、网页读取与交互、下载及文件选择。
- **命令与 Linux**：普通 UID／Root 命令、交互终端、Root chroot 环境、共享工具版本与租约、持久任务和恢复屏障。
- **网络与逆向**：Root 抓包、PCAP 分析与轮转、当前 socket UID 快照、HTTP 重放、按需 TLS／HTTP 代理、JADX／Apktool、Frida Root Binder 和活动账本。
- **手机构建**：共享 ARM64 JDK／Gradle／Android 工具链、公共只读依赖基线、Java／Compose／JNI 构建与 APK 导出。
- **Skills**：导入与来源管理、私有 GitHub 凭据、显式信任公钥、Ed25519 验签及撤销记录。

上述能力的实际设备覆盖和限制分别记录在验收文档中；不表示全部场景或正式 Release 已通过。抓包只走 Root，不提供 VPN 回退；UID 归属是查询时的真实 socket 快照，不冒充历史逐包归属。

## 最新验证与发布状态

截至 **2026-11-09**，最新已验证产品提交为 `a621f7f06a455b0a968c0a2cfeae701977ea3618`；后续文档提交不改变该审查包的来源身份。

| 项目 | 已取得的证据 | 当前边界 |
| --- | --- | --- |
| Frida 17.26.2 主包 | 当前启动九个用例全部通过：策略资格、Root native／ART、普通 App attach、spawn、resume 前异常、取消、Root／worker／App 死亡恢复 | 使用经审计的本次启动基线；不是主包空基线新启动或默认 Release 的替代证明。正式策略入口仍关闭 |
| Linux Frida 生命周期 | 30 项真实会话测试全部通过；已定位并修正一次性 worker 全局 GIO 析构卡住问题 | 历史 PC 0x320 崩溃未确认根因，不声称已因果修复 |
| 完整 CI | [42602162251](https://github.com/Asagnc/Aurora/actions/runs/42602162251) 五个任务全部成功 | 对应上述产品提交，主机通过不替代真机资格 |
| 手机 APK 构建 | 标准构建、导出、普通 UID JNI 已有真机证据；484 MB 文件选择器完整安装／清理已在独立空安装区通过 | 后者使用固定离线伴随输入，不冒充官方下载或新安装后的运行资格 |
| 完整审查包 | `app/build/outputs/release-review-a621f7f-complete`；10 条校验和独立复核、Frida 离线重链接和 guard 重建通过 | 未正式签名，`releaseReady=false`；不是正式发行包 |

详细证据：[主包 Frida 验收](docs/evidence/0.1.40/frida-1726-main-integration.json)、[大归档 SAF 验收](docs/evidence/0.1.40/apk-large-saf-20261109.json)、[完整审查包记录](docs/RELEASE_REVIEW_20261109.md)。更早版本的结果只作为历史证据，不能自动扩展到当前产品字节。

## 哪些工作还需要手机

**目前无需持续连接手机。** 先在电脑上准备并核对剩余用例，再集中安排设备验收；不重复执行已经取得充分证据的完整批次。下表是剩余范围，不表示每项都需要用户手动操作。

| 阶段／项目 | 仍需手机验证的内容 | 需要用户配合的操作 |
| --- | --- | --- |
| 功能收尾：Frida | 当前主包空基线新启动、资格失效与正式入口条件的最终核对；保留历史崩溃排查范围 | 如需新启动或管理器撤权，由用户在用例准备好后手动重启、解锁或调整该应用授权；不自行重启 |
| 功能收尾：网络捕获 | 后台／熄屏长时轮转，以及未覆盖的死亡、封口／发布中断、取消和删除恢复路径 | 集中留出一段测试时间；需要熄屏时提前说明，不调整全局路由或后台管理器设置 |
| 功能收尾：代理与静态逆向 | 未覆盖的取消、进程死亡、会话删除、工具租约释放及代理私有目录回收 | 通常仅需保持连接；不自动安装系统 CA |
| 功能收尾：APK 工具链 | 未覆盖的安装／构建失败、取消和恢复边界 | 若用例需要系统文件选择器，再由用户选择指定测试文件；已通过的 484 MB 完整导入不重复列为缺口 |
| 功能收尾：浏览器与文件提供器 | 核对后补测剩余提供器、后台恢复和系统交互边界 | 只在自动化无法完成的文件选择或系统界面中协助 |
| UI 阶段 | 对照既有 Eta 比较和批准方案，检查当前页面、输入法、明暗主题及实际布局 | 手机实拍与修改后确认；在功能验收之后进行 |
| 用户配置后：Skills | 自有真实私仓、凭据撤销、公钥信任／撤销及最终包行为 | 在应用中配置所需凭据，不向聊天或仓库发送 PAT／私钥 |
| 用户配置后：正式发布 | 默认 Release、正式签名后的安装／升级与核心功能验证 | 由用户配置现有正式签名密钥；不生成替代密钥，不清空主应用数据 |
| 用户配置后：付费模型 | 真实供应商请求、路由、usage／缓存记录及失败行为 | 用户配置模型密钥并确认开始付费测试后才调用 |

短时后台 Root 检查、30 分钟低流量捕获、64 段／512 MiB 预算、Root 撤权恢复、浏览器恢复等已有部分设备证据；上表只补齐缺口，不将它们全部当成未测试。高负载捕获曾出现非零内核丢包，不作零丢包或线速承诺。

**不需要手机的工作**：代码审查、核心与 Linux 主机测试、CI、固定来源／许可材料核对、离线重链接、未签名审查包生成及文档整理。完整剩余清单以 [NEXT_STEPS](docs/NEXT_STEPS.md) 为准。

## 开发构建

沿用现有工具安装即可，无需重装 Android Studio：JDK 25、NDK `30.0.16248370`、SDK CMake `4.1.2`、编译平台 API 37、Build Tools `36.0.0`。应用最低要求仍是 API 36。依赖和工具来源固定，具体准备步骤见 [构建记录](docs/BUILD.md) 与 CI 配置。

Windows PowerShell：

```powershell
./gradlew.bat :core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease
```

Linux／macOS：

```sh
./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease
```

编译 AndroidTest 不等于执行真机测试。Root、Linux 镜像及其他专项需要显式选择和设备前置条件，不盲目运行整个设备测试集。Debug 为开发预览，默认 Release 输出未签名。

## 文档入口

- [当前状态、剩余工作与接续约定](docs/NEXT_STEPS.md)
- [设计基线](docs/DESIGN.md)与[工程规则](AGENTS.md)
- [实现记录](docs/IMPLEMENTATION.md)与[开发进度](docs/PROGRESS.md)
- [2026-10-31 设备回归记录](docs/DEVICE_ACCEPTANCE_20261031.md)（后续变更见 NEXT_STEPS 与各项新证据）
- [Frida 策略实现](docs/FRIDA_POLICY_IMPLEMENTATION.md)、[轮转捕获](docs/CAPTURE_ROTATION_IMPLEMENTATION.md)、[网络代理](docs/NETWORK_PROXY_IMPLEMENTATION.md)
- [Skills 实现](docs/SKILLS_IMPLEMENTATION.md)、[APK 构建方案](docs/APK_BUILD_PROPOSAL.md)、[发布审查记录](docs/RELEASE_REVIEW_20261109.md)

开发顺序保持：**功能验收 → UI → 用户配置密钥后的签名与真实模型测试 → 拓展**。新底层／依赖／持久化选型先比较确认，例行实现与修复连续推进。
