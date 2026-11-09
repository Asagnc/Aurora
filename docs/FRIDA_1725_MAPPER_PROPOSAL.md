# Frida 17.25 mapper 独立资格提议（2026-10-31，已确认独立资格）

## 已知问题与基线

当前生产代码固定Frida17.24.3、bridge7.0.13、独立worker/Binder/活动意图/guard/cgroup/journal。解除Aurora的Thanox限制后，六项组合三个独立批次通过；此前受限配置下App原生SIGSEGV/PC0x320仍保留，未确认为代码修复。该地址与固定Frida的远程调用哨兵相同只是线索，不是根因证据。FridaPolicyAvailability.releaseQualified=false继续保持。

最新完整审查包绑定cba97ab，旧Frida与来源材料完整保留。本提议不改已通过的SDK只读依赖路径，也不把外部配置变化算作原生崩溃已修复。

## 新版本实际变化

官方2026-10-30发布17.25.0，GitHub latest与固定tag均已核实。发布说明介绍Linux改用进程外ELF mapper，避开目标内dlopen和memfd加载路径；还拆出Efni/Idun子项目，并有Linux异常展开与arm64重定位修复。新的Idun设备栈在部分桌面平台有按需daemon机制，不能据此推断Android本地worker会启动，也不能忽略资格检查。

这不是仅换版本号：当前最小SELinux适配专门服务旧memfd路径，不能直接沿用其必要性/充分性结论，更不能先关闭或撤回当前全局策略。官方说明没有证明该版本修复Aurora遇到的0x320事件，仍须针对自有目标验证取消、冻结/恢复和死亡窗口。

| 路线 | 收益与代价 | 建议 |
| --- | --- | --- |
| 保持17.24.3并继续排查 | 原有字节、回归与来源闭包输入稳定；仍有受限环境原生异常，正式入口继续关闭 | 保留为不可变基线 |
| 17.25.0先独立资格，再决定替换 | 当前稳定版的新加载路线可能减少旧memfd依赖；需要重核ABI、bridge、子进程、SELinux、恢复和来源材料，不保证能修好旧异常 | 推荐进入独立资格，尚不接生产 |
| 自行维护17.24.3补丁或改guard终止语义 | 可聚焦RemoteCall/取消窗口；需先证明根因，并承担上游分叉或延长worker生存期的维护/生命周期成本 | 目前证据不足，不先选择 |

## 固定候选与来源

- 当前17.24.3 Android ARM64 core devkit：60,903,232字节，SHA256 0c688824a24949e7be77109fd2c7306bba60cb47f608789e141bb24cd23b1005。
- 候选17.25.0官方Android ARM64 core devkit：61,054,192字节，GitHub公布SHA256 985d334e4c71ed6190fdfbf748b96c121b69c360d0d36838244f7da4c35fe59f；已独立下载并校验，候选 worker 主机构建和实际 Android 普通 Shell 的 --version 已通过；未接生产；后续独立包 Root 注入资格失败，见末节。
- core固定tag提交f91556129517937e82831458d10f6b8f4ca06dbb；gum固定tag提交5ebe3aeccab8dcd4b727378dab67fb15fa8a3cca。
- efni/idun及其余实际链接输入、bridge/compiler版本、原生运行闭包与许可材料须按实际候选补齐。候选 stripped worker 为89,491,280字节；APK/RSS仍未测量。

## 拟授权范围与开放标准

1. 只创建独立候选准备区，校验官方固定字节；不改当前生产pin/已安装包/共享活版本。先编译现有worker并检查实际SDK版本、C API、符号/链接依赖、16KiB和Android36支持。禁止新旧SDK混链来掩盖不兼容；旧私有libsepol符号若不可用，先明确兼容缺口。
2. 复用既有独立worker、Root Binder调用方校验、不可变票据、现有活动账本/journal/版本租约及退出/删除屏障；不新增数据库、通用daemon、frida-server常驻服务或系统CA。不自动撤旧策略，不新增通用execmem/tmpfs执行或permissive。
3. 验证只使用本机自有夹具：Root native/ART、普通App attach、自有包早期spawn hook、resume前异常、取消、目标暂停/恢复及Root/worker/App死亡；不误杀活目标、不重放未知副作用，未知保留并阻断删除。源码差异与进程/监听清单必须确认没有额外设备栈daemon/监听器，协议字段必须反映实际版本与能力。
4. 对“是否无需全局SELinux适配”单独验收。当前boot仍有先前已批准增量，不能在该条件下宣称stock策略通过；需要后续由用户安排新boot并解锁，不由ADB自行重启、不回写旧整份策略。只读审计保留Enforcing。
5. 完整CI、实际ARM64、真机故障矩阵、固定来源/源码材料与完整未签名审查包对应后，才提议替换生产。未通过则保留17.24.3审查基线和关闭入口；不使用正式密钥或付费模型，不更改用户Thanox规则。

