## 2026-10-22 Linux PTY原生组合

新增--run-pty控制终端移交与前台guest组，复用已有PTY传输与独立scope监督。新增Root主机五项待CI；Android双ABI Debug/testAPK/R8Release/lint构建通过。应用Linux终端/Binder票据与租约尚未接通，executionReady=false，无新真机。详见LINUX_PTY_NATIVE.md。

## 2026-10-22 Root Linux运行验收准备

真实镜像专项新增三项，覆盖guest退出/文件/导出/版本编辑/删除、超时及取消后台子进程与租约。Android测试APK本地编译通过，尚未真机执行；见LINUX_RUN_QUALIFICATION.md。83aa2c1完整CI39755532891成功。无产品行为变更，不递增App版本号。

## 0.1.28 Root批量文件通道（2026-10-22，优先接续）

用户已确认LINUX_WORKSPACE_ACCESS_PROPOSAL.md推荐方案，不再询问此选择。已删除每命令递归归还属主，收尾只处理固定元数据。新增Root列目录/批量读/版本编辑/选定导出/删除、跨Root进程文件锁及持久retirement防迟到复活。Agent原文件工具按.aurora-linux路径分派，读保持顺序、混合域编辑写前拒绝；文件浏览和图片发布已接。Root丢失时待删记录保留、其他启动恢复继续。详见LINUX_ROOT_FILES.md。

核心155：Windows148通过/7POSIX跳过；新增Android普通4只编译（切片/FIFO链接/待删保留/无关删除恢复），普通门槛88；旧Binder普通fixture拒绝新Root文件接口。Debug/testAPK/R8Release/lint、双ABI ELF检查通过，证据evidence/0.1.28。d841962完整CI39750999177成功。无新真机；任意Agent Linux命令仍未开放、Linux PTY未接，executionReady=false。Run能力2/File能力1需新Root服务。

已补UTF-8切片续读：普通/Root文件共用单次CharsetDecoder，截断中文/emoji时只推进已返回字符，真正非法字节与EOF损坏仍为非文本；新增核心3用例。用户从设置重新连接Root后自动重试待删会话，Root仍不可用则继续保留。

下一步Root派发/真实Ubuntu/批量文件/选定图片/撤权/删除专项、Linux PTY与PRoot生命周期；当前Root运行与文件tombstone/锁元数据保留，GC需旧epoch死亡和无待处理记录资格，不能删除活锁或误放迟到请求。批量文本/路径32KiB预算，图片选定导出32MiB；大文本FD传输尚未实现，不宣称性能领先。

## 0.1.27 应用Root派发与运行账本（2026-10-22，优先接续）

22fad47完整CI39747188832已成功。新增SQLite v6原子运行意图+租约、Root Binder start/recover、服务epoch与取消join/旧进程死亡屏障、单次Root票据/迟到请求拒绝、原生scope恢复后确认、会话可写目录和显式恢复入口。App启动保留有租约的待删会话而不整段启动失败。见LINUX_APP_RUNS.md。

核心147：Windows固定sample重跑140通过/7POSIX跳过；Android普通新增4只编译、门槛84，普通Binder拒绝新Root入口用例已扩展。本地Debug/testAPK/R8Release/lint及ELF/源码打包通过。无新真机或Ubuntu guest运行证据，Agent任意Linux执行仍未开放，PTY未接。

**新选择待确认**：当前内部验证路径在scope退出后递归归还工作区属主，存在大目录收尾成本；不把它当最优方案。已研究并建议保留Root属主、由内置Root批量文件通道按需读写/导出/删除，见LINUX_WORKSPACE_ACCESS_PROPOSAL.md。这是会话可写文件访问的新选择，不重复审批共享镜像属主。确认后替换验证路径，再做Root真机资格/PTY。Root运行tombstone暂保留，后续按旧epoch死亡与无租约资格GC。此状态不可标记完整Linux就绪。

## 2026-10-22 原生启动与恢复互斥

运行协调进程在fork前取得.run.lock，监督继承，worker在入组前关闭。恢复也必须取得同锁，堵住scope记录出现前空目录被误当作已退出的窗口；两层监督死亡后worker不持锁，仍可恢复。新增主机1项“持启动锁、无scope记录也拒绝恢复”，挂载/命令组12，加既有cgroup6/恢复7共25。最新本地双ABI Debug/Release/lint通过。5a5b381的Linux主机阶段（含宿主FD/环境不继承、超时子进程PID实查）已通过；本次新增锁测试和完整CI状态待查。无新真机或Ubuntu26.10 guest证据，应用业务租约/PTY未接，executionReady=false。

