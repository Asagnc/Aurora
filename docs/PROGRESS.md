# Aurora 接续记录

更新时间：2026-10-22。新对话先读 AGENTS.md、DESIGN.md、本文件和 IMPLEMENTATION.md，再检查源码与 Git 状态。

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

## 2026-10-22 guest ELF 执行资格（接续）

4a783c2完整CI39741811061成功。新增固定shell fixture：在已有只读chroot/会话绑定和cgroup监督内execve真实动态ELF，显式环境、工作目录、文件产物与非零退出码37复核；缺失动态加载器必须失败并回收scope。CI主机挂载组从5增至7项。使用主机shell和匹配库，不冒充Ubuntu26.10/Android实测，不开放任意Agent命令。

本地Debug/Release/lint通过（outputs/chroot-shell-build-20261022.txt）。Linux用例需CI Root执行，Windows不作通过声明。业务命令/PTY、scope与版本运行租约映射仍待实现，executionReady=false；没有新手机测试。此变更不发布新App版本号。

## 0.1.26 后端环境代发布（2026-10-22，优先接续）

已实现Root/普通独立代，Root原始UID/GID/权限预检与批量恢复、独立ID/旧manifest兼容、元数据/目录同步与事务发布。Root删除也走票据/取消join；先确认旧文件工作停止再恢复版本操作，Root缺失保留版本与恢复记录。复用SQLite v5租约保护新代，未接Root业务命令/PTY的实际运行租约。见LINUX_ROOT_GENERATIONS.md。

Agent install支持backend=auto/root/proot；显式Root连接默认只准备Root代，UI按需另备普通代/空间提示/后端列表；普通PRoot probe拒绝Root属主代。协议2须更新Root服务，既有协议1硬链接兼容。executionReady=false，未跑真实Ubuntu guest。