当前需要确认的是“允许17.25.0新mapper进入独立资格”，并非立即升级/开放或保证解决旧崩溃。用户于2026-10-31已明确采用推荐方案，先独立资格再决定替换；不自动升级生产或改全局策略。

依据：[官方17.25.0说明](https://frida.re/news/2026/10/30/frida-17-25-0-released/)、[官方固定发布及附件摘要](https://github.com/frida/frida/releases/tag/17.25.0)、[固定core源码](https://github.com/frida/frida-core/tree/f91556129517937e82831458d10f6b8f4ca06dbb)、[固定gum源码](https://github.com/frida/frida-gum/tree/5ebe3aeccab8dcd4b727378dab67fb15fa8a3cca)。AGENTS.md第19行要求“Confirm newly introduced architectural choices before implementing them”。

## 独立资格进展

现有worker使用单一新devkit完成编译、四项系统动态依赖和16KiB ELF检查；候选worker SHA256 ad83aec06cbc42a542bc7c05108eb4be6146fd269ef7b969f5e5cf48a0cad06d。实际Android普通Shell仅执行--version返回17.25.0，随后删除临时文件，未调用frida_init/Root/注入。首次沿用旧SDK展开上限失败记录保留；候选按官方五个归档条目与350,867,568展开字节精确检查后重试通过，未放宽生产上限。

这只证明构建和版本执行，不证明新mapper、私有策略ABI或无daemon。固定源码/运行闭包审查、自有目标注入及死亡矩阵仍待；生产17.24.3与releaseQualified=false保持。

## 固定源码与隔离资格包

完整固定core源归档4,374,507字节，SHA256 b36c4e6f85aa5fdf60b068b500949d676396bca63584dffe2f6af8c921ce3879；检查312个文本源码，自动策略补丁调用位于server/inject/tests入口，不在自有worker调用入口；候选链接map不含该命名符号。Android默认后端分支显式排除Fruity/Droidy，保留成员无Fruity/Idun命名，ELF mapper成员实际保留。17.24.3与17.25.0的lib/selinux/patch.c、头文件和selinux.wrap逐字节相同，libsepol来源仍为507985d。静态证据不替代运行时无daemon/监听/策略改动证明，详见tools/frida-mapper-qualification/source-audit.json。

为保持当前安装/data/共享工具与资格实验分开，使用冻结9ae826a源码副本生成单独包名io.github.asagnc.aurora.fridaqualification，名称Aurora Frida Qualification。只改副本的包身份/标签/工具版本说明和构建输入选择，worker只接受固定ad83aec0摘要，Java桥沿用原固定7.0.13字节；Root Binder、账本、guard/cgroup、journal和回收实现不变。不增加产品候选路径/调试后门、不在原App注入下载路径。首次构建缺少Git忽略的固定bridge输入失败；补入经原bundle-inputs验证的同一字节。新副本还需将既有winflexbison置于构建PATH，未安装新工具。

独立包通过构建、包名/签名/内置worker与目标instrumentation审查后才安装，并请用户在Root管理器中单独授权；不复用主App的私有数据或Root授权，不把新身份算成当前Aurora正式资格。该测试包不是正式发行，来源/许可闭包和完整故障矩阵仍待。

## 独立设备资格结果（2026-11-09归档）

17.25.0 独立包首轮六项全部失败（21.443秒），另三次诊断复测保留失败。自有 Root 服务被新 agent 注入后 SIGABRT，原生 abort 信息为 `Unable to locate the libc; please file a bug`，随后客户端收到 Binder 死亡 `Root service exited`，guard 取消 worker。不能归因为授权失败，也不证明旧0x320已修复。停止重复注入，候选不晋升；生产继续固定17.24.3、正式门控false。精简证据见evidence/0.1.40/frida-1725-device-failure.json。

构建副本遇到上游talloc下载504后，复用已验收主包内24个自有原生执行器（双ABI各12个，固定APK摘要核对），明确排除旧Frida worker；新worker仍只接受ad83aec0固定字节。准备脚本要求显式提供原验收APK，不依赖随后会被覆盖的build输出。诊断副本额外捕获工具失败堆栈，未进入主产品。

此轮Enforcing前后均保持，但最小适配执行后策略字节变化，因此不是stock策略或策略未变证明。恢复接续时独立包有9条待恢复票据，必须经既有恢复协议确认再清理，不能直接删数据库。用户已人工确认新的整页Root引导正确且连接成功，授权由管理器控制；不再要求授权弹窗。

2026-11-09收尾实测：独立包9个精确自有夹具经现有恢复协议1项4.905秒通过，数据库只读复核待恢复票据0、自有会话0；没有强删数据库或声称脚本已卸载。主包保留数据更新后Root连接/重连/命令/PTY/取消/超时清理6项13.859秒通过；Debug/Release/AndroidTest及双lint共224任务构建成功。见root-guidance-20261109.json。