## 2026-10-22 会话删除租约屏障

SpaceStore删除与崩溃删除恢复均拒绝仍有Linux租约的会话，防止ON DELETE CASCADE提前抹掉未确认进程的环境保护记录。新增Android2项只编译，普通门槛80；本地Debug/testAPK/R8Release/lint通过。命令主机测试追加验证显式宿主FD和环境变量不会继承；不增加常驻进程或轮询。业务派发/租约创建与恢复闭环、Root文件清理、PTY仍未连接；尚未约真机。f3d1fb3 CI的Linux命令/挂载11、cgroup6和恢复7已通过，完整Android阶段当时仍在运行。

## 2026-10-22 原生guest业务命令入口

618c843完整CI39745288718成功。新增原生--run：真实guest argv/输出/退出码、显式环境/cwd/FD关闭、有界超时及cgroup收尾；Root专属单次目录持久generation/runId，确认scope空且退出后才写completed.json，双监督死亡恢复不伪造完成。见LINUX_GUEST_COMMANDS.md。新增主机4用例，挂载/命令组11；本地Debug/Release/lint通过，CI另记。尚未接应用Binder业务派发/SQL运行租约/PTY/Root会话文件清理，Agent executionReady=false，无新真机结果。下一步必须先闭合这些路径，不将原生入口称为完整Linux功能。版本号仍0.1.26。

# 2026-10-22 guest ELF资格补充

固定chroot shell fixture验证动态ELF加载、干净环境、cwd、产物和退出码37；缺失加载器失败回收，新增主机2项（挂载组7）。本地Debug/Release/lint通过；CI及设备结果另记。不是Ubuntu镜像或业务命令执行，生产租约/PTY尚待接入，executionReady=false。上一版4a783c2完整CI39741811061通过。

# 0.1.26 后端环境代发布（2026-10-22）

Root/普通独立代、原始属主恢复、事务发布和受租约保护的Root清理已接，Root文件操作票据/取消屏障复用原协议，新增Linux能力协议2。见LINUX_ROOT_GENERATIONS.md。核心143；Android普通新增5仅编译，普通门槛78。没有新真机或真实guest证据，Root命令/PTY的运行租约与会话回收仍待接，executionReady=false。

# 0.1.25 chroot挂载资格（2026-10-22）

Root/PRoot后端共享属主代方案已获确认。新增固定C chroot/只读根/可写会话绑定/FD挂载与cgroup收尾测试，主机新增5项，旧13项继续；见LINUX_CHROOT_MOUNTS.md。未运行真实Ubuntu ELF，未接属主代发布与业务租约/命令/PTY，executionReady=false，无新真机证据。

# 0.1.24 scope恢复（2026-10-22）

新增Root专属持久记录和身份复核、协调兜底/两层死亡后独立恢复、入组前父死亡保护及Agent recover_root。见LINUX_SCOPE_RECOVERY.md。新增主机7故障+既有6生命周期；Android正常恢复用例仅编译，普通门槛73。业务chroot/PTY/租约及Android撤权仍待验证，不标记完整执行就绪。

# 0.1.23 cgroup监督资格（2026-10-22）

新增固定任务scope原生程序与Agent probe_root，包含私有mount/pidfd/subreaper/脱离后代/kill空事件及独立监督收尾。新增主机6项和Android普通1+Root1仅编译，普通门槛73。见LINUX_SCOPE_IMPLEMENTATION.md。未开放任意Linux执行，监督强杀持久恢复及chroot/PTY仍待继续。

# 0.1.22 Root 辅助安装（2026-10-22）

普通文件由App创建保留标签，Root单次批量补硬链接；固定镜像独立复核、身份票据、取消确认后清理、失联保留与恢复已接。见 ROOT_LINUX_INSTALL.md。核心136，Android新增普通5+Root镜像专项1，仅编译；普通门槛72。本轮无新真机/guest/chroot性能结论。完整Linux执行及cgroup原生监督仍待继续。

# 0.1.21 真机Linux资格（2026-10-22）

普通3+Root3共6项真机通过，失败记录保留。Android空间查询改StatFs，硬链接能力改下载前检查并明确Root安装尚未接；Root硬链接/cgroup基本操作/私有挂载实测成功。PID空间本机缺失，用户批准任务专属cgroup方案替代必需PID空间。Root probe只查挂载，完整执行仍未就绪。见DEVICE_LINUX_2026-10-22.md及LINUX_CGROUP_PROPOSAL.md。完整本地Android构建/R8/testAPK/lint通过，不扩展为guest/chroot/事件恢复或性能验收。

# 0.1.20 Root 命名空间资格（2026-10-22）

