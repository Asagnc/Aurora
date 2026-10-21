# Android16 ARM64 本地验收 · Aurora 0.1.10

2026-10-21。用户授权全部可进行测试，后续明确选择按真机结果继续、这一轮只做本地验收。没有调用真实付费模型或宣称真实缓存命中最优。

## 最终结果

| 检查 | 结果 | 证据文件 |
| --- | --- | --- |
| 核心JVM | 45通过，--rerun-tasks实际重跑 | core-final.txt；本地JUnit XML合计45 |
| 真机普通 | 41通过 | ordinary-0110.txt |
| 独立浏览器启动恢复 | 1通过，单独instrumentation进程 | browser-recovery-0110.txt |
| 显式授权Root | 4通过 | root-0110.txt |
| 后台亮屏/熄屏Root | 2通过，共35.638秒 | power-matrix-final.txt |
| Debug、AndroidTest、R8 Release | 构建通过 | 本任务outputs/acceptance-20261021/wake-build.txt |
| Lint | 0错误，1项既有MiMo VectorPath警告 | app/build/reports/lint-results-debug.txt |
| 任务后CPU锁 | ActiveAuroraWakeLocks=0 | idle-health.txt |
| 模拟器本轮 | 未完成，卡住/冷启动退出；用户同意先按真机继续 | 不列为通过，也不拿旧0.1.9结果替代 |

设备48项是41+1+4+2的最终版本用例，不与最初0.1.9重复测试相加。它们验证已实现能力，不代表所有规划功能已完成。证据位于[evidence/0.1.10](evidence/0.1.10/README.md)。

环境：真我GT6SE/RMX3850，实际Android16/API36、arm64-v8a。FolkPatch为此前用户在管理器内授权；本轮Root接口再次确认UID0。现有数据从0.1.8覆盖到0.1.9、0.1.10，未卸载主应用或清除数据。最终versionCode11，versionName0.1.10。

开发APK：本任务outputs/Aurora-0.1.10-development.apk；SHA256 `2be096876e2a159028bb0d618066642955aab2d856037eb093ccb0b8680db7d2`。不是正式签名发行版。

## 覆盖与过程

普通组包含浏览器Agent批量/DOM/操作、文件清理与持久化、普通UID跨进程Binder/命令、后台Agent生命周期、真实ARM64原生命令与主题对比度。后台组检查切换会话/界面生命周期、停止服务和通知停止入口、取消收尾、重复提交与失败启动。新增两项真实PowerManager检查并发lease独立释放、系统超时回收。

4项Root包含握手/连接断开重连、UID0输出、受控产物归属/读取、取消/超时/断开清理。2项新专项先确认命令已写出首个tick，再退到主页；分别保持亮屏和执行睡眠键并检查Asleep/Dozing，要求15个tick、退出码0、UID0和任务入口收尾。均显式要求auroraRootTest=true及auroraScreenTest=true；普通测试默认排除RootRequired。

电脑直接授予通知权限被设备以权限不足拒绝，用户在手机设置中处理后继续。首轮普通组在文件清理之后停住，线程信号等待且系统栈出现Thanox；用户处理后台策略并打开应用后恢复，39项通过。203.906秒包含停顿，不能与新版13.858秒比较为性能提升。

熄屏探索有一轮夹具漏建目录，纠正准备逻辑后才继续。随后0.1.9等待40秒超时；按用户确认接入有界CPU锁后，在原先策略下仍超时。用户再次处理后台冻结豁免后，熄屏17.562秒通过；最终亮屏/熄屏组合35.638秒2项通过。超时和后续通过均保留，不把改善单独归因于唤醒锁。见[COMMAND_POWER.md](COMMAND_POWER.md)。

没有对日用真机执行模拟器脚本中的Aurora force-stop/进程死亡序列。真机新版本进程死亡恢复、外部Root管理器撤权、实际Root服务强杀、长期拔线/深度Doze/电池模式矩阵仍未验收。短时USB熄屏不是长期省电保证；唤醒锁不能绕过主动冻结。

收尾：保留Aurora0.1.10和用户数据，移除io.github.asagnc.aurora.test并确认仅主包仍在；DSHA先通过可见按钮停止Web运行，再停止本轮安装的测试应用进程，保留数据。全局冻结设置未由测试工具更改，设备设置由用户处理。

## DSHA手机补查

官方v0.2.0-rc2标准APK在ARM64手机完成内置Ubuntu/Node离线部署，显示READY，DSH0.2.0-rc.2。未授予DSHA Root/ADB/Shizuku，未输入API Key或登录模型账户。

实际进入手机聊天WebView、模型/推理菜单、抽屉、全局插件、终端设置、通用/模型/内置插件/Agent预设四类Web设置；原生插件市场/管理、PTY/简易终端、Skills、备份/自动备份和工作文件页。简易终端uname -m实际返回aarch64。PTY页面已打开，但ADB模拟输入未获回显，未据此判定产品故障或执行通过。

细节：Agent预设标签与关闭按钮的可访问性边界重叠，中心点击关闭面板，改点标签可见左缘后成功进入；Aurora需要检查窄屏触达。作品文件页提示配置工作目录暂不可读并回退/root/Documents，只记录实际状态。备份默认排除原生密钥、自动备份默认开启；Skills仅复制SKILL.md，引用资料需另行放入。未执行备份恢复覆盖或第三方插件。

结论继续保持：保留胶囊B，学习DSHA的任务/文件工作台与Eta的能力/权限状态；Aurora仍缺Markdown/消息操作、持续输出/PTY、Skills/依赖安装、Linux/抓包/逆向/手机构建链，不因本轮通过而改成完成。

截图顶部140像素系统状态栏已裁除，避免公开手机通知；应用界面未生成或改绘，未上传原始状态栏与完整手机层级XML。裁剪参数和原图哈希见证据目录。

## 尚未验证

- 真实供应商/中转、在售型号能力、费用/TTFT/缓存读写；用户明确本轮先本地验收。
- 尚未实现的PTY/持续日志、Skills安装、Linux、抓发包、逆向和手机构建链。
- 原生多模态、长文Markdown、浏览器上传下载/异步脚本及完整renderer故障矩阵。
- 小时级耗电/峰值内存、跨机型/Root管理器与性能领先结论。
- 本轮模拟器回归和新版独立进程死亡恢复；旧版结果保持历史身份。