核心143（Windows136通过/7POSIX跳过）；Android新增普通5项只编译，普通门槛78，Root镜像专项验证已更新但未运行。本轮证据见evidence/0.1.26，无新真机结果。上一版4411fe7完整[CI39736472325](https://github.com/Asagnc/Aurora/actions/runs/39736472325)通过。下一步真实guest/命令/PTY与scope—版本租约/Root可写会话回收闭环，再约真机；不重复审批后端属主方案。

## 0.1.25 chroot挂载资格（2026-10-22，优先接续）

用户已确认LINUX_OWNERSHIP_PROPOSAL.md：Root用户默认只准备原始Linux属主Root代；PRoot的App UID代按需另建，各代会话间共享，不逐会话复制、不强依赖OverlayFS。两种同存约多108MiB基础文件加块开销，Root清理需Root，不能重复询问此选择。

已接固定C chroot fixture：FD克隆/属性/挂接、只读根、可写HOME/tmp/project、只读proc/dev、关闭host目录FD；复用cgroup监督与恢复。新增Linux主机5项，旧13项继续；没有运行Ubuntu ELF、不接任意命令或变更现有镜像属主。见LINUX_CHROOT_MOUNTS.md及evidence/0.1.25。Android普通门槛73，无新增真机证据，executionReady=false。

上一版5ff4b8a完整[CI39734462398](https://github.com/Asagnc/Aurora/actions/runs/39734462398)成功。下一步按已确认方案实现Root代属主发布/Root清理和空间展示、生产租约与真实guest执行；PRoot guest仍未实跑。手机不必一直连接。

## 0.1.24 scope恢复（2026-10-22，优先接续）

Root专属持久记录、boot/PID启动时间/pidfd/父与scope inode身份验证、损坏拒绝、协调进程兜底和独立恢复已接；工作进程父死亡保护补齐入组前竞态。Agent新增recover_root，probe先恢复，活跃或不明确记录不干扰。见LINUX_SCOPE_RECOVERY.md。新增主机7项故障，原6项继续；Android现有Root用例补持久恢复，仅编译，普通门槛73。

上一版8fe33c4完整[CI39732417088](https://github.com/Asagnc/Aurora/actions/runs/39732417088)通过。本轮证据见evidence/0.1.24。下一步Android强杀/撤权资格及chroot属主/挂载/命令/PTY和业务租约，PRoot guest仍未实跑。executionReady=false；无新增手机验收，不重复确认已批准架构。

## 0.1.23 cgroup监督资格（2026-10-22，优先接续）

新增固定原生任务scope资格：新建专属组、自入组、私有mount、pidfd、subreaper、独立会话监督、kill/空事件/reap/remove。Agent probe_root接此资格，不要求PID空间，executionReady=false。见LINUX_SCOPE_IMPLEMENTATION.md。新增主机6项CI和Android普通1+Root1仅编译，普通门槛73。

上一版fd0c847完整[CI39650196565](https://github.com/Asagnc/Aurora/actions/runs/39650196565)已通过。当前本地/CI证据见evidence/0.1.23；未连手机。完整chroot/PTY/租约、监督自身强杀或Root撤权后的持久恢复未接，不把固定资格当完整运行时。下一步继续已批准cgroup方案的恢复闭环。

## 0.1.22 Root 辅助安装（2026-10-22，优先接续）

Root 批量硬链接安装已接：App 解压普通文件保留标签，Root 一次请求复核官方镜像并补链接，App 复核后原子发布。增加服务进程身份票据、取消 join、失联暂存保留及显式 Root 恢复；不盲删、不重放。见 ROOT_LINUX_INSTALL.md。普通硬链接不支持时仍在下载前失败。

核心136项；新增Android普通5项和Root镜像安装1项，普通门槛72。本轮没有新增手机运行证据，测试包编译不等于真机通过。完整构建与校验结果见 evidence/0.1.22。Root chroot、任务cgroup原生监督/事件恢复、Linux命令/PTY仍待接，PRoot guest仍待实跑，executionReady=false。下一步继续已批准 LINUX_CGROUP_PROPOSAL.md，不需重复确认。手机不必一直连接。

## 0.1.21 Linux 真机资格与修订（2026-10-22，优先接续）

保留数据更新真机，最终普通3+Root3共6项通过。详见DEVICE_LINUX_2026-10-22.md及evidence/0.1.21。发现并修复Files.getFileStore在Android抛SecurityException（改StatFs）；普通硬链接被拒，增加下载前能力预检和结构化失败/暂存清理，不复制或link2symlink冒充。Root链接实际成功且保持App属主，但Root安装器仍未接。

**用户已确认新修订**：当前内核没有pid空间入口，Root授权UID0有效；不再以PID空间为Root Linux硬前置。改任务专属cgroup v2+私有挂载+subreaper/pidfd/事件，见LINUX_CGROUP_PROPOSAL.md，不需重复确认。真机已验证scope创建/子进程加入/仅本scope kill/空状态/删除，以及私有挂载不改变调用者空间。生产probe_root改只查私有挂载，executionReady仍false；cgroup原生监督和事件/恢复、Root安装、chroot/命令/PTY仍待接。PRoot guest没有跑到，不把--version当资格完成。

eb52443完整[CI39643726174](https://github.com/Asagnc/Aurora/actions/runs/39643726174)已成功。本版本地Debug/R8Release/testAPK/lint通过；实际失败及6项通过原始证据均保留。手机Aurora保留，测试辅助包清理；不改全局SELinux/调度/Root策略，不装新内核或模拟器。

## 0.1.20 Root 命名空间资格（2026-10-22）

用户已确认Root Linux加私有PID空间，与私有挂载配合；能力不足禁用此Linux后端，原生Root命令保留。已接固定原生探测与Agent probe_root、现有Root日志/限时/CPU租约；没有挂Ubuntu或chroot。Agent PRoot固定探测接真实环境版本租约并处理取消释放，完整命令/PTY租约仍待接。见LINUX_ROOT_LIFECYCLE.md、LINUX_IMPLEMENTATION.md。

增加Linux主机Root生命周期4项，Android普通拒绝/Root专项/显式下载运行各1项。普通门槛67；新增设备用例只编译，不把主机namespace结果当Android通过。上一版590e2ed完整[CI39640576221](https://github.com/Asagnc/Aurora/actions/runs/39640576221)已通过，含源码打包和双ABI ELF验证。当前阶段请求小范围手机测试，最终完整验收仍放最后；截至实现时ADB无设备，不自动重装模拟器或Studio。

## 0.1.19 PRoot 打包与固定探测（2026-10-22）

已将批准的PRoot/talloc/shmem从固定摘要源码编进ARM64/x86_64 APK，加入独立loader和共享talloc，修正当前NDK缺失头、Release入口回收及断言移除后的UB。libandroid-shmem固定目录改显式PROOT_TMP_DIR并避免不可写路径无限重试。原生管理页及Agent增加固定离线probe，但executionReady仍false，任意Linux命令、PTY和Root chroot未接。见LINUX_IMPLEMENTATION.md及LINUX_NATIVE_BUILD.md。

本地Debug/R8Release/testAPK/lint、双ABI APK ELF/依赖/16KiB检查、独立ARM64源码构建通过；核心仍129，新增Android冒烟1项只编译，普通门槛66。CI增加打包校验和对应源码ZIP交付。未新增设备、Root、实际guest或性能证据。用户同意以后底座闭环完成做小范围真机检查，最后再完整验收，不需每次小改连手机。

## Linux 执行层准备修正（2026-10-22）

继续已批准架构，补齐rootfs数值UID/GID计划和新安装entries.json原子发布，提取时复核属主/权限/精确TAR类型，保持普通解包不chown。旧安装缺失元数据须在后续执行资格阶段补全，不自动视作可执行。新增3项核心用例合计129；本机127通过/2项POSIX跳过，官方ARM64样本扫描通过，Debug/lint通过。详见LINUX_IMPLEMENTATION.md。PRoot构建、Root挂载/进程监督与命令/PTY租约仍待继续，未新增手机验收或性能结论。

## 0.1.18 Linux 基础环境安装（2026-10-22）

用户已确认采用推荐方案，不需再次确认Ubuntu/PRoot/chroot架构。已接官方签名固定摘要、rootfs tar规则与Linux链接/权限、ELF结构/解释器验证、SQLite v5安装恢复/环境代/活动租约元数据、共享目录安装器、Agent环境管理和原生管理页。详见 [LINUX_IMPLEMENTATION.md](LINUX_IMPLEMENTATION.md)。当前尚无Linux执行工具，executionReady明确false；PRoot依赖构建与Root挂载/执行/租约生命周期仍待接入，不把安装基础包当成完成Linux后端。

新增核心11项，共126；Windows实际125通过/1项POSIX跳过，官方ARM64样本SHA与扫描通过。CI新增官方签名+真实Linux提取/ELF验证；新增2项Android迁移/租约测试仅编译，普通门槛65。完整本地构建证据与CI结果随提交记录。本轮无设备/Root/付费模型/手机性能验收；当前基包压缩约35MiB、文件内容113253544字节，不含编译器/逆向工具和文件系统额外空间。

## Linux 底座研究记录（已确认，2026-10-22）

0.1.17最终提交026a2dc完整 [CI39611202577](https://github.com/Asagnc/Aurora/actions/runs/39611202577) 已通过：核心115、Linux主机PTY8/源码12、Debug/R8 Release/testAPK/lint。没有新增设备验收。

用户继续后，已重新查Ubuntu26.10官方Base/26.04.1 LTS、Debian13.7、Alpine3.24.2、Termux PRoot5.1.107.98、bxroot0.2.4、远程Eta/DSHA源码及Android/NDK/挂载限制。推荐方案见 [LINUX_PROPOSAL.md](LINUX_PROPOSAL.md)：Ubuntu26.10同架构、Root私有挂载命名空间chroot优先、普通按需PRoot、共享环境代与缓存/会话HOME分离。用户已批准，实施状态以上述0.1.18和LINUX_IMPLEMENTATION.md为准。没有降低target、全局挂载/SELinux修改、重新安装Studio/模拟器或付费模型调用。

## 0.1.17 Skills 来源与文件发现（2026-10-21）

0.1.16 最终实现95f435f完整 [CI39608255424](https://github.com/Asagnc/Aurora/actions/runs/39608255424) 已通过，核心101、Linux主机PTY8/源码12与Android构建/lint。

本版增加显式GitHub ref及子目录选择：含斜杠分支可由UI或Agent传完整ref，一次提交解析固定SHA，不逐个猜测分支；兼容旧来源JSON和旧ID，显式ref有独立命名空间。修复名为archive/releases的仓库或子目录被误判下载；来源标签区分正式发布/分支快照/指定引用。普通仓库默认仍跟进最新正式release。无显式ref的歧义tree URL不自动消歧，需填写来源选项。

skills_read不填path时支持资源搜索/分页，能遍历超过128项文件；UI增加工作流文件页与UTF-8分段预览。更新/回滚后详情跟随实际当前版本，当前项、启停状态、正文同步刷新。固定运行中的默认版本策略保持原契约。

新增14项核心测试（GitHub解析5、提交解析1、文件分页3、下载重定向/预算/取消5），合计115；完整本地Android构建/R8/测试APK/lint证据见 [evidence/0.1.17](evidence/0.1.17/README.md)。没有新依赖或数据库迁移。UI、真实SAF/外网下载及新增Android用例仍待最终真机验收；没有付费模型实测或最优性能结论。私有来源/签名、Linux/编译/逆向/Root抓包执行工具包仍未实现。

## 0.1.16 Skills 故障与取消边界（2026-10-21）

上一版 112e496 完整 [CI39606872871](https://github.com/Asagnc/Aurora/actions/runs/39606872871) 已通过，包含核心87、Linux主机PTY8/源码12校验和Android构建/lint。

本版将已有发布/恢复流程提到核心层并由实际安装器复用，新增14项故障/取消/UTF-8测试，核心合计101项通过。覆盖部分目录已移动而索引未提交、已索引版本保留、批量第二包失败、恢复失败保留日志并可重试、清理失败不覆盖原错误、发布前/发布中取消、忽略线程中断的流通过关闭结束、打开阶段取消、已有目标不覆盖和中文/emoji跨64KiB读取。取消后的已提交安装照样发布目录刷新；更新返回真实启停状态。SAF打开使用CancellationSignal，阻塞文件检查移到IO线程。没有新增库/守护进程/底层选型。

本地 core/test、Debug、R8 Release、AndroidTest APK、lintDebug 全通过，0错误/既有MiMo矢量警告1；证据见 [evidence/0.1.16](evidence/0.1.16/README.md)。Android迁移/界面/SAF真实提供器/Root/耗电仍待集中真机验收；主机故障夹具不等同真实断电或设备强杀。下一步仍需补GitHub歧义引用、私有来源/签名、更多安装/导入设备场景；Linux/编译/逆向/Root抓包执行工具包尚未实现。

## 0.1.15 Skills（2026-10-21）

用户已批准方案并要求最新稳定版本。已实现标准 SKILL.md/YAML、ZIP/HTTPS/GitHub 安装、共享内容哈希库、SQLite v4 索引/恢复/会话引用、每轮版本固定、五个 Agent 工具和原生管理页（搜索/启停/更新/版本选择/移除/清理）。目录变化追加原生历史，不改旧前缀；更新需显式操作或 Agent 调用，不存在隐藏后台自动下载。详见 [SKILLS_IMPLEMENTATION.md](SKILLS_IMPLEMENTATION.md)。依赖已核实稳定版并升级传递依赖；只支持 Stored/Deflate 的边界配套处理 R8 可选解码器引用。

新增核心 15 项，合计 87；本地核心测试、Debug/R8 Release/AndroidTest APK/lint 验证记录保存在工作区 outputs/skills-verified-build-20261021.txt。新增两项 Android 数据迁移/引用专项只编译，未执行；本版没有设备、真实模型、耗电或性能领先结论。最终真机集中验收安排不变。待后续重点：安装崩溃注入/取消矩阵、私有来源/签名、含斜杠引用消歧、大目录和界面实机审查；Linux/编译/逆向/Root 抓包执行工具包仍未实现。

## 0.1.14 原生交互终端（2026-10-21）

用户已认可推荐方案，强调Agent效率/速度/质量与美观。实现普通/Root原生PTY、六个Agent工具、按需增量文本/屏幕/Base64、后台跨回复会话、Root Binder控制+Unix socket FD数据、截止/取消/同SID作业清理、会话记录/中断恢复、原生Canvas查看与IME、滚动/字号/复制/接手。见[INTERACTIVE_TERMINALS.md](INTERACTIVE_TERMINALS.md)。Termux稳定v0.118.3固定提交，只导入12个终端Java组件，来源哈希/Apache/MIT声明保留；没有整个Termux/第三方App/新Linux发行版依赖。

最终实现提交0be35fc已通过完整[CI39597157476](https://github.com/Asagnc/Aurora/actions/runs/39597157476)：核心72项、Linux主机8项PTY生命周期、12源码哈希校验、Debug/R8Release/testAPK/lint全部通过。新增10项普通Android用例及2项显式Root用例只编译，ordinary gate61，设备尚未连接。复查另补启动前空间已关闭的结构化失败、防止后台launch异常崩溃、主线程取消不做阻塞FD关闭、持久状态失败明示。主机不是Android/Root证据，无真实模型或性能基准。见[evidence/0.1.14](evidence/0.1.14/README.md)。下一步Skills版本化工具包，新底层继续研究确认。不擅自恢复模拟器或要求现在接手机。

## PTY 研究记录（已确认，2026-10-21）

已研究 Termux 独立原生组件、xterm.js/WebView、libghostty-vt 与自写模拟器，推荐稳定 Termux 组件 + Aurora 自有普通/Root PTY 会话管理；见 [TERMINAL_PROPOSAL.md](TERMINAL_PROPOSAL.md)。最新非预发布实查为 v0.118.3。Ghostty 当前嵌入 API 尚变动且未单独打版本；Termux 上游主线程解析不能直接照搬。用户随后已认可并进入0.1.14实现，不再重复请求此架构许可。

## 0.1.13 事件日志（2026-10-21）

用户已认可FileObserver事件驱动日志方案。实现复用现有start ID固定产物目录，无新增Root回调；账本持久映射，SQLite1/2→3新增artifact_directory/presentation；Root命令握手与请求协议2，身份协议1不变。工具详情可看实时stdout/stderr、复制、跟随/暂停，离页/后台/收起/删除释放监听。有效有界JSON投影保留大输出错误/退出码/真实丢弃预算，完整结果未修改。详见[LIVE_COMMAND_LOGS.md](LIVE_COMMAND_LOGS.md)。

核心67通过（日志新增6、投影新增2），debug/R8release/AndroidTest/lint通过；新增5个设备用例只编译，ordinary gate为51。图文0.1.12此前已推送bd7dd16且CI通过。本版无连接设备，最新Root2、UI、SQLite迁移、inotify事件尚未实际跑；不要把“编译”写成验收。继续PTY/持久终端与Skills，新选型先研究确认；不擅自恢复已清理的模拟器。

## 0.1.12 图文消息与图片产物（2026-10-21）

用户已确认Compose Markdown+Coil3+独立会话产物组合，要求按实际最适合选择。实现GFM/代码复制、后台增量流式、长回复阅读页、images_publish、会话原图快照/清单、SHA校验、缩略图、大图缩放、SAF保存和单URI分享。删除取消等待图片任务并清理对应内存缓存；不重复加入模型图像输入。实现细节和限制见[RICH_CONTENT_PLAN.md](RICH_CONTENT_PLAN.md)。库固定0.46.0/3.6.3；Android16最低API36保持，不使用Files.writeString的36.1重载。

核心59项通过，debug/R8release/AndroidTest构建及lint通过（0错误、既有MiMo警告1）；新增5项设备用例只编译，当前ADB无设备，未安装/清数据/重下载模拟器。真实模型/远程图片站点、交互视觉与内存压力未验收。继续持续日志/PTY与Skills；新架构仍研究后确认，既有架构内直接实现。未把候选库当性能领先证据。

## 0.1.11 阅读/详情基础与图文选型（2026-10-21）

用户认可先做阅读、任务详情和持续日志；补充要求后续Agent能发图片。先实现不依赖新选型的消息复制、滚动跟随/暂停/回到最新、稳定流式行、命令结构化输出与产物目录入口；错误字段顺序不再影响状态判断。正文仍为纯文本，Markdown/图片未加入依赖；完整比较和推荐组合待确认，见[RICH_CONTENT_PLAN.md](RICH_CONTENT_PLAN.md)。PTY/持续日志仍待做。当前没有连接设备，本版不得沿用0.1.10真机结果。

本机清理后Android Studio与模拟器/系统镜像已移除，JDK独立位于本任务work/jdk-25，SDK/NDK/CMake/ADB及Gradle缓存保留。构建使用该JDK；需要设备验收时优先沿用用户最终集中真机安排，不擅自重新下载大型模拟器。

## 0.1.10 真机与有界命令唤醒（2026-10-21）

保留数据覆盖安装真机0.1.10（code11）。最终普通41、独立浏览器恢复1、Root4、亮屏后台/熄屏Root2通过，核心45实际重跑，debug/test/R8 release/lint通过（0错误、既有1警告）。报告见[DEVICE_ACCEPTANCE_2026-10-21.md](DEVICE_ACCEPTANCE_2026-10-21.md)，证据evidence/0.1.10。主应用/数据保留，辅助test包已移除，DSHA测试进程已停止。

用户确认普通/Root命令各自持有带超时的CPU局部唤醒lease，结束/错误/取消后释放，不覆盖模型等待或整轮Agent。Root由应用侧持有，不重复归到UID0，见COMMAND_POWER.md。接入锁后曾仍超时，用户再次处理冻结豁免后通过；不能宣称锁单独解决或无耗电代价。任务后实际Aurora锁数量0。

DSHA已在ARM64手机完成部署，实际进入聊天、抽屉、四类Web设置、插件、PTY/简易终端、Skills、备份和文件；简易终端uname -m返回aarch64。PTY自动输入未获回显，不计执行通过；第三方插件/备份恢复/虚拟屏特权执行未测。本轮不再全部停留于x86受阻状态。

模拟器本轮快照卡住/冷启动退出，用户选择“先按真机结果继续”，不要继续反复调试或把旧模拟器结果冒充新版通过。用户明确本轮先本地验收，不调用付费模型。新真机进程死亡恢复/长期Doze/外部Root撤权仍待专项。

## 竞品实际页面审计（2026-10-21）

本轮按用户要求先暂停功能扩展，实际检查 Eta v3.3.1、DSHA 官方 APK v0.2.0-rc2、电脑本地 DSH0.2.0-rc.2 Web，以及 Aurora0.1.9 页面。覆盖矩阵、截图和待补项见 [UI_AUDIT_2026-10-21.md](UI_AUDIT_2026-10-21.md)。DSHA ARM64 Linux 在 x86 模拟器 PROOT_COMPAT 受阻；用户已明确选择“先继续，最后补齐 DSHA”，不再反复要求现在接手机。桌面 Web 不能当手机聊天布局验收，工具列表不等于工具执行验证。

结论：保留已认可胶囊B；优先补正文阅读/消息操作/任务轨迹与产物的连贯体验，持续日志/PTY与Skills工具包仍是执行底座重点。Eta 学习权限/能力状态，DSH 学习任务工作台和结果取用；不复制整套庞大运行时，也不承诺原生即性能领先。新架构仍需研究确认。

实际发现并纠正工具能力页旧文案：0.1.9后台Agent前台服务已接入，交互式终端/持续日志未接入，分开显示。没有新增执行能力。当前真实模型缓存、手机后台/Root组合与受阻DSHA页面仍待集中验收。

## 0.1.9 后台任务（2026-10-20）

用户已认可原生 specialUse 前台服务架构。实现应用内 ChatController/AgentExecution 与界面生命周期分离、通知回到会话/停止、单运行入口、SQLite schema2 agent_runs 与中断恢复；不自动重放工具。详情见 BACKGROUND_TASKS.md。本轮模拟器专项和完整验证记录于 docs/evidence/0.1.9；新版真机后台与Root联合测试待后续统一验收，不反复要求连接。

后续：PTY/持续输出读取与后台日志，Skills版本化安装，再推进Linux/逆向/抓包/构建链。已有前台服务不等于持久终端或系统强杀后继续原进程。新的架构选择仍需研究确认。

## 最新确认与验收 · 0.1.8

用户认可新对话方向，最终选择输入区对照右侧B，并说“你可以测试了”。见 COMPOSER_B_2026-10-18.md 和两份参考图；覆盖旧A/框内三指标要求。单胶囊、灯泡、具体型号、控制行上下文圆环，框外速度/最近输入/缓存；默认折叠执行轨迹。布局与控件已落地，现有配色偏好保留；不把截图示例指标当成实测。

2026-10-18 真我GT6SE / Android16 / ARM64 / FolkPatch：从0.1.3保留数据覆盖到0.1.8，没有卸载。普通33项、独立恢复1项、显式Root专项4项通过；包含真实UID0、监督产物app归属/读取、取消/超时/断开清理与重连。普通测试包含真正ARM64原生程序和跨进程生命周期，不再只是编译验证。模拟器普通33+独立1项通过，core45、debug/release/lint通过。设备测试报告位于docs/evidence/0.1.8。未做外部管理器撤权或Root服务强杀专项，也没有真实模型/计费/缓存/性能基准。

后续优先级仍是后台任务/PTY、Skills版本化工具包、Root文件与挂载清理和系统API；随后抓包/发包/逆向、手机编译链。新架构超出已确认设计时先研究确认。DSHA比较对象已明确为DSH-APP/DSHA，不是随意某个同名手机项目；不能靠原生Compose身份宣称快过其WebUI。

## 产品与最新确认

Aurora 是以 Agent 完成任务为中心的 Android 16+ harness，包名 io.github.asagnc.aurora。用户主要对话、观看进度与结果；已有工具应可由模型直接调用。Kotlin/Compose、SQLite WAL、libsu RootService/Binder、系统 WebView 与三类空间已确认。不要要求 Shizuku；抓包只做 Root，不做 VPN。新底层选型需研究并确认。性能和缓存目标必须有实测，不能承诺全网站可用、100% 命中或领先 Eta。

已确认新界面：docs/assets/approved-chat-refinement.png，保留原 Aurora 图标，使用单层圆角输入框，2026-10-16 用户新参考覆盖旧要求：三指标移到输入框外底部一行，详情仍在输入框上方就地展开。移除抽屉顶部新建按钮，保留右上新会话入口。全新安装不播种会话，不按默认标题清理用户现有会话。外观采用完整浅/深配色、模式缩略图、配色预览、预设/系统/自定义和柔和/鲜明/单色风格；仅配色，不做壁纸。

## 已实现（0.1.3）

- 三协议流式网络、协议原生历史、工具循环、完整终态校验、取消、工具账本与中断恢复；重复调用 ID 在一轮内和数据库账本中均拒绝重放。
- 十家 BYOK 官方预设与自定义中转，密钥 Keystore AES-GCM 保存；型号目录核查日仍为 2026-10-12，尚无账户动态能力校验。
- SQLite WAL 会话与原生历史追加保存、搜索、置顶、重命名、多选删除、分页。
- 会话/共享只读资源/共享写工作区，批量文件列举/读取/版本校验编辑、导入与恢复；删除协调任务、文件和浏览器数据，不跟随外部链接。
- Agent 浏览器工具：list/open/read/action/evaluate/close、批量 open/read；复用用户可观看的会话页面。最多 8 个活页，批量两个操作并发。页面读写由主线程协同，导航版本和 URL 校验、取消、输出截断、独立 Profile、全浏览器代理与清理恢复。
- DOM 包含文本、CSS 选择器和截断标记；点击/填写/滚动、同步 JS 返回 JSON。网页内容标为不可信；无原生 JS Bridge。异步 Promise、上传下载、跨站 iframe、任意网站成功率和后台常驻仍未验收或未实现。
- 新输入框和就地指标面板、纯文本助手回复、折叠执行记录；记录界面仅取最近 50 项及有界预览，避免每次执行加载完整历史结果。
- 7 套配色、系统动态配色、自定义 HSV/颜色值、纯黑选项与配色风格；完整角色映射与记忆化，旧偏好保留，无壁纸依赖。
- 多次模型调用的输出用量累计后计算整轮端到端估算速度；缓存和上下文仍明确是最近请求。缺失计数不作为 0 相加。
- Root 只读连接/断开/重连在 FolkPatch 应用内授权后已于 0.1.2 真机验证；0.1.3 无新增特权操作。

## 当前验证与环境

0.1.3 核心 45 项通过；API 37 模拟器和 API 36 ARM64 realme GT6 SE 各通过 18 项普通设备测试与独立进程恢复 1 项。浏览器 Agent 使用本地 HTTPS 拦截夹具和确定性模型测试，不消耗付费 API。验证开页、填写、点击、实际 DOM 读回、跨会话拒绝、过期操作、脚本错误、输出预算、批量顺序/部分失败与取消；主题检查主要文本角色对比度。

本地 debug/release/lint 构建已通过；release 未正式签名。最后资源清理后 debug/release/lint 再次通过（0 错误、2 项既有警告）；补充账本重复 ID 断言后真机持久化 9 项复测通过。真机已覆盖更新保留数据；最后仅移除 io.github.asagnc.aurora.test 辅助包，绝不卸载主应用。

JDK25（Android Studio/jbr）、Gradle9.8.1、AGP9.4.1、Kotlin2.4.21、compile/target37、min36；具体稳定依赖固定在构建文件。GRADLE_USER_HOME 为 ../gradle-cache。真机 ADB 6c72c941；模拟器 emulator-5554。

默认 scripts/verify-emulator.ps1 只接受模拟器，要求至少 18 项普通测试，再单独进程运行 BrowserRecoveryTest。该恢复测试不能与会创建新 Profile 的用例放在同一进程末尾运行。不要使用 connectedDebugAndroidTest 对日常真机测试，Gradle 收尾可能卸载主应用；真机用显式序列号 install -r 和 am instrument，排除 RootRequired，普通组排除 BrowserRecoveryTest。

## 下一步（未完成）

1. 真实模型和网关的工具任务验收、能力探测、最新型号刷新；不能用本地夹具替代真实供应商计费和缓存结果。
2. 缓存按协议、精确模型、实际供应商/网关、账户路由与参数匹配；目前仅基础前缀/范围/Claude 断点，最新各家缓存模式和期限策略尚未全面适配，未做到所有模型最优。
3. Root 命令已在 0.1.7 接入，真机执行/归属/撤权仍待测；系统 API、挂载清理、原生抓包/发包、逆向工具仍待做（见 ROOT_COMMANDS.md、REVERSE_ENGINEERING.md）。非 Root 不增加高开销替代抓包路径。
4. Skills 安装/运行、校验回滚；PRoot/chroot 和手机 APK/NDK 编译。当前 Skills 入口仍是只读资源目录。
5. 后台任务服务、原生图片/PDF 理解、浏览器上传下载/异步脚本/renderer 故障注入、任务成功率/延迟/内存/耗电基准。
6. 正式签名与合格单色启动图标。MiMo 长矢量警告保留，不为消警告改坏品牌形状。

GitHub 使用本机 gh 登录，不使用聊天中曾暴露的 PAT，不记录或上传密钥。继续把实现、验收与未完成项分别写清。
## 0.1.4 界面修订（2026-10-16）

按最新确认将指标放框外，压缩输入区，思考图标与模型型号/等级并列。无连接模型页使用紧凑添加入口；供应商编辑可返回列表。工具记录使用动作名称、真实状态和有界格式化预览；持久化后直接推送最近 50 项事件，不逐步统计完整历史。步骤为本轮工具调用数，不等于模型请求数。对比范围与待办见 UI_AUDIT_2026-10-16.md；占位能力、Markdown 和后台执行仍需继续实现。

## 0.1.5 已确认界面落地（2026-10-16）

按用户认可的交互预览落地，思考改为灯泡；去除内嵌模型边框、左对齐空白页、步骤线执行详情、原位展开指标与底部会话菜单，保留现有配色偏好。使用 debug 专用、无模型/存储操作的示例页面验收对话和运行状态，不把示例当成能力实测。对比度测试扩展至输入区背景；极端自定义鲜明配色修复后模拟器 18 项及独立恢复 1 项通过。用户要求先完成模拟器，本轮真机待后续统一验收。详见 CHAT_REFINEMENT_2026-10-16.md。

## 0.1.6 原生命令（2026-10-16）

新增 command_execute，由 APK 内原生监督程序执行普通 Android UID 命令；参数原样、分离标准输出/错误、每路 8 MiB 产物上限、有界文字预览、退出码/超时/取消、同组子进程清理与父退出处理。接入 AgentLoop、工具账本和会话租约；未知结果不记为成功。执行环境/工具能力页显示真实能力，不再复用关于占位。详见 NATIVE_COMMANDS.md；全量待办与优先级见 FEATURE_STATUS.md。

NDK 30.0.16248370、SDK CMake 4.1.2；arm64-v8a/x86_64、16 KiB ELF 对齐。使用 APK nativeLibraryDir 打包程序，普通命令不走 PRoot，不从可写目录运行下载二进制。Gradle Variant API 生成 native 资源；SDK 安装脚本使用官方 CLI 支持的斜杠路径，避免 Windows 兼容脚本拆开分号参数。

新增 8 项设备用例（含确定性模型接入），核心 45 项、模拟器普通 26 项及独立恢复 1 项已通过；本轮 ARM64 只编译，真机后续统一验收。RootService 仍只读握手；没有完成 Root 命令、持久后台任务、PTY、Linux/Skills/逆向插件或手机构建链。独立守护化/变更 UID 不属于普通进程组的完整约束，不能称为安全沙箱。

## 0.1.7 Root 命令接口（2026-10-17）

新增 command_execute_root，连接 Root 并验证 UID 0/协议后才在新任务中暴露；普通 command_execute 保留原权限。Binder 异步启动/单向结果回调、调用 UID 校验、最多两任务、有界请求与预览；取消等待清理后返回，断开停止接单并取消/等待，再解绑；客户端/服务 Binder 死亡处理，不自动重连或重放。工具定义按每轮固定。详见 ROOT_COMMANDS.md。

Root 监督产物按打开的目录/文件描述符恢复为 app 所有；不递归 chown 任意命令文件/挂载。任意 Root 命令创建的目录、挂载和独立守护进程仍需专门清理机制。修复原生监督器创建线程寿命：创建和阻塞等待留在同一 OS 线程，取消完成收尾后才归还线程池，避免 PDEATHSIG 在线程回收时误触发。普通命令增加环境总字节预算。

核心 45 项、模拟器普通 33 项和独立恢复 1 项通过；新增 7 项以普通 UID 的 debug 独立进程测试真实 Binder、取消、关闭、服务被杀和原生父退出清理。debug/release/lint 通过，保留现有 MiMo VectorPath 警告。两个新 RootRequired 设备用例已写但未运行；模拟器 user 镜像无 su，不能据此宣称 Root 执行/产物归属/管理器撤权已验收。真机继续按用户要求最后统一测试，保留安装与数据，不主动反复索要 USB。

接续：先完善任务生命周期/Root 文件与挂载清理、Skills 版本化基础；新的后台服务/终端架构若超出已确认设计，检索后向用户确认。PRoot/chroot、抓包/发包/逆向插件、手机构建链、真实模型缓存基准仍未完成。外部管理器撤权不保证杀掉已存在 UID 0 服务，目前只观察服务死亡/断连与显式断开；不要写成撤权已全面处理。

2026-10-20最终验证：core45、模拟器普通39、独立浏览器恢复1、Agent进程恢复prepare1/verify1通过；debug/AndroidTest/R8 release构建通过，lint0错误、1项既有MiMo矢量警告。序列化1.12.0升级后已复跑。证据：docs/evidence/0.1.9；新版真机后台/Root联合验收未做。