已确认PID命名空间生命周期设计；新增固定原生Root探测、Agent probe_root和既有日志产物，PRoot探测接环境版本租约。没有任意Linux业务命令/chroot。新增Linux Root主机4项，Android普通/Root/显式镜像专项各1，设备未运行前不写通过。详见LINUX_ROOT_LIFECYCLE.md与LINUX_IMPLEMENTATION.md；构建和CI结果以对应证据为准。

# 0.1.19 PRoot 原生构建（2026-10-22）

批准的三组件固定源码摘要，NDK30/API36双ABI构建，PRoot/loader/talloc随APK打包。原生页与Agent固定离线probe入口已接；执行就绪仍false。双ABI Debug/Release ELF、依赖及16KiB校验与独立ARM64适配器构建通过；完整Android构建/R8/testAPK/lint通过。新增Android冒烟仅编译，主机编译不能替代Android实际执行。见LINUX_IMPLEMENTATION.md、LINUX_NATIVE_BUILD.md及evidence/0.1.19。

# 0.1.18 Linux 基础环境（2026-10-22）

Linux方案已获用户认可。本阶段接入官方固定摘要/签名验证、tar.gz链接与权限规则、ELF/解释器检查、SQLite v5版本/恢复/租约元数据、原生安装页和Agent环境管理。详见LINUX_IMPLEMENTATION.md。基础环境安装不等于Linux可执行；PRoot打包、Root挂载/监督、命令与PTY租约仍待继续实施，executionReady保持false。核心126（Windows125通过/1POSIX跳过）；新增Android2项仅编译，设备集中验收不变。

# 0.1.17 Skills 来源与文件浏览（2026-10-21）

显式GitHub ref和子目录接入UI/Agent，斜杠分支与URL路径可明确区分；旧来源JSON/ID兼容，不新增SQLite迁移。修复archive/releases路径误判，来源标明正式发布/快照。skills_read支持省略path搜索/分页全部声明资源；用户有文件浏览与分段阅读页。详情订阅目录版本事件，更新/回滚后刷新真实状态。核心115；完整构建证据与边界见evidence/0.1.17。新增界面仅编译，未冒充实机审美/交互验收。

# 0.1.16 Skills 稳定性（2026-10-21）

核心实际发布/恢复逻辑增加7项失败与取消用例，归档导入4项关闭/取消用例，UTF-8分块3项，共101项核心测试通过。复用SQLite恢复记录，不增加持久化类型；发布中取消完成短提交单元、已提交目录变化保证通知，返回实际启停状态。见 SKILLS_IMPLEMENTATION.md 和 evidence/0.1.16。本地完整Android构建/R8/lint通过，设备证据保持历史范围。

# 0.1.15 Skills（2026-10-21）

已按用户确认实现，详见 [SKILLS_IMPLEMENTATION.md](SKILLS_IMPLEMENTATION.md)。标准解析、版本化共享资源/恢复日志、SQLite v4、按需 Agent 工具、原生管理与显式最新来源更新。核心 87 项；Android 构建与 R8/lint 验证，新增两项设备测试仅编译。没有新增 Linux/逆向/抓包运行时，也没有真实模型缓存或性能结论。

# 实现与验证证据

## 设置状态文案与Skills研究（2026-10-21）

Skills新架构已研究，详见SKILLS_PROPOSAL.md，确认卡待答复，未接依赖/安装器/数据库迁移。独立修复设置版本从包信息后台读取，后台Agent/图文/日志/交互终端文案同步实际能力；Debug/R8Release/lint通过，0错误/既有1警告。此修订不扩展运行能力或硬件证据。

## 0.1.14 交互终端（2026-10-21）

最终实现提交0be35fc完整CI通过：[39597157476](https://github.com/Asagnc/Aurora/actions/runs/39597157476)。核心72、Linux主机PTY8、源码校验12及全部Android构建/lint通过；证据范围见evidence/0.1.14/README.md。设备12项新增用例仍仅编译。

已确认并实现普通/Root PTY、六个Agent工具、会话所有权/记录/中断恢复、原生终端查看与输入、前台服务跨模型回复生命周期、Root控制与FD数据通道，详见INTERACTIVE_TERMINALS.md。核心72通过，本地debug/R8release/testAPK/lint通过（0错误、既有MiMo警告1）。新增10项普通Android及2项Root用例仅编译，Linux主机8项已在CI步骤通过；未连接设备，不宣称Root/UI/性能验收。日志16MiB、尾部64KiB、4/8活动终端及65536单元主缓冲预算明确，不伪装无限日志或强杀后原shell恢复。

## 0.1.13 命令事件日志（2026-10-21）

FileObserver按观看生命周期收集，真实事件驱动增量读取、每流64KiB尾部预算；复用执行ID固定目录、账本持久映射。SQLite3非破坏迁移、Root命令协议2；完整输出结果之外存有效有界presentation，避免截断JSON隐藏错误。详见LIVE_COMMAND_LOGS.md。核心67项通过；新增5项Android专项只编译，Binder路径和v1迁移夹具已更新；新版设备/Root/迁移/手势/耗电未验收。

## 0.1.12 图文消息（2026-10-21）

用户已确认原生Compose Markdown+Coil3+会话图片产物组合。正文/代码复制、流式后台增量、完整阅读、Agent图片发布、本地快照和远程下载、SHA校验、查看/保存/分享已接入；见RICH_CONTENT_PLAN.md，详细边界同列。核心59项通过；debug/R8release/AndroidTest构建与lint通过，0错误/既有1警告。新增5项设备用例已编译但未执行，本版没有真机UI/实际远程站点/真实模型/性能结论。保持B输入区。

## 0.1.11 阅读/详情基础（2026-10-21）

实现消息复制、流式行稳定标识、按会话自动跟随/用户拖动暂停/回到最新；工具预览按JSON字段解析错误与退出码，解析/格式化移至Default线程；命令输出分stdout/stderr、复制预览、到产物目录，原始结果可展开。新增五项核心投影测试。Markdown、图片管线、持续日志与PTY未实现，图文选型待用户确认，见RICH_CONTENT_PLAN.md。本版设备未连接，视觉/手势行为未验收；历史0.1.10设备结果不扩展到新版。

## 0.1.10（2026-10-21）

按用户确认实现本地命令有界CPU唤醒范围；普通执行use收尾，Root在应用侧持有至结果/取消清理后finally释放，UID0服务不重复取得。没有新增依赖、整轮Agent持锁或设备策略修改。见[COMMAND_POWER.md](COMMAND_POWER.md)。真实ARM64普通41、浏览器恢复1、Root4、亮/熄屏2通过，核心45重跑；构建/lint通过，0错误/既有1警告。失败探索记录与策略调整条件保留，不能单独归因于锁；模拟器未完成。证据见[DEVICE_ACCEPTANCE_2026-10-21.md](DEVICE_ACCEPTANCE_2026-10-21.md)。

## 页面审计与状态文案（2026-10-21）

实际点击对比与边界见 [UI_AUDIT_2026-10-21.md](UI_AUDIT_2026-10-21.md)。纠正 ExecutionStatusPage 将已实现后台Agent误列为未接入的旧文案；后台前台服务和未实现交互式终端分别显示，未新增执行逻辑。该小范围显示修正构建与模拟器实际页面核对记录见 evidence/ui-audit-20261021/README.md，不重复扩大为底座回归通过结论。用户同意最后补齐 DSHA 受阻 ARM64 页面。

## 0.1.9（2026-10-20）

已确认的后台Agent架构落地：原生specialUse前台服务、通知停止/返回会话、独立运行投影、SQLite非破坏升级及任务记录。切换会话不取消、不串流，进程死亡只标记中断/未知，不重放工具。实现、验收边界见 BACKGROUND_TASKS.md；本轮新版真机后台/Root联合测试仍待统一验收。

## 0.1.6（2026-10-16）

普通原生命令接入真实 Agent 工具循环。独立原生监督程序负责信号、子进程组、两路输出预算和原子结果元数据；Kotlin 传递 argv、协调取消、返回退出码/产物与不确定状态。无新增运行库，不给普通 UID 自动提权，不默认经过 PRoot。详见 NATIVE_COMMANDS.md。

核心 45 项；模拟器普通测试扩为 26 项，另进程恢复 1 项通过。新增 8 项覆盖真实 native ELF 调用、双路各 9 MiB 输出预算、取消/超时/父退出和异常监督程序、参数/环境/UTF-8、AgentLoop 实际文件产物。ARM64 与 x86_64 均编译；ARM64 运行、Root 执行、实际供应商与性能基准未验收。FEATURE_STATUS.md 为当前能力/缺口索引。

## 0.1.5（2026-10-16）

认可的交互预览已落地，唯一指定图标修订为灯泡。输入区不再嵌套模型边框，指标改为原位展开，主菜单统一底部面板；空白/消息/执行步骤复用同一套正式组件。debug 专用 ChatReviewActivity 提供明确标注的内存示例，release manifest 不含该 Activity。没有新增执行后端或伪造任务结果。

扩展主题对比度用例覆盖输入区表面；极端颜色失败已修正，模拟器 18 项和独立进程恢复 1 项通过，核心 45 项通过。debug/release/lint 构建通过。真机和真实模型待后续，用户本轮明确要求先做好模拟器。证据与截图见 CHAT_REFINEMENT_2026-10-16.md。

## 0.1.4（2026-10-16）

用户确认的外置指标布局已实现；模型/思考控制压缩，无连接模型页显示添加入口。供应商编辑支持返回列表。执行记录使用动作名与准确状态，可展开调用 ID 和有界格式化预览。ToolRunStore 持久化后发送开始/完成事件，界面保留最近 50 项及最多 8001 字符结果，避免逐步查询历史数量。完整结果仍存数据库；取消仍不伪造完成。

核心 45 项、模拟器普通 18 项与独立进程恢复 1 项通过；持久化测试新增事件顺序、预览截断而原结果完整断言。debug/release/lint 通过，仍有既有资源警告。实际页面对比与后续缺口见 UI_AUDIT_2026-10-16.md。当前仅连接模拟器，0.1.4 真机、真实模型和缓存基准待验收。

2026-10-15。目标见 DESIGN.md，未完成范围见 PROGRESS.md。没有性能领先或最高缓存命中率的实测结论。

## 0.1.3 当前状态（优先于下面的历史记录）

已从手动浏览器接入 Agent：BrowserToolExecutor 使用 BrowserAutomation 在 Main dispatcher 运行真实会话 WebView；ToolRouter 固定每轮定义。提供 8 个浏览器工具，包括批量开页/读页、点击/填写/滚动和同步 JS JSON 返回。页面 ID 限定会话所有权，修改/脚本要求最新 URL 和导航 revision。文本/元素列表、脚本长度和返回值都有预算与显式截断；没有新增浏览器依赖或原生 JS Bridge。网页属于不可信观察数据。

用户可打开同一组页面观看；Agent lease 防止关闭观看页时中止正在执行的操作，操作结束且无人观看时暂停页面。全局 8 页、批量并发 2；取消会停止导航，超时返回结果待核实提示，不自动重放。同步 JS 运行超时不能保证终止已经进入 renderer 的 JavaScript，因此不宣称硬 CPU 限额或任意脚本可控；异步 Promise 明确拒绝。

工具账本查询拒绝已有 call_id；核心循环也拒绝同一轮跨响应重用 ID。运行记录在对话内折叠查看，只读最近 50 项和每项最多 8000 字预览。完成账本不等于业务成功，错误单独显示。多轮 usage 总计缺失字段保持未知；速度用整轮累计输出除以整轮耗时，缓存/上下文仍对应最近一次请求。

新界面按已确认方向采用单层 24dp 输入框、轻量三指标、输入框上方定位的小面板、无助手头像气泡、无抽屉新建按钮；右上保留新会话/文件/浏览器。主题完整映射浅深角色，预设/系统/自定义、HSV 与六位颜色值、柔和/鲜明/单色、纯黑和即时预览；无壁纸。旧偏好保留，默认青岚且无彩色功能图标。方案沿用 Compose，不复制 GPL 项目代码。

验证：核心 45 项；模拟器与 realme Android 16 ARM64 各 18 项普通设备测试，另进程恢复 1 项。Agent 浏览器为本地夹具上的确定性模型循环，不是付费供应商实测；真实 API、缓存/费用/网站成功率和后台持久执行未验收。Root 仍限 0.1.2 已验证的只读握手；没有新增 Root 执行工具。上传下载、异步脚本、跨站 iframe、完整 renderer 崩溃矩阵仍未完成。

BrowserRecoveryTest 必须独立进程执行；新建 Profile 的测试会留下待下一进程清理的空元数据，不能在同一批测试末尾把它们判成启动恢复失败。verify-emulator.ps1 显式分组并检查真实测试数量。

来源：[WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback))、[ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore)。

## 历史 0.1.2 实现说明


0.1.2 按新确认的 A 方案改为胶囊式输入框：灯泡思考按钮、模型家族图标与三项指标位于同一外框，单行胶囊、多行自然增高。右上菜单进入新会话、本会话文件与浏览器。抽屉底部为紧凑彩色横排入口；模型选择支持全部/官方/中转过滤，每行保留家族图标。外观支持五套预设、自定义强调色、浅色/深色/系统模式、彩色图标开关与系统动态配色；配置本地保存。字体大小跟随系统。当前会话文件入口不混入共享目录；会话文件浏览和预览也归属会话任务，删除时取消并等待。抽屉返回键先收起抽屉，不直接离开主界面。

会话搜索查询数据库而非仅过滤已加载的一页；百分号与下划线按文字匹配。初次导入附件创建会话时保留草稿，草稿支持配置变化恢复；主动切换会话时清空当前草稿。

支持官方/中转 HTTPS 地址、模型 ID、三种协议和认证方式，保存连接与加密密钥。抽屉保留打开、长按/菜单、重命名、置顶、多选、批量删除与分页；消息支持分页。共享空间浏览最多列举 200 项，文本预览有 64 KiB 上限。Skills 页脚明确打开只读共享资源浏览，安装和运行器尚未实现。

同一运行轮次固定连接与凭据，变更用于下一轮，不重新执行当前工具。配置切换按新范围、已知失效、支持追加更新和未知中转分别解释。token/s 是最后请求输出 tokens 除以整轮耗时的端到端估算，包含工具时间，不是纯生成速度。十家官方预设与稳定型号已按 2026-10-12 官方资料核查；账户实际权限与动态能力校验未完成。型号来源见 PROVIDERS.md，图标来源见 ASSETS.md。

## 手动浏览器

系统 WebView / AndroidX WebKit 1.17.1；当前是手动浏览，不是完整浏览器 Agent。HTTPS 导航、最多同时八页、页签切换、关闭/前进/后退/刷新已接入。按会话绑定 Profile；不支持 MULTI_PROFILE 或完整数据清理的 WebView 明确拒绝创建，要求更新系统 WebView，不混用默认空间。不提供特权 JS Bridge；文件/内容 URI 和混合明文内容禁用。浏览器退出暂停 WebView 计时器并停止加载，页面和 Profile 可在本进程保留；不宣称零内存开销。

代理入口位于浏览器内，默认跟随系统；支持直连和无认证 HTTP/HTTPS/SOCKS 代理。更改前需关闭所有页面。覆盖作用于 Aurora 的全部 WebView，不作用于模型 API 和其他 App。等待系统回调后才允许加载；超时/失败阻止继续打开页面，避免默默退回其他网络路径。系统 WebView 对本地地址的默认代理绕过规则仍存在。设备测试在本机开启 IPv4 回环代理并确认收到实际 HTTPS CONNECT 请求；没有把回调成功等同于路由成功。

删除先写入 SQLite deleting 标记，再停止所属页面。实际测试证明 Chromium 可在 WebView.destroy 后仍保留加载过的 Profile：直接 deleteProfile 会抛 in-use。实现先等待 WebStorageCompat.deleteBrowsingData 清理缓存/Cookie/JS 存储，再删除会话文件；空 Profile 元数据在下次进程启动删除。清理中断/超时保留删除标记和文件，不报告成功。普通测试验证两会话 Cookie/localStorage 隔离、仅清理所属数据、禁止重开删除中的空间，并通过独立进程验证孤立元数据恢复。

上传/下载、模型端 JS/DOM、批量页面工具、完整 renderer 退出及代理网络故障矩阵尚未完成。已处理 onRenderProcessGone 时销毁对应页并提示重开，但尚无真实 renderer 崩溃注入验收。浏览器代理不等于全机抓包；抓包按最新确认只做 Root 原生后端，逆向工具候选和待办见 REVERSE_ENGINEERING.md。

来源：[Profile 删除限制](https://developer.android.com/reference/androidx/webkit/ProfileStore)、[浏览数据清理](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)、[代理范围与回调](https://developer.android.com/reference/androidx/webkit/ProxyController)。

## Root 连接边界

已实现 libsu 6.0.0 RootService/AIDL 的只读握手、手动连接与断开、超时和服务退出状态。入口不自动请求 Root；服务校验调用者 UID，只返回自身 UID/PID、SDK、内核和 SELinux 状态。客户端只接受 UID 0 的握手。使用普通绑定而非常驻 daemon，断开后关闭缓存 shell。系统操作工具、执行器和 Root 真机连接尚未验收，不记为已完成。

专用 RootConnectionTest 被 RootRequired 标记，默认普通测试排除；必须在已授权设备显式执行且传 auroraRootTest=true。普通存储测试通过不代表 Root 测试通过。[libsu 原始 API 与生命周期](https://github.com/topjohnwu/libsu/blob/6.0.0/service/src/main/java/com/topjohnwu/superuser/ipc/RootService.java)。

## 网络与协议

共享 OkHttp 连接池；HTTPS；禁止 URL 内凭据/查询参数；禁用重定向与连接失败自动重试。取消关闭 Call，阻塞读和编码在 IO 线程。错误不回显响应正文、密钥或原始异常。SSE 单行与累计轮次有内存预算。内部明文回环接口仅供测试夹具。

三种协议保留完整原生输出、Chat reasoning_content、Claude 签名/内容块和 Responses opaque 项。只执行完整终态；重复调用 ID 拒绝；Anthropic 多个结果合并为一个 user turn。稳定系统/工具前缀指纹、连接、模型、协议、地址和密钥修订约束历史复用。工具开始/完成账本持久化；取消传播，不自动重放动作。

官方 OpenAI GPT-6 Astra/6.1 Sol 的 Chat 工具请求提前拒绝并提示 Responses；GPT-6 Luna 的 Chat 工具需要 none。未知中转不被假定与官方路由完全相同。思考等级按核实的具体型号与协议开放，未知名称只提供 auto。官方核实的 GPT-6 Responses 模型变更显式等级时追加 configuration_update，顶层 effort 保持最初值，历史更新保留原位置；切回 auto、认证/密钥/前缀变化或中断时重建范围，保留完整事实和工具账本。网关能力探测尚未完成。

来源：[追加更新与手动历史限制](https://developers.openai.com/api/docs/guides/reasoning)。本地测试验证不出现相邻更新、数据库重开后保留原始/有效 effort，以及两轮工具调用只执行一次；没有付费服务缓存命中实测。

来源：[OpenAI 当前协议限制](https://developers.openai.com/api/docs/guides/latest-model)、[Claude effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[OkHttp Call](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt)。

## 密钥、数据库与中断

Keystore 管理 AES-256-GCM 密钥。随机 IV、连接/修订认证附加数据，密文在 noBackupFilesDir 用 AtomicFile 发布；相同密钥保持修订。Credential 字符串脱敏，配置数据库不存密钥。云备份与 Android 设备迁移规则排除私有数据。

SQLite WAL 与外键；IO 线程操作，串行写事务。消息/协议记录分表追加，期望序号冲突拒绝。中断的 started 工具变 uncertain，已完成结果保留；切换/恢复时把结果作为执行事实供模型继续判断。突然进程终止仍可能丢失尚未保存的流式片段；后台持久服务未完成。

来源：[Keystore](https://developer.android.com/privacy-and-security/keystore)、[SQLite](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)、[AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)、[备份规则](https://developer.android.com/identity/data/autobackup)。

## 文件与空间

会话空间、只读共享资源、保留工作区；共享命名空间复用文件核心实例，保持同一写锁。导入副本每文件最多 64 MiB，不删除原件；不等同于原生图片/PDF 理解。

读取每批 64 项、4 并发、每片 64 KiB，保留顺序与错误。编辑每文件 2 MiB、每组 16 MiB，SHA-256 预检；禁止重复/父子目标、逃逸和链接。备份、日志、逐文件原子替换，未提交组恢复回滚，外部版本冲突停止并保留日志。目录元数据断电持久性需真机验证。

删除先停止并等待任务/导入，封闭新任务，再标记 deleting。遍历不跟随链接，检测当前命名空间子挂载并拒绝；删除中断可继续。测试验证共享链接不会删除共享文件。这不是任意 Root 程序的安全隔离；Root 挂载、浏览器和子进程尚未接入清理协调。

## 验收证据

- 核心 42 项测试，零失败：协议、取消、认证/路由、重定向、脱敏、GCM 篡改/身份替换、缓存范围/追加更新、文件冲突/恢复和任务清理。
- Android 16/API 36 ARM64 realme GT6 SE 真机、Android 17/API 37 模拟器各 8 项测试，零失败：数据库重开/分页、原生追加冲突、真实 Keystore、删除链接边界、删除恢复、工具中断账本、配置更新持久化和文件日志恢复/列举。
- 0.1.2 模拟器普通测试 14 项通过，另起进程的浏览器恢复检查 1 项通过；新 UI 尚未完成最终真机验收。历史真实手机结果仍为 8 项，不能用模拟器结果替代。
- debug 与 R8/resource-shrunk release 构建通过；release 未正式签名。
- lint 无错误；保留 MonochromeLauncherIcon 和一项 MiMo 复杂矢量路径警告。品牌路径未为消除警告而删减形状，也没有据此宣称渲染性能已优化。两次单色衍生图不合格，未采用。
- 界面已实际启动与检查。无真实 API 密钥，未验证真实服务计费、缓存命中或模型效果。
- Android 16 ARM64 真机开发 APK 安装、启动成功。未证明 Root、手机 APK/NDK 编译或性能领先。
- [GitHub Linux CI](https://github.com/Asagnc/Aurora/actions/runs/38269980475) 对 f70ac51 通过核心测试、debug/release 构建和 lint，并保存开发构建文件。
- 模拟器界面操作已验证创建、重命名、置顶、多选和两个会话批量删除；验收用会话随后清理。

## 构建与已处理陷阱

2026-10-15：0.1.1 在 API 37 模拟器检查实际主界面、会话抽屉和供应商面板；确认 Gemini 弧线路径恢复正确形状，键盘弹出后输入框、操作按钮与三项指标仍位于键盘上方。输入测试未发送模型请求。当前本地核心 42 项、模拟器 9 项通过，debug/release/lint 构建成功；lint 仍有单色启动图标和 MiMo 长路径两项警告。新版真机检查按用户要求放到最后。

固定 Gradle 9.8.1、AGP 9.4.1、Kotlin 2.4.21、JDK 25、SDK platforms;android-37.0、Build Tools 36.0.0。最低 SDK 36。

同一仓库避免同时运行两个配置缓存构建。Android 平台 AtomicFile 无 exists；用 openRead 支持备份恢复。测试进程启动失败曾产生零测试 XML 而构建返回成功；默认 verify-emulator.ps1 只操作明确指定的实际模拟器、只覆盖安装、不卸载，且强制至少九项通过。Gradle connectedDebugAndroidTest 曾在收尾卸载手机应用，不再作为连接日常手机时的默认工作流。资源目录移动曾导致 AGP 增量遗漏 launcher；清理 app 构建输出后完整构建通过。

真机暴露 Files.readString/writeString 在基础 Android 16 不存在，它们在 36.1 才加入；文件恢复与设备测试使用基础版本可用的字节 API。JVM 模块中的 API 调用不能仅靠新平台模拟器和 app lint 判定可用性，必须在最低支持平台执行核心路径。[官方 API 版本](https://developer.android.com/reference/java/nio/file/Files)。

其他精确依赖版本在构建文件内，来自 Google Maven/Maven Central/Gradle 官方接口/libsu Releases，排除 alpha、beta、RC、preview。构建组合验收不等于服务能力或真机性能验收。

2026-10-15 final device verification: Android 16 ARM64 realme GT6 SE passed 14 ordinary tests, one separate-process browser recovery test, and one explicitly authorized RootConnectionTest (read-only connect/disconnect/reconnect). FolkPatch authorization is configured inside its app. Packet capture, privileged operations and revocation remain unverified.

## 0.1.7 Root command interface (2026-10-17)

Supersedes the earlier read-only implementation status: RootService now exposes asynchronous start, completion callback, cancel-and-wait and close. Only an explicitly connected, UID-0/protocol-verified endpoint is included as command_execute_root at agent-run construction. Existing ordinary command execution remains separate; no retry/fallback after an uncertain result. UID checks cover each production method. Controlled supervisor artifacts use descriptor-based ownership restoration; arbitrary Root files/mounts remain outside automatic cleanup.

See ROOT_COMMANDS.md for lifecycle, wire budgets and limits. Seven ordinary-UID cross-process contract tests added; emulator 33 ordinary tests + 1 isolated browser recovery and core 45 pass. Debug and R8 release compile for ARM64/x86_64; lint has no errors and retains the existing MiMo VectorPath warning. Two RootRequired command tests compile but have not run. Android 16 Root execution/ownership/manager revocation still require hardware evidence; historical read-only FolkPatch evidence does not verify them.

## 0.1.8 capsule B and hardware validation (2026-10-18)

Supersedes pending hardware statements above: on Android16 ARM64 realme GT6SE, 33 ordinary tests, 1 isolated browser recovery and 4 explicitly authorized RootRequired tests pass. Existing FolkPatch grant remained available after install-r over0.1.3; app was not uninstalled/cleared. Root UID0, controlled artifact ownership/readability, direct cancellation/timeout, disconnect cleanup/reconnect are verified. Strong-killing the actual Root service, external manager revocation and arbitrary Root files/mounts remain unverified/unfinished.

The approved capsule B is implemented with thinking bulb, concrete model, inline context ring, external statistics and bounded in-place details. Execution history defaults collapsed, active titles align with actual tools and completion count is explicitly recent history. Existing themes preserved. Actual device/keyboard/narrow long-name views are separate from clearly labelled debug examples. Details and raw test reports: COMPOSER_B_2026-10-18.md and docs/evidence/0.1.8. Core45, emulator33+1 and debug/release/lint pass; no paid-provider or comparative performance evidence.

2026-10-20最终验证：core45、模拟器普通39、独立浏览器恢复1、Agent进程恢复prepare1/verify1通过；debug/AndroidTest/R8 release构建通过，lint0错误、1项既有MiMo矢量警告。序列化1.12.0升级后已复跑。证据：docs/evidence/0.1.9；新版真机后台/Root联合验收未做。
