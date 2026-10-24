## 2026-10-24 受管Binder、活动账本与Agent恢复

已接派发/恢复Binder、PFD所有权、进程屏障后的三类精确证明清账、Agent status/recover和删除前恢复。guard可信摘要来自最终APK载荷，双ABI摘要/ELF审计通过。核心255项243通过/12跳过，Debug/Release/testAPK/lint成功；新增6项Android用例仅编译。Frida worker/桥仍未打包，不显示注入ready，见ROOT_MANAGED_BINDER.md与local-managed-binder-passed.txt。

发布资格增加最终链接map，ARM64实际worker保留1,894归档成员（含调试段来源），继续嵌入资源与源码/notice闭包，不以外层ELF检查替代整个发布。手机和真实模型仍未操作。

## 2026-10-24 独立原生guard与恢复主程序

新增开发期独立coordinator/guard主程序，连上意图/创建计划/真实准入/单次GO、owner/worker pidfd观察、独立卸载报告、退出与域回收。持久worker报告在资源收尾前保存，正常成功还必须实际worker退出码为0；缺失/部分报告不从stdout补造。coordinator与guard都为专用subreaper，收尾仅用/proc真实children及P_PIDFD waitid核实自有/接管后代，再按pidfd停止/回收，既有附加目标不成为选择器。合作结束最多15秒，之后保留5秒强制收尾；coordinator对guard也有绝对等待截止，冻结不无限waitpid。

开发CLI支持独立recover，不重放脚本。先退役，复核旧guard/boot及worker PID-start，按完整inode记录/空组计划回收；已有最终记录精确复用。即便元数据拒绝恢复，coordinator仍尽力停止自己接管的后代，不据此去杀未经验证的其他任务组。Root权限不是对其他Root程序的沙箱，Android实际任务cgroup/Frida helper行为仍须资格测试。

新增14项真实Root cgroup/自有模拟worker测试，覆盖正常/到期、两层父进程退出、冻结guard、脱离进程组及主动迁移的自有helper、exec错误/坏报告、重复恢复/迟到启动、完整报告但不退出和无响应；待CI。ARM64完整guard与fixture链接通过，Windows仅跳过。不含真实Frida注入，不是已打包/注册Agent的产品入口；接下来接真实Frida通路及Root Binder/恢复策略。

创建计划1c73a8d完整CI40009832615已成功（9项新cgroup窗口测试通过），见对应证据。继续推进，不停止等待用户确认例行实现。手机仍最后集中验收，无真实模型调用。

## 2026-10-24 cgroup创建计划与空组恢复

独立guard接入时补上mkdir与完整inode记录之间的崩溃窗口。TaskScope拆为reserve/materialize，旧create仍保持原组合行为；受管路径先将任务key、guard身份、boot、parent dev/inode、随机name及变长parent路径带校验和落盘，再mkdir、再持久完整记录，之后才允许fork。普通路径不增加计划文件。

恢复先按原完整记录回收，再核对计划guard已死、boot/父目录身份，仅移除精确计划指向的空组；未完整登记的已占用组只报busy，绝不kill。无mkdir/已删除/完整登记/短tmp记录均有选择性处理，不扫描整个cgroup树。计划最大约8KiB，常规仅写实际路径长度，Root ownership而非校验和提供信任边界。9项真实Root cgroup故障用例待CI，ARM64完整夹具链接通过，Windows仅跳过。

前一7025c34完整CI40008334926已成功，含25项journal/退出持久化；见ci-40008334926-passed.txt。继续独立guard及恢复接入，不停止等待用户“继续”。仍无手机/真实模型操作。

## 2026-10-24 退出事实与资源收尾持久记录

新增managed.worker与managed.stopped：只有专用流验证EOF并匹配READY身份/票据的52字节报告才落盘；123字节收尾记录区分CLEAR/NOT_CREATED/UNKNOWN，明确真实退出码可未知。先持久退役才允许收尾记录，已有矛盾/部分文件拒绝覆盖；无worker报告仍是缺失，不能从资源清理合成脚本卸载。新8项与原17项Root journal合计25项主机用例待CI，NDK30/API36 ARM64夹具链接通过，Windows仅平台跳过。独立guard/恢复正在接入，不是已开放的Agent功能。

用户要求连续推进，不再每个小阶段结束等待“继续”；保持只在新增底层选型确认或必要设备/授权阻碍时联系。c6fce52完整CI40005725398已成功，证据ci-40005725398-passed.txt。手机仍最后集中验收，不调用真实付费模型。

## 2026-10-24 真实cgroup放行10项通过

c6fce52的CI40005725398 managed-supervision独立job成功，9接收/10观察/17 journal/10实际Root cgroup放行均实际通过。放行夹具包含真实fork/入域/GO/worker及helper exec/回收，最终加载位是模拟，不计为Frida注入或Android设备资格。证据ci-40005725398-managed-passed.txt；ARM64夹具完整链接及16KiB证据managed-launch-arm64-build.json。完整workflow仍待核实。

Root独立guard主程序、最终事实持久发布、精确恢复/版本固定、Root Binder和Agent仍未接。保持Frida未打包/未ready，手机仍最后集中验收。本提交仅文档/证据，不重复触发未变构建。

## 2026-10-24 原生cgroup admission与GO放行基础

新增managed_launch_prepare/release：单线程Root guard中fork，父死信号及父PID复核；子进程先关闭自己的journal锁副本，进入本任务新建cgroup，停在私有socket门前。父进程捕获pidfd/startTicks/boot，持久READY后才返回；release持久记录后再核实owner/worker存活并单次GO。拒绝SIGCHLD自动回收/自定义处理器，避免失去未回收子PID所有权。新子进程close_range CLOEXEC，只有控制流明确跨exec，worker bootstrap再恢复CLOEXEC；exec失败由原生launcher报告never-attached事实。

新增10项Root真实cgroup/自有原生worker测试（不注入Frida）：放行前不执行、退役/父死/worker死亡、准入失败、exec失败、重复GO、FD及helper exec隔离、错误参数和自动回收拒绝，待CI；Windows仅平台跳过。完整ARM64夹具链接已通过；不是独立Root guard主程序，也未APK打包。未来调用者仍须核实/固定worker版本与普通exec属性、记录scope、持锁及监督/真实回收，Root Binder/最终事实持久化/Agent仍未接。

上一轮aeebb08完整CI40003765109已成功，含17项Root journal故障用例；证据ci-40003765109-passed.txt。手机验收仍最后集中进行，未调用真实模型。

## 2026-10-24 Root一次性启动与持久退役屏障

新增独立Root journal实现：私有目录内固定文件，Root属主/权限/单硬链接/NOFOLLOW验证，与既有.run.lock约定相同的非阻塞flock。意图、164字节guard/worker PID-startTicks-boot身份、放行记录和退役标记均先写入并fsync文件及目录。启动/身份/放行不可重放，退役可幂等但重试仍需fsync；仅退休标记也阻止之后迟到start。损坏/部分写/错误阶段顺序保留失败状态，不删除或覆盖。

新增17项Linux Root主机测试含真实进程互斥、父进程死亡、子进程关闭继承锁不会释放父锁、短写/文件或目录fsync失败、错误票据/身份、symlink/hardlink/FIFO/权限等，待CI；Windows仅平台跳过。NDK30/API36 ARM64完整链接和16KiB检查通过。当前是独立持久原语，尚未被实际cgroup guard/Binder/Agent调用，不能把身份记录当作worker已启动或注入已清理。

上一轮f53d824完整CI40001467909已成功：9接收+10观察+19真实Frida及Android构建/lint通过，见ci-40001467909-passed.txt。本轮仍不操作手机/真实模型；继续接实际fork/admission/GO与回收。

## 2026-10-24 监督端新增19项主机用例通过

f53d824的CI40001467909独立managed-supervision job已成功：9项报告流接收、10项真实自有子进程/pidfd/截止测试实际通过，后者19.048秒，包含真实15秒取消上限。证据ci-40001467909-managed-supervision-passed.txt；该job无Frida注入或手机执行。完整workflow仍在运行，不能将独立job成功写成整体成功。5d9ebff的40001398070被新提交取代取消，不记通过。

Root guard/阶段日志/退役恢复/Binder/Agent入口仍待接。当前产品不会因这两个监督原语而显示Frida ready；继续按ROOT_MANAGED_TASKS_IMPLEMENTATION.md接入。只更新文档/证据的本提交不重复触发未变构建。

## 2026-10-24 事件驱动的受管观察循环

b7c3882完整CI40000038379成功，9项严格EOF接收主机测试、19项真实Frida和Android构建/lint均通过；见对应证据。

新增独立原生managed_observe：同一个poll等待owner/worker pidfd、最终事实流和绝对单调时限；不新建线程、不定时扫描/proc。到期发duration、owner退出或流损坏发cancel，单次通知后最多给15秒合作清理，并受调用者预先扣除强制回收预算的force_deadline限制。无响应返回超时，交给后续guard强制回收，不把报告EOF当worker退出；报告已完整但worker仍活也不结束等待。

新增10项自有原生子进程主机用例，包括真实pidfd、协作到期/父死取消、无报告/损坏流、报告完整但不退出、无响应强制边界及真实15秒上限，待Linux CI；Windows按平台跳过。NDK30/API36 ARM64独立夹具完整链接且LOAD均16KiB对齐，19184字节，仅夹具不是Frida worker体积。还没有实际cgroup guard/阶段日志/Binder/Agent产品入口，也没有手机执行。

## 2026-10-24 监督端严格退出流接收

51a5658完整CI39997696909已成功，7项控制/解码与19项真实Frida生命周期均实际通过；Android构建/lint成功，仍不是手机运行证据。

新增独立纯POSIX接收器，按事件非阻塞累计52字节，完整帧也必须等EOF才发布事实；额外字节/重复帧/截断/附带FD一律失败，关闭收到的FD，失败状态不可被后续合法字节改回成功。每次最多处理53字节，不随对端大流分配或无限排空。新增9项Linux主机测试覆盖所有分片/截断边界、延迟尾部及控制消息截断，待最新CI；NDK30 ARM64/API36编译检查通过。本机Windows按平台跳过这9项，未当作执行通过。

这里只补监督侧接收原语，尚未接实际cgroup guard/Binder/持久退役/Agent。接收事实与worker/domain退出仍需分别核实。没有操作手机或真实模型。

## 2026-10-24 原生监督控制与最终事实开发通道

新增私有原生父子stream：到期/取消事件、关闭时清理、Frida deinit后52字节独立最终事实，绑定票据摘要和worker PID/startTicks。bootstrap验证Root父身份、恢复CLOEXEC、清除内部环境；不使用stdout头部作证明。无frame/强杀/报告失败仍是未确认。修复attach API已尝试但session缺失被误标detached的边界，保留部分事实。

新增7项控制/帧解析主机用例与8项真实Frida监督用例（合计19），待最新CI；ARM64原型交叉链接/16KiB检查通过89143968字节。未改变App打包，未重复运行未变的Android/JUnit。上轮cc84c22完整CI39993061650成功；15项受管基础/通道Android仍仅编译，没有操作手机/真实模型。

Root Binder、实际原生cgroup guard、Root持久阶段/退役与App恢复尚未接上；worker事实不能单独释放待处理意图。下一步按ROOT_MANAGED_TASKS_IMPLEMENTATION.md继续连接，不宣称本轮已完成Root受管派发。

## 2026-10-24 FD通路完整CI通过与握手前取消补查

dbea45a完整CI39991608445成功：9项真实Unix socket（含对端死亡）及11项真实Frida生命周期/脚本FD通路全部通过，Android Debug/Release R8/testAPK/lint通过。证据ci-39991608445-passed.txt、fd-tests-passed.txt、frida-tests-passed.txt。

随后补上已claim但进入超时域前就已取消的关闭兜底；外层finally释放监听/连接和自有快照。新增第6项Android通道测试，检查监听不可连接、自有副本已关闭、调用者原FD保留；仅编译。Debug/Release再次通过29秒，testAPK/lint26秒及引用断言编译13秒；不重复核心/原生测试，未改其生产源码。累计此受管基础/通道15项Android仅编译，仍留最终手机验收。

下一步接Root Binder描述符所有权、实际worker PID捕获、独立于Linux的任务cgroup监督、持久阶段/退役标记及最终证明小通道；随后连接Agent/UI、ART/spawn、许可证闭包与APK体积资格。FD交付不是执行/清理成功，也没有将Frida显示为ready。

## 2026-10-24 封印脚本FD端到端开发通路

1e5baf1完整CI39989586948成功，8项真实Unix socket测试实际通过。新增Android RootScriptChannel、固定72字节帧核心与原生attach-channel：Root UID/PID/startTicks、nonce/票据摘要、首片rights、封印/内容校验、单次ACK/EOF、取消关闭accept及自有快照。不增加网络库或磁盘脚本副本，仍无Root Binder/任务cgroup产品派发。

Frida运行时统一初始化一次，解析JSON前先准备随附GLib；反初始化由调用者统一负责，保留硬截止到退出。新增3项真实Frida通路用例和对端死亡第9项socket用例待最新CI执行。核心242项（232通过、10平台跳过）与Debug/Release R8/testAPK/lint本地通过；新增5项Android通道用例仅编译。ARM64原型已重新链接/16KiB检查，未打包/未注册Agent，手机仍最后集中验收。见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

## 2026-10-24 原生Frida预算与证明错配补查

将原生attach观察上限从270秒收紧为250秒，和已批准Root总270秒/预留20秒一致；解析夹具补250000接受、250001及270000拒绝。ARM64 NDK重新编译/16KiB外层检查通过，报告frida-managed-budget-build.json；新原生输入边界须由最新Linux CI实际执行。票据证明补App UID、Root PID/startTicks/bootId、脚本版本/字节数错配，核心239项本地再次通过（229通过、10跳过，9秒）。

schema1..7迁移/保留Linux票据租约、封印内存等新增9项Android仅编译，尚未执行手机。受管基础4a5f89e的Frida独立job已通过；完整CI状态另查，不将进行中记为已通过。

## 2026-10-24 Root受管任务账本与内存快照基础

用户确认组合后新增schema8 root_managed_runs、不级联删除的owner屏障、不可变attach票据/严格清理证明；不绑定Linux环境。SpaceStore在删文件前拒绝未确认任务，损坏记录不跳过。修复历史迁移用例版本断言与重复索引创建。RootScriptMemory新增<=1MiB封印memfd、精确摘要与大小、CLOEXEC/取消回收；没有写盘回退。FD握手/Root入口/恢复/Agent仍未接入，不能显示Frida可用。

核心239项（229通过、10跳过），Debug/Release R8/testAPK/lint本地通过；新增9项Android用例仅编译。上一提交16a32b5完整CI39971396204已成功。当前提交推送后核实CI，不操作手机/真实模型。详见ROOT_MANAGED_TASKS_IMPLEMENTATION.md。

## 2026-10-23 静态逆向Agent账本接入修复与Frida完整CI

发现真实Agent路径的两个遗漏：ToolRunStore产物白名单未含shared_toolchain/reverse_analyze，调用prepared会在执行前失败；且一次probe三步都登记同一单产物字段，第二步会被拒绝。现允许这两个已注册工具，probe只登记首步主产物，结果另列每个尝试的规范logDirectories；不改数据库结构、不允许覆盖其他工具既有产物。独立Linux run票据仍保护每个原生步骤。

reverse_analyze结果使用现有Linux小投影，shared_toolchain保留小型错误投影；大日志/历史截断不再掩盖非零退出或probe错误。Android映射重开测试扩展两个工具，新增1项长输出错误投影重开用例；五个Root静态逆向资格用例现在先经真实SharedToolchainTool+ToolRunStore.recording探测，分析也经过账本，避免只测底层绕过注册路径。均只编译，设备仍留最终验收。

本地核心235项（225通过、10平台跳过）及Debug/Release R8/testAPK/lint通过，35秒，证据local-static-agent-ledger-passed.txt。Frida805f5cd完整CI39967903594已成功，八项真实主机用例全部通过（含桥控制单次执行）；证据ci-39967903594-passed.txt及frida-tests-passed.txt。此CI尚不包含本段随后发现的App账本修复，后者完整CI推送后核实。

下一步Root持久化/FD新通道方案已比较，见ROOT_MANAGED_TASKS_PROPOSAL.md，待确认；当前schema仍7，没有新增Root执行入口，也未操作手机/真实模型。

## 2026-10-23 Java桥加载与选定脚本控制

CI39966517578的Frida独立job七项实际通过，包括生成桥加载/普通C目标不误报Java；整体workflow另查。原生原型接上一次aurora:run payload消息、严格文件预算与userScriptPosted/桥收到/同步求值返回三种不同事实，新第8项真实单次执行用例待CI。未接APK/Root/Agent、未做ART资格，不能宣称手机Java动态逆向已可用。详见DYNAMIC_REVERSE_IMPLEMENTATION.md。

## 2026-10-23 真实Frida会话与Java桥构建进展

修复共享事件上下文和GLib销毁顺序后，又通过实际崩溃栈定位Frida设备后端半启动关闭竞态；改用启动完成事件屏障，不添加固定延时。CI39965185125的独立frida-session job六项已实际通过，身份错配含5次快速退出子场景；整体workflow状态另查，不能算全CI已通过。

Java桥7.0.13+官方编译器19.1.0+Nodebinding17.24.3固定锁文件/摘要，Windows开发期两次编译成功235893字节；新增第7主机用例验证真实加载时普通C目标不会误报Java能力，待CI执行。完整native post/ART、spawn、Root/Agent包装、持久恢复与许可证仍待完成。源码及生成过程不向手机安装半成品，详见DYNAMIC_REVERSE_IMPLEMENTATION.md。

## 2026-10-23 Frida附加、消息与退出收尾原生实现

已新增原生附加流程：PID/pidfd复核、受限脚本读取、QuickJS加载、有界序号消息、跨线程取消、独立清理截止、unload/detach/manager确认。使用frida_unref与最终Frida上下文排空处理晚到回调。未打包/注册Agent工具；spawn/Java桥/持久恢复仍待接入，不能宣称动态逆向可用。

ARM64 NDK交叉构建/依赖与外层16KiB检查通过。新增3项Linux输入+6项真实Frida主机生命周期测试，Windows跳过，推送后必须核实CI实际运行；不是9项本地通过。上一轮构建CI39960629598已成功。详情与证据见DYNAMIC_REVERSE_IMPLEMENTATION.md，手机留最终集中验收。

## 2026-10-23 动态逆向原生构建资格与APK构建链检索

用户回复“多处对比取最优”，按DYNAMIC_REVERSE_PROPOSAL推进独立Frida Core worker的构建资格。官方17.24.3 devkit字节摘要通过，现有NDK ARM64交叉链接与外层16KiB段/系统依赖检查通过；strip后约89MB、单库ZIP约36MB，仅原型体积，不是完整APK/RSS。新增可复现构建适配器、2项离线归档拒绝测试及CI资格步骤。

worker目前只实现版本/Root本机进程探测原型，未打包/注册Agent工具，没有手机安装半成品或常驻server。attach/spawn、Java桥、消息与取消清理必须接着完成，详见DYNAMIC_REVERSE_IMPLEMENTATION.md。原生构建完整CI推送后核实；不因静态链接通过就显示runtime ready。

清理本轮重复展开devkit及旧strip文件，释放434501141字节，固定源压缩包和当前原型产物保留供后续开发。手机APK构建链的AOSP/Termux/归档AndroidIDE/CodeOnTheGo来源已重新比较，见APK_BUILD_RESEARCH.md；这是待定研究，不是新构建链已批准或实现。手机与真实模型均未操作。

## 2026-10-23 按类静态分析与完整设备资格用例

reverse_analyze新增可选JADX className，使用上游--single-class/--single-class-output及--no-res；输出固定在当前唯一run目录的selected.java，向Agent返回selectedSource计划路径。严格限制参数类型、长度、控制符/路径分隔符/选项前缀，Apktool不接受该选项。未指定时保留原全量行为，不改变JVM/Root架构。按类仍需解析输入和依赖，不宣称已测性能领先。

新增自有732字节DEX及可复现Java/D8生成脚本；Windows主机实际验证全量和按类JADX输出中的标记。新增StaticReverseDeviceTest五项：真实启动+只读挂载、DEX全量/按类、自己APK清单、缺失输入失败、挂载任务取消及持久bundle引用回收；只编译，未执行手机、不联网下载。准备/故障边界见STATIC_REVERSE_ACCEPTANCE.md。

当前核心235项：225通过、10平台跳过；新增1核心选择参数回归已运行。Debug、Release R8、testAPK和lint通过（39秒），证据evidence/0.1.40/local-selected-analysis-passed.txt。完整CI推送后核实。

动态逆向新底层方案见DYNAMIC_REVERSE_PROPOSAL.md，Frida17.24.3+Java桥7.0.13来源已核实；仍待用户确认，不视为已接入。

## 2026-10-23 Linux执行结果完整性与参数校验

修复Linux命令结果投影把缺失timedOut视为未超时的问题。现须有明确scopeRemoved=true、interrupted=false、timedOut=false以及数值型0..255退出码和stopped=true才归为exited；字段缺失或类型不符为outcome_unknown，success=false、exitCode=null。明确超时/中断仍保留对应状态，非零真实退出仍保留原退出码。外层protocol/runId也不接受字符串数值或错误类型冒充。reverse_analyze复用该投影，自动获得相同行为；退出成功仍不证明反编译完整。

command_execute_linux现在先拒绝额外参数与null/字符串/布尔/小数等非法timeoutMs，不再默默换成默认60秒，也不在参数失败时登记产物或派发命令。未提供timeoutMs的正常请求继续使用默认值。对照原生guest_run_finish源码确认真实完成记录总是输出上述明确布尔字段；没有修改Root协议、持久化或任务收尾策略。

新增2项核心回归（多个字段/参数子场景）已运行。核心234项：224通过、10平台跳过；Debug、Release R8、testAPK和lint通过（41秒）。证据evidence/0.1.40/local-strict-linux-proof-passed.txt。手机留最终集中验收，本轮完整CI推送后核实。

## 2026-10-23 多环境逆向资格隔离与过期探测收尾

原先只有一个全局资格槽，其他会话在不同Linux代发起probe会清空已通过的资格。现改为按Root身份、Linux代和bundle隔离的进程内有界资格表，最多32项；新探测只撤销自己的scope，失败不会抹去其他环境资格。淘汰项需要重新探测，不从旧结果推断就绪。

每次probe持有独立尝试标识，完成时必须仍为该scope的当前尝试；被重试、删除或淘汰取代的迟到结果不能重新授权。工具删除前和finally均撤销该bundle资格，清理失败/取消也不保留旧证据；持久任务租约与真实退出证明继续由原Store/SQLite/Root流程控制，资格表不代替删除屏障。删除未成功也会保守撤销资格，需要重试probe。

只在极短无I/O的表操作上使用JVM成员同步，CLI运行与文件操作不持有该锁，没有新增依赖/持久化/常驻工作。依据Kotlin官方Synchronized文档：https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-synchronized/ 。新增3项核心回归覆盖跨代/身份隔离、旧尝试拒绝、移除及重装、独立bundle和容量淘汰。ARM64 CLI仍待最终手机验收。

上一轮3beb4c7完整CI39953323831成功，证据evidence/0.1.40/ci-39953323831-passed.txt。本轮核心232项：222通过、10平台跳过；3项新资格测试实际通过。Debug、Release R8、testAPK和lint通过（42秒），见evidence/0.1.40/local-concurrent-qualification-passed.txt。本轮完整CI推送后另查。

## 2026-10-23 逆向资格与Agent环境版本一致性

修复共享环境更新后的版本错配：shared_toolchain probe此前独立读取全局当前代，而reverse_analyze沿用Agent已固定的代，导致重复探测仍不能满足分析资格。现在probe先取得同一LinuxAgentEnvironment的任务租约，三个CLI及后续分析共用固定代。status展示该任务选择的generation并据此判断runtimeReady；只查看状态不新增租约，未开始执行的任务仍能看到后续选择变化。没有新增运行时、持久化结构或后台进程。

扩展既有Android回归断言：无环境观察为null、观察不固定旧代、已固定任务跨全局更新仍报告原代。设备执行留最终集中验收，不能以testAPK编译替代设备通过。

上一轮556ecf6完整CI39947239121已确认成功，见evidence/0.1.40/ci-39947239121-passed.txt。本轮核心测试、Debug、Release R8、testAPK、lint通过（41秒），见evidence/0.1.40/local-generation-check-passed.txt。

## 2026-10-23 无图形JVM一致性、依赖盘点与清理错误可见性

上轮bedcfd3完整CI39925157055已通过，日志确认3项新增原生工具挂载测试实际成功。不是Android JVM运行证据。

新增staticReverseJava统一启动参数，探测/分析共同使用无图形profile、native-access、显式堆与ActiveProcessorCount；CLI不再依赖显示环境的自动判断。主机实际启动子JVM验证isHeadless、处理器1、最大堆128MiB三项语义；不是仅断言参数字符串，更不是ARM64性能结论。依据OpenJDK27 GraphicsEnvironment源码对java.awt.headless的处理，未加GUI/音频包或新运行时。

新增scripts/audit-static-reverse-elf.py：读取固定摘要的JRE与Ubuntu ARM64归档，用已有NDK llvm-readelf做43个ELF的DT_NEEDED名称盘点，不执行或解压不可信路径。名称缺口仅见libjsound/libsplashscreen/libawt_xawt的ALSA/X11相关库；不自动安装桌面栈。报告只做basename可用性，未证明路径搜索、符号版本、dlopen或Android SELinux可用，实际probe仍必须进行。工具只作按需离线开发审计，不加入每次Agent任务开销。

修复Store清理异常仅被原安装异常suppressed而Agent看不见的问题：正常失败+清理失败明确返回cleanup remains pending；取消仍传播CancellationException，状态通过cleanupPending记录已知清理失败。成功显式cleanup后清除此错误标志；它不是“全目录已经扫描为空”的保证，有界维护仍按64候选。新增POSIX权限失败用例覆盖普通错误/取消及恢复；Windows跳过，此用例需要Linux CI实际执行。

主机源码获取脚本也改为仅在完整校验后发布，网络/摘要/预算失败移除本次创建的partial；不覆盖或删除未知已有partial，也不为它发起网络请求。新增3个离线Python测试，含3种失败子场景，加入CI；测试不访问互联网。

当前核心229项：219通过、10跳过（原9 POSIX+新1权限失败）；本轮新增主机JVM测试已执行。3 Python测试通过。Debug、Release R8、Android测试APK、lint通过，日志outputs/static-reverse-headless-cleanup-qualified.txt。新POSIX用例与新改动完整CI推送后另查。手机、真实模型均未操作。

一手依据：https://raw.githubusercontent.com/openjdk/jdk/jdk-27%2B35/src/java.desktop/share/classes/java/awt/GraphicsEnvironment.java 。报告与证据见evidence/0.1.40。

## 2026-10-23 静态逆向原生挂载、资格与Agent执行

已接入任务专属工具目录到guest /opt的只读绑定，复用现有open_tree/mount_setattr/move_mount与私有挂载域，不改原Linux基础镜像。源与guest目标均固定描述符且不跟随链接；无工具参数的普通Linux任务保持原路径。工具票据仍在派发前绑定持久引用，只有原任务退出证明才释放，删除仍受保护。

Root linuxRunProtocol从2升级3；新App拒绝旧Root执行服务，最终更新后要断开/重连Root，FolkPatch已有授权不重置。prepare按票据bundle选固定共享目录并校验标记，不能由Agent传任意宿主挂载路径。原生--tools-dir只接受绝对路径，旧命令格式仍支持。SQLite schema仍7。

shared_toolchain新增probe：使用当前Root身份、Linux代与bundle执行固定Java/JADX/Apktool --version；协议、runId、环境、bundle、停止/退出状态及版本文本都匹配才缓存该身份的启动资格。App重启/Root身份或环境变化后重新探测；资格不持久伪装通用兼容。runtimeReady现在由真实探测与安装状态决定，失败含步骤与原证明，不靠“文件存在”置true。启动资格不是任意APK分析质量证明。

Agent新增reverse_analyze，JADX java和Apktool manifest/resources/smali，字面argv、无shell/--force，产物在会话Linux项目reverse/UUID，日志复用runs/UUID。Agent须先将输入置于Linux项目；不把Android宿主绝对路径悄悄挂入guest。默认堆768MiB/4线程可调，仅为初始资格配置，不代表总RSS或安卓最优。厂商framework缓存按会话HOME和工具bundle隔离。退出码/超时/部分结果保留，失败不称完整成功，不自动重放。工具定义固定，不随Root连接临时增删来扰动请求前缀。

复用上游JADX启动器的native-access与ZIP额外字段兼容参数，保留Aurora显式堆/线程预算；Windows Java17自有class由JDK25运行JADX实际反编译成功，采用上游参数后JNI警告消失。不是ARM64 JRE27执行证据。系统可信Context filesDir先toRealPath，再检查受管理子路径，修复/data/user/0别名导致合法安装误拒绝。

新增5核心测试（3个参数/失败结果，2个资格证明）与Linux原生主机3项挂载/链接/参数测试。当前227核心：218通过、9 POSIX跳过；Debug、Release R8、Android测试APK、lint本地通过，NDK ARM64编译通过。主机原生3项本机Windows仅语法检查，实际运行待Linux CI；手机未执行，不把CI夹具当实际JVM/Android结论。对应源码包重新生成，未新增依赖/守护进程或付费API调用。

后续：检查本次完整CI，最终手机执行安装/probe/自有APK分析、取消/超时/并发删除/撤权/重启与性能；逆向静态链已实现而未做Android运行验收，Frida/原生分析/手机APK构建链仍属后续范围。

## 2026-10-23 共享工具持久任务引用与删除屏障

复用linux_runs现有ticket JSON，增加可选toolBundle SHA256，与原Linux环境租约在同一事务登记；旧票据默认null，schema仍7，无新表/框架。退出证明必须同时匹配原runId/generation与toolBundle；缺失或不同bundle不释放记录。恢复仍由原Root停止证明驱动，不重放命令。

StaticReverseStore.reserve在同一操作锁内确认已安装版本并提交持久任务；remove持该锁查询真实待处理Linux票据，有引用或查询失败则保留版本。删除检查只读取尚未完成的linux_runs，不解析历史消息。App重建管理对象不能抹掉数据库引用；更新不改旧bundle。LinuxRunManager内部工具调度入口使用此登记顺序，原命令与PTY同样复用结构化Dispatch；连接身份仍从同一Root端点捕获。

这轮只连接持久引用/删除屏障。原生工具只读挂载和Agent reverse_analyze尚未实现，Root prepare明确拒绝非空toolBundle；管理状态runtimeReady仍false，不把登记成功当能执行，不把工作强行回退普通命令。后续需原生只读挂载、Root能力协商、CLI计划与实际ARM64运行资格后才开放分析。

新增3核心测试：bundle序列化/精确证明、模拟持久记录查询跨Store重建的删除保护、查询失败保留。新增1项Android SQLite持久引用/证明测试仅编译，未在手机执行。当前222核心：213通过、9 POSIX跳过；Release R8、Android测试APK及lint通过。来源样本仍显式提供；不能从注入查询夹具称已做进程重启或Android挂载测试。

本轮未操作手机/真实供应商；完整CI推送后另查。日志outputs/static-reverse-leases-qualified.txt。

## 2026-10-23 共享工具管理与失败清理闭环

用户进一步明确：不长期保留无用半成品；Agent要能安装、更新、删除共享工具及管理共享文件。已接入shared_toolchain（status/install/update/remove/cleanup）到Agent工具集。ARM64且已连接Root才下载，固定可信目录逐组件校验与准备，全部完成才原子发布；安装失败/取消finally清理整次暂存，成功也删除归档与暂存。已安装同bundle直接复用，不再重复下载。update选择当前APK编译期可信目录，不承诺动态追随未验证的上游latest。

新增StaticReverseStore，所有管理操作由同一进程Mutex串行。卸载先把选定版本原子退役到带标记的暂存，再删除；清理保留owner标记到最后，进程在清理中断后仍可重试。启动/Agent显式cleanup每次最多64候选，删除已识别旧暂存及最终空UUID目录，不扫描/删除无关内容。未知标记、清理失败仍保留；错误不伪装成功，启动清理失败以cleanupPending提示而不阻断应用启动。已知标记的损坏版本可显式remove后重装。

StaticReversePreparation本身失败也清理自己创建的target；不删调用前已存在的target。既有SkillNetwork复用原OkHttp/HTTPS/取消逻辑，新增受限maxBytes参数供官方73MB JADX包使用；Skills默认64MiB不变，不增加网络库。

共享工作区原有files_edit_batch可创建/修改，本轮新增files_remove用于显式选定单个普通文件：核对观察到的字节数后unlink，不递归、不留回收站。大小检查不是内容版本保证；同UID/Root程序并发更改不构成安全沙箱。共享resources依旧按原三类空间约定由Skills/Linux/工具安装管理器变更，不开放任意原地改写来绕过版本管理。

新增7个Store生命周期测试+2个共享文件删除测试；使用注入的小型准备器验证发布/清理控制流，不能冒充真实ARM64整包安装。真实官方包校验与JADX/Apktool准备仍由上一轮来源测试覆盖；JRE展开/Android Root执行要最终手机验证。当前219核心：210通过、9 POSIX跳过；Debug、Release R8、Android测试APK、lint通过。

工具文件安装管理已接入，但分析执行的只读挂载与持久租约仍未连接，因此runtimeReady明确false；当前没有任务能挂载这些新工具版本。启用实际分析前必须接入原Linux持久任务退出证明与工具租约，不能只用此进程Mutex保护Root任务。完整运行能力未完成，不向模型宣称可执行。手机未操作，未调用付费模型。

## 2026-10-23 已批准静态逆向：来源与准备层

用户确认STATIC_REVERSE_PROPOSAL推荐组合：JADX1.5.6、Apktool3.0.3、Temurin27+35 ARM64 JRE，按需共享Root chroot，复用监督任务。新增编译期可信目录、固定bundle ID、长度/SHA256/取消/物理路径检查；准备层复用RootfsArchive/SkillArchive，ZIP和tar仍遵循各自链接规则，JAR完整复制后再次校验。只写未发布暂存目录，不把准备成功当已安装。

真实发布包重新下载重算摘要，JRE为ELF64 AArch64，304条目/197873172文件字节；JADX实际为lib/jadx-1.5.6-all.jar，已修正原先CLI单jar名称猜测。归档版共149086879压缩字节，不逐会话下载。可复现脚本scripts/verify-static-reverse-sources.py（默认核验本地文件，--download显式下载缺失包），未将大包提交Git。

新增5核心用例，含显式官方样本的扫描/真实JADX和Apktool准备；输入目录及样本文件计入Gradle test inputs，避免未提供样本的缓存掩盖后续验证。当前210核心：201通过、9 POSIX跳过；CI未提供官方逆向样本时该1项另跳过，不能写成来源已验证。Debug Kotlin、lint与Release R8通过。

Windows x86_64 JDK25上两个CLI --version返回正确；自有APK清单夹具解码exit0并产出manifest，但Apktool保留5项应用资源引用警告，不作为完整资源反编译通过。JRE27 ARM64未执行，手机未操作。源码包校验/WindowsCLI成功不能证明Android16 chroot执行。

尚未完成：共享版本发布与回收/租约、Root只读挂载、Agent安装/分析接口、UI状态、实际ARM64 CLI与取消/低内存/会话删除验收。本阶段不对Agent广告工具已安装可运行。

## 2026-10-23 下载展示元数据与产物详情

下载握手单独验证 mime（128字符）与 Content-Disposition（512字符），超长提示整体丢弃；上传 ready 仍只允许 kind。复用已有 AndroidX URLUtilCompat 解析 filename/filename*，再移除路径片段、控制/格式字符及孤立代理，名称最多120个UTF-16单元且不截断代理对；MIME只保留规范化类型，不用于内容验证/自动打开。原始头、Response URL均不写入结果。

displayName/mimeType进入已有工具结果，无数据库迁移、旁路文件或新依赖；磁盘名仍 runs/UUID/download.bin，原中断恢复协议不变。下载详情展示名称、类型、字节与产物入口；只允许规范UUID下载路径，旧结果回退download.bin。下载不再打开不存在的命令输出日志。

新增4项核心测试，生产传输JS增加2个元数据场景共18个（属于一个JUnit）。核心205项：196通过、9 POSIX跳过；Debug、Release R8、Android测试APK与lint通过。新增1项Android文件名扩展参数端到端用例仅编译，最终真机统一执行。未调用付费模型或操作手机。

官方依据：https://developer.android.com/reference/androidx/webkit/URLUtilCompat 。名称/MIME始终是网页提供的不可信提示；跨域未暴露的头不可读取，不重发网络请求猜名称，不承诺所有下载来源可用。

## 2026-10-23 浏览器传输错误码与 MIME 通配符修复

页面传输仅返回白名单错误码：脚本、Response 可读性、源类型、读流、字节预算、上传目标/accept/多选与协议等。原生只接受精确错误字段；未知代码/额外字段统一降为 page_transfer_failed，不把页面异常文本、签名网址或请求内容写入账本。页面代码属于未可信诊断提示，不作为原生事实。工具继续保留方向 phase 与 unknown/outcome，不自动重试；超时新增 transfer_timeout。

修复 Agent 文件输入 accept=*/* 被误拒绝的问题，保留 application/* 与扩展名匹配。新增通用与类型族 MIME 通配符场景；生产 JS 夹具逐一验证失败代码和精确消息形状。

本地核心201项：192通过、9 POSIX跳过；生产传输JS16子场景属于一个JUnit用例。Android Debug Kotlin编译、lint通过；没有操作手机。文件展示名/MIME元数据仍待后续，不更改下载固定磁盘名和已批准恢复协议。

## 2026-10-23 0.1.40 下载中断恢复与登记顺序

改为先SQLite登记目录再创建partial；仅对uncertain且无结果、已登记下载做有界恢复，保留完成文件、同级内容和不明条目，不重放请求，不把uncertain改成成功。schema6→7增加待回收部分索引；主机提取实际SQL验证10000条完成历史不被常态回收扫描，迁移成本及Android计划仍待设备资格。

本地核心200项（191通过/9 POSIX跳过），新5项Android仅编译。0.1.39实现d403a4f完整CI39915958302已通过；本轮0.1.40另查CI。手机仍0.1.32未操作，见BROWSER_DOWNLOAD_RECOVERY.md。

## 2026-10-23 0.1.39 原生浏览器文件选择与上传快照

接入单/多文件原生回调、会话/资源和系统SAF选择；64KiB流式快照、精确FileProvider URI、名称/MIME、数量/总量与只读属性，复用OwnedTasks协调取消与删会话，旧进程快照有界回收。为避免表单导航提前删文件，已交付URI保留到关页/删会话，待选请求导航即取消；UI有说明。

本地核心196项（188通过/8 POSIX跳过），Android新5项只编译。0.1.38实现40d96bc完整CI39913505934已成功；0.1.39另查CI，手机仍0.1.32未操作。下载 .partial崩溃回收、真实手势/Chrome读取URI/SAF/提供器阻塞与性能仍未验收，见BROWSER_CHOOSER_IMPLEMENTATION.md。

## 2026-10-23 0.1.38 浏览器 Agent 二进制传输第一阶段

用户确认组合方向。新增 browser_download/browser_upload：独立 ArrayBuffer 端口、文档/会话校验、64KiB 原生落盘 ACK、预算和临时文件完整发布；Agent 显式供给 conversation/resources 文件，真实 File 仍有整文件成本。复用账本与产物目录，浏览器事件合并显示字节，不新增网络栈或轮询。WebKit 按官方稳定发行更新 1.18.0，不启用隔离放宽。

主机核心191项（184通过/7跳过），新增传输生产JS14场景属于其中一项JUnit；Android新4项只编译。用户原生chooser/快照、崩溃遗留回收、更多错误诊断和真实Cookie/代理/表单/性能仍待继续，不能标为传输全部完成。见 BROWSER_TRANSFER_IMPLEMENTATION.md；手机未操作，仍0.1.32。

## 2026-10-23 浏览器文件传输研究（待确认）

已比较系统 chooser/精确 URI、ArrayBuffer 与 File/DataTransfer、页面流式下载、OkHttp、DownloadManager 和 CDP/新增网络栈。建议沿用 WebView 网络上下文并增加有界二进制传输，用户原生 chooser 与 Agent 文件供给分别实现；不把下载回调 URL 自动重发，也不假设脚本 click 具有用户激活。新通道、文件快照生命周期、预算和能力边界待用户确认，见 BROWSER_TRANSFER_PROPOSAL.md；没有提前实施或操作手机。

## 2026-10-23 0.1.37 异步浏览器完整CI通过

9517ea9完整CI39909014575成功，证据evidence/0.1.37/ci-39909014575-passed.txt；含新生命周期/JSON合同/生产JS测试及原有全部主机和Android构建门槛。随后补mjs测试任务输入追踪（脚本变化不能被误判UP-TO-DATE）与跨域导航Android夹具，core/testAPK/lint本地再通过。最新后续提交CI另查。

当前功能/证据/缺失项统一见NEXT_STEPS.md，避免把历史章节中的旧待办重新当作未实现。浏览器异步六项、Root抓包七项仍仅编译待最终手机；没有连接/更新手机或调用真实供应商。接续优先浏览器文件传输研究与实现，新通道/持久化选择仍先确认。

## 2026-10-23 0.1.37 浏览器事件等待与异步JS

用户已确认每次调用独立WebMessagePort，见BROWSER_ASYNC_PROPOSAL。实现awaitPromise显式启用/有界deadline、精确origin/运行ID/文档revision、单次结果与关闭/取消收尾；结果不提供原生权限、网页数据仍不可信。加载等待从50ms轮询改为WebView回调StateFlow，并修复工具/批处理吞掉外层超时取消；同步/异步JSON预算与Unicode截断、字段/深度校验统一，解析移出Main并再次核对文档。

本地核心187项（180通过/7 POSIX跳过）；生产JS在现有Node实际跑14场景；Debug/Release R8/testAPK/lint通过。BrowserAsyncTest六项仅编译，实际WebView消息转移/origin/取消/刷新/renderer与手机性能仍待最终集中验收。详情BROWSER_ASYNC_IMPLEMENTATION.md，证据evidence/0.1.37。手机未连接/更新，仍0.1.32，无真实供应商调用。新增代码CI待查；此前84bcb4d完整CI39838321745、提案3fe2c2f CI39840197721均已成功。

## 2026-10-23 0.1.36 抓包主机资格完整通过，最终真机用例就绪

9ba73c9完整CI39837102395成功：原生组件/离线外层12项，私有netns内部10项全部通过，包含any标准cooked格式、截短统计和空闲CPU预算。证据evidence/0.1.36/packet-snapshot-host-ten-passed.txt。Android Debug/Release/testAPK/lint、核心/既有Linux验证和ELF/对应源码全部通过。

另补Root真机专项至7项，新增取消、断连重连和删除会话；仅编译通过，未连接手机。BPF命名主机/服务可能在编译期解析，已修正Agent工具措辞并建议数字地址/端口；这版Debug/Release/testAPK/lint也本地通过。最终验收步骤已写ROOT_CAPTURE_ACCEPTANCE.md，不卸载Aurora、不清用户数据，手机仍0.1.32。未完成项是Android实际Root发布/生命周期/熄屏与性能对照，不要将主机结果代替手机证据。

## 2026-10-23 0.1.36 主机采集生命周期已通过

ec6a727的完整CI39836086203成功：组件/离线外层12项、私有netns内部9项（空闲截止、严格预算、包数、取消、SIGKILL、非法过滤、接口down、符号链接）、既有Linux/PRoot/核心及Android构建全部通过，证据evidence/0.1.36/packet-root-host-lifecycle-passed.txt。随后补齐上游标准262144抓取长度、snapshotTruncated显式统计、字节预算完成原因一致性，新增any接口与空闲CPU预算检查；本地核心179项（172通过/7跳过）、Debug/Release/testAPK/lint及双ABI ELF/源码包通过，这些新增主机项等下一CI。

手机仍0.1.32，未连接/更新；Android Root、实际文件发布/取消/断连/删除/熄屏和性能资格仍在最后集中验收。不要把主机netns结果写成Android实测。详细实现/边界见ROOT_CAPTURE_IMPLEMENTATION.md。

## 2026-10-23 0.1.36 Root 抓包与 Agent 接入

已实现 packet_capture_root 的严格双端合同、追加Binder协议与原子端点能力、现有Root会话派发/监督/有界唤醒锁、固定运行cwd、worker文件身份/完成状态、选定PCAP属主发布与状态文件持久化、工具账本/日志/中文标题。离线保持普通UID，不扫描或递归chown会话。采集文件完整与任务成功分别检查，取消、硬超时、未知死亡、发布失败不当成功。

本地核心179项（172通过/7个POSIX跳过），Debug/Release/testAPK/lint、双ABI ELF/16KiB与源码包通过。新增6核心项已实跑；新增私有网络空间9项控制场景待CI，Android普通与Root专项4项仅编译，最终集中真机不变。先前76eb16a CI39833896207完整成功，主机离线/worker共11项证据已归档。详细合同与尚未验收项见ROOT_CAPTURE_IMPLEMENTATION.md，手机仍0.1.32。

## 2026-10-23 Root 采集 worker 第一阶段

用户已确认 ROOT_CAPTURE_RUNTIME_PROPOSAL 分工。新增独立 libpcap worker：Root UID 限制、固定 capture.pcap 排他且不跟随链接创建、整包前置字节预算、包数预算、CLOCK_BOOTTIME 截止、signalfd 取消、非阻塞 pcap/poll、有界分批、最终 flush/ferror/fsync 与大小检查、结构化保存量和未知可区分的内核统计。非混杂、4MiB 内核缓冲和100ms缓冲交付是待性能资格验证的初始值，不宣称最优。

Android arm64/x86_64 Debug、Release、测试APK和lint已编译通过，双ABI ELF/16KiB检查通过，对应源码包已生成。新增主机整包边界和缓冲失败测试待 CI 实跑。当前仅原生组件，尚未接入 Root 专用派发、选定产物属主发布和 Agent live 工具，不属于已可用实时抓包。取消退出2、I/O或采集失败1、预期预算或时长完成0；外层硬截止和未知死亡由现有监督器判定。真机受控流量、熄屏、丢包与CPU对照仍留最后集中验收。

## 2026-10-23 0.1.35 写盘资格与下一步控制提案

493cc86 CI39831312910最终完整成功：实际组件/离线分析主机10项、既有核心/签名镜像/POSIX/PTY/namespace/scope/恢复/挂载/GC/PRoot及Android Debug/testAPK/Release/lint、ELF/16KiB和对应源码包通过。主机对照实测未修改上游向/dev/full输出返回0；Aurora派生版拒绝成功，非普通/dev/null正常，两段轮转完整保留20000包。证据evidence/0.1.35/packet-host-ten-write-boundary-passed.txt。这是主机I/O及组件资格，不是Android Root实时抓包验收。

现有组合已接固定组件、Agent packet_inspect、日志/产物、中文任务标签；新增Android3项与日志映射仍仅编译，手机未更新/连接，保持0.1.32，最终集中验收。实时抓包尚未接，不能拿上述10项当作全功能完成。

下一步控制方式已整理ROOT_CAPTURE_RUNTIME_PROPOSAL.md（待确认）：libpcap+小型原生采集worker直接处理整包字节/时长预算、signalfd/timerfd/poll与结构化状态，tcpdump继续成熟离线分析，复用现有Root任务/日志/属主发布，不自写mmap或另建守护进程。此项细化并调整先前“tcpdump实时写盘”的控制职责，属于新增原生worker选择，按用户最初要求先确认，不提前实现。

## 2026-10-23 0.1.35 写盘结束/轮转边界补强

Agent离线提交6e8500b CI39829765714最终完整成功，含新增的字面过滤/选项隔离，主机离线共7项成功。继续核查真实采集前的写盘边界：派生tcpdump在退出/轮转核实flush/ferror，普通文件fsync后关闭；轮转更新当前dumper，关闭前清空信号处理使用的全局指针，避免复用已关闭dumper。回调仅检查stdio已有错误，不逐包刷盘。原上游缓存不改，替换次数严格验证；非普通输出不要求fsync。工具中文标签“分析网络数据包”已接。

本地Debug/Release/testAPK/lint通过；新主机三项（/dev/full不能成功、/dev/null可写、两段轮转保留20000包）及未修改上游/dev/full对照待CI，新合计10项不能提前记通过。强杀仍不承诺完整；Root实时抓包与任务发布/属主/异常、最终手机和性能资格继续，不把源码组件/离线分析标成实时抓包完成。手机未操作，仍0.1.32。

当前Debug SHA256 16ffa7525f0a1da1e2c45e1fc925eb3f73af52729ba39338653d2186e6ae0aa4。

## 2026-10-23 0.1.35 Agent 离线包分析接入

Agent 已注册 packet_inspect，普通UID独立原生进程解析会话PCAP/PCAPNG：字面BPF以--隔离选项，默认100/最多5000个匹配包，512MiB输入和30秒预算，无DNS/服务名查询。复用app.commands全局限流、现有前台任务/取消、工具账本产物关联及简洁详情。输入拒绝越界/符号链接/非普通文件；包数不等于扫描字节，超时及解析错误保留真实状态。没有把实时抓包能力标为已实现，也不为离线解析要求Root。

本地Debug/Release/testAPK/lint再次通过，双ABI ELF/16KiB及对应源码包通过。PacketNativePackageTest当前3项（版本、真实过滤/Agent字面过滤/原包保留、派发前越界拒绝）与CommandLogDeviceTest第五类产物映射仅编译，手机仍0.1.32、最后集中验收。e04bbba CI39828476867的主机五项离线步骤已成功，该CI最终完整成功，证据evidence/0.1.35；新增两项主机Agent参数风格/过滤选项隔离待下一CI。Root实时采集/预算/文件属主/取消/异常落盘/受控流量与性能仍继续。

## 2026-10-23 0.1.35 组合抓包组件接入（资格分阶段）

用户确认“我觉得可以组合都取最优”：libpcap底层采集/过滤、tcpdump成熟写盘/离线读取、Aurora Agent/任务/产物适配；不并行运行多个采集引擎，不据此引入AF_XDP或常驻服务。ROOT_CAPTURE_PROPOSAL已标组合确认。先接官方stable tcpdump4.99.7/libpcap1.10.7源码与SHA锁、NDK静态链接独立PIE、双ABI nativeLibraryDir分发、许可/源码头告知与对应源码包。Android版本0.1.35/code36。

ExternalProject隔离上游配置；派生FindPCAP严格使用当前目标ABI库，避免宿主pkg-config/pcap-config。Windows生成器按宿主选择NUL，私有TEMP/TMP修复win_flex并行ABI固定临时名冲突。无上游缓存修改。未增加可选OpenSSL/libsmi或蓝牙/USBmon/RDMA等采集后端依赖。二进制含TPACKET_V3支持字符串，只证明编译支持，设备实际模式待测。

本地Debug/Release/testAPK/lint及双ABI ELF/16KiB与源码包通过；新增主机离线五项接入CI（版本、匹配原包、不匹配、非法过滤、截断输入），尚未运行；新增Android普通UID两项仅编译。详见PACKET_NATIVE_BUILD.md。当前只是组件包装，不宣称Root实际抓包可用：Agent结构化抓包、受控Root运行/预算/取消/异常、产物归属、详情及吞吐/丢包/功耗仍继续。手机仍0.1.32，按用户要求最后集中验收，未连接或安装更新；无真实供应商调用。

本地当前Debug SHA256 1fcd62b5f0b4f01c7f4d246f1db053827308158268abb3de161cf337b8c75037。

## 2026-10-23 0.1.34 本地与主机资格完成，手机留最终集中验收

07adcd8 的 CI39825123468 最终完整成功，包括固定签名镜像/POSIX提取、现有 PTY/namespace/scope/恢复/挂载/GC、5项 ptrace 内核先决条件、4项实际派生 PRoot 生命周期、Android Debug/testAPK/Release/lint、双 ABI ELF/16KiB和对应源码包。实际 PRoot 四项覆盖启用/禁用 seccomp 的 tracer SIGKILL，以及 guest 正常退出37后两层 setsid 后代停止；使用 pidfd 验证原进程退出，不靠 PID 字符串轮询猜测。证据 evidence/0.1.34/proot-host-lifecycle-passed.txt；首次 host memset_explicit 构建失败保留。主机用 glibc、非 Android shmem、独立 fixture，不等于 Android 实际 Ubuntu/PRoot 或持久恢复已通过；PRoot executionReady仍 false。

本地173核心：166通过/7 Windows POSIX跳过，已带固定实际 Ubuntu 镜像重跑；Debug/Release/testAPK/lint通过。当前 Debug SHA256 88960c0af5f93fd200ef6bdc0896f739162e370dc7534784d0794e7eddb4d74c。Root 连接不可变快照修复、命令输出非法尾字节分类修复与新 Android 断言均仅编译，未更新手机；手机仍已验收0.1.32。最终补 RootMetadataDeviceTest4、RootConnectionTest和NativeCommandTest及既有Agent/Linux/PTY/恢复回归。无真实供应商调用。

用户对抓包提案要求“多处对比给我最好的”，尚非组件审批。已补 Linux/libpcap/tcpdump/PCAPdroid/Termux/Eta/DSHA 的来源与取舍，仍推荐 tcpdump4.99.7+libpcap1.10.7；1.11.0官网为testing。详见 ROOT_CAPTURE_PROPOSAL.md，等待对补充比较的确认，不先接入依赖或抓包二进制。五分钟是复用现有命令的第一阶段边界，不能写成最终长期抓包已完成。

## 2026-10-23 命令输出预览 UTF-8 修正（0.1.34）

NativeCommandRunner 旧逻辑在截断预览时反复移除末尾 1–3 字节并重试解码，可能把 0xff 等损坏字节隐藏成正常文本。改为复用 core 的 RFC3629 尾部边界检查，仅省略合法但未完整的 UTF-8 尾部；二进制/非法序列继续返回 text=null，原始产物不改变。去掉重复 decoder 与异常重试。新增 core 三项覆盖各宽度标量、非法尾部和 ANSI/空白；新增 Android 原生命令一项保留非法尾字节与精确产物，已编译待最终手机验收。

本地 core 173项，失败0、错误0、跳过7（Windows POSIX项），已以固定实际 Ubuntu 镜像输入重跑；Debug/Release/testAPK/lint通过。当前 Debug SHA256 88960c0af5f93fd200ef6bdc0896f739162e370dc7534784d0794e7eddb4d74c。CI39823929519 的实际 PRoot 主机测试在构建阶段失败：runner glibc 缺 Bionic 的 memset_explicit，0 项运行，原失败保留。补仅主机测试用 volatile 清零适配与禁止未解析符号检查；将实际 tracer 测试提前至 Android 全量构建前。四项运行结果仍待新 CI，不提前记通过。没有手机或真实供应商调用。

## 2026-10-23 0.1.34：PRoot 启动边界与 Root 连接一致性

PRoot 派生源码现在同时要求 EXITKILL 与初始子进程 PDEATHSIG/父 PID 复核，覆盖追踪选项安装前后 tracer 死亡；不修改上游缓存。新增 Linux 主机独立 tracer 五项验证，包含两层 setsid 后代、SIGKILL 的两种选项路径、SIGTERM，以及启动保护前/后死亡。1b43db0 的 CI39822710274 已观察这五项步骤成功，原生 PTY、namespace、scope/recovery/mount/guest-PTY/GC 步骤亦成功；该 CI 最终完整成功。它们是内核先决条件证据，不是实际 PRoot/Android 全链路验收，PRoot executionReady 仍 false。

修复 RootController 多个独立 volatile 字段在断开/重连间可能组合旧 Binder 与新身份/能力的竞态：身份、协议与 Binder 以单个不可变快照发布/清空。Linux 命令和 PTY 一次捕获同一快照的身份、执行与恢复客户端，票据不再二次读取连接。沿用原 Binder/SQLite/恢复协议，无新依赖或持久化格式。RootConnectionTest 补断开后的客户端清空及已捕获身份保留断言；仅编译，留最后集中真机运行。

本地启动保护版 Debug/Release/lint、双 ABI ELF/16KiB与对应源码包通过；Root 连接修改版 Debug/Release/testAPK/lint通过，0.1.34/code35 最终重建亦通过；Debug SHA256 434c717b2cfeda92841bc327ed790d301d6942e1afaf1928fd5619d15fd22066，双 ABI ELF/16KiB与对应源码包通过。新增实际派生 PRoot 的 Linux/glibc 主机编译与四项生命周期测试已接入 CI，当前仅 Python 编译检查，不提前记运行成功。手机仍 0.1.32，未连接、安装或控制手机，无真实供应商调用。下一步继续 PRoot 实际生命周期与普通 UID 能力边界、工具包资格、最终手机与长时性能/真实协议验收；不能把内核测试替代完整产品完成。

## 2026-10-23 PRoot tracer death prerequisite

Previous head 7c60f65 CI39820489556 completed successfully. PRoot now builds a checked derived tracee/event.c with mandatory PTRACE_O_EXITKILL in both default and seccomp ptrace option paths. Unsupported kernels reject startup instead of dropping this requirement. Upstream source remains unchanged; corresponding source includes the adapter. Local Debug/Release/lint and dual-ABI ELF/16KiB verification passed. This is a prerequisite only: no SIGKILL descendant runtime qualification, startup-race proof or Android PRoot acceptance is claimed; executionReady remains false. No phone actions; final combined device acceptance remains pending.

## 2026-10-23 0.1.33元数据回收主机资格完成，手机最后集中验收

23dd228完整CI39818966808成功：原scope/recovery/mount/guest-PTY31与新增GC11项通过；普通PTY8/namespace4、核心/实际签名镜像、Android Debug/testAPK/Release/lint及双ABI源码打包同样通过。原缺.recovery.lock导致4项拒绝回收的失败记录保留。见LINUX_METADATA_GC.md和evidence/0.1.33。Native已验证不等于Android Root/SQLite完整链路验收。

后续应用侧修正：扫描异常立刻关闭/清除游标，过期/关闭先移除登记，临时输出清理错误不掩盖原始失败；每个候选在扫描和删除前各读取解析一次，减少同阶段重复读取但仍重新校验hash/epoch。普通Binder拒绝新接口及Root部分暂存续清测试已补，Root专用共4项，全部只编译待真机；本地170核心（163通过/7POSIX跳过）、Debug/testAPK/Release R8/lint通过。Debug SHA256 84c690bcb6088917fdf474a56bec00c540b922aa6e713f487d7e891666925e3a，双ABI ELF/16KiB与对应源码包通过。应用侧后续修改的新CI另查。

用户最新要求手机最后集中验收，覆盖前面请求连接的安排；不要再为GC/小改逐轮索要连接，不操作或更新手机。目前设备仍上次已验收0.1.32。最终集中跑RootMetadataDeviceTest4、普通Binder/SQL、既有Agent/Linux/PTY/恢复回归；测试仅指定fixture记录、无付费API。继续PRoot完整生命周期/外部Root撤权/手机联网镜像/长时功耗压力/供应商缓存等剩余项；不能把GC主机通过写成所有功能完成。
## 2026-10-23 回收主机首次失败修正，真机留最后

用户最新决定：真机最后集中验收，不再为本轮GC请求连接或更新手机。d05d5f4的CI39804584434：原31项scope/recovery/mount/PTY通过；新GC11项中4项正常回收失败，原因是scope_state_recover会创建.recovery.lock，GC平面白名单漏列。补齐已知原生锁文件并让主机夹具显式包含它；原失败保留evidence/0.1.33。没有把拒绝回收记为成功，也没有降低未知/链接/挂载保护。

修正后本地core/Debug/testAPK/Release R8/lint成功，新CI待查；GC完整主机资格与真机仍不得提前标通过。Linux设置补Root重连提示和操作错误说明。当前手机仍上次已验收0.1.32，未进行任何新手机动作。
## 0.1.33 Root元数据回收实现，待主机/真机资格（2026-10-22，优先接续）

用户已确认LINUX_METADATA_GC_PROPOSAL推荐策略。接入AIDL末尾可选linuxMetadataProtocol1、Root单游标16项分页/30秒被动过期/显式finally关闭、App SQLite按候选ID筛除未确认run、Linux设置显式清理按钮。只选择已证明死亡的旧epoch，保留当前/未知记录。原生--gc持run flock验证scope恢复及stopped，原子移入Root私有linux-runtime-gc暂存后平面白名单删除；--gc-trash续做部分删除。稳定GC父目录锁不删除，防止child锁unlink/rmdir窗口让新旧收集器分锁；statx挂载ID拒绝目录/文件绑定挂载，不遍历未知树、链接或多硬链接。owner退役标记及workspace锁inode不删，不逐命令全局扫，不新增依赖/schema/常驻进程。

本地core、Debug/testAPK、Release R8和lint通过；新增原生主机11项（完整/部分删除、锁占用、未知/链接/硬链接/目录、模式、缺证明、文件/目录挂载、缺失路径等）及Root真机3项（当前epoch保留、旧已停记录删除且迟到请求不重建、SQL待确认保护后恢复再删），仅编译未运行，不能记GC已验收。已异步请求连接手机，当前ADB无设备；等待用户回复，手机仍已验收0.1.32。真机用选定fixture IDs，不扫用户历史；测试中只强杀已验证的Aurora自身Root服务PID以建立旧epoch，不动外部进程。运行前确认没有其他pending Linux任务。

上一内存锁提交2d3135d的CI39796653039完整成功，证据提交a63e109的CI39795463257成功。当前新增GC需自己的CI/native资格；Linux host脚本含mount/unshare测试，Windows本地不能冒充已运行。下一步先查新CI，再手机install-r运行RootMetadataDeviceTest/普通Binder与SQL相关回归；资格通过后再称可用。RootMetadataMaintenance取消时临时输出目录NonCancellable清理，App finally关闭游标；未知/不确定情况保留。PRoot/外部撤权/网络镜像/长期性能与真实供应商缓存仍未完。
## 2026-10-22 Root会话锁内存清理与磁盘GC提案（优先接续）

修复RootWorkspaceLocks按owner永久保留Mutex的内存增长：复用新core KeyedMutex，注册时统计持有者及等待者，正常/异常/取消后引用归零才移除；不删除磁盘FileChannel锁文件，也不把等待者留在旧锁、后续调用放到新锁。3项JVM测试含已持有时取消等待者、其他key不阻塞、1000个结束key释放、取消/异常收尾及8线程4000次跨key交错。核心170：163通过/7POSIX跳过，Debug/testAPK/Release R8/lint通过。无新依赖/schema，无新真机结果；手机仍上次已验收0.1.32，不安装当前未专项验证APK。

磁盘回收属于新的持久化清理策略，比较方案已写LINUX_METADATA_GC_PROPOSAL.md：建议仅回收已证明死亡的旧Root epoch、无SQLite待处理意图并通过native清理证明的运行目录，保留当前epoch/不明记录、owner退役屏障和workspace锁inode；有界显式维护，不逐命令全盘扫，不引入新常驻进程/新tombstone索引。清理尚未实现或执行，按最初要求提案推送后异步请求确认；等待期间不擅自删已有Root元数据。接续从用户对该方案的答复继续，另有PRoot生命周期/Root撤权/联网镜像/长期性能功耗/供应商缓存等既有待办。
## 0.1.32 真机并发恢复专项完成（2026-10-22，优先接续）

用户连接GT6SE后，保留数据覆盖0.1.32/code33；AgentLinuxDeviceTest5、LinuxRunRegistryTest5、CommandLogDeviceTest5共15项全部通过。实际验证：恢复本会话不停止另一会话的Linux PTY；显式恢复与终端完成回调共享同一证明不误报失败；Root探测输出关联、owner筛选/计数、SQLite重复完成与四类日志映射持久化通过，原批命令/PTY/超时/Root图片Agent场景同时回归。详见DEVICE_LINUX_RECOVERY_2026-10-22.md和evidence/0.1.32/device-scoped-recovery.txt。

Debug APK SHA256：4086f9f306620b4930441c2f160a6eaf1490e196be36ecc133e8036caa06f554。手机留主应用0.1.32，测试辅助包已卸载并打开MainActivity；未清数据/卸载主应用，未使用付费模型或修改Root授权。上一提交60fee70完整CI39793142166成功；f3f4e51的CI39794753012最后仍queued，证据提交不改变应用代码。下一步仍为PRoot完整生命周期、Root元数据GC、外部Root撤权、手机官方镜像联网下载、长时性能功耗和真实供应商缓存等，不把15项专项当全部功能已完成。
## 0.1.32 会话恢复范围与重复完成确认（2026-10-22，优先接续）

修复Agent linux_environment recover_runs原先遍历全局待处理项、可能停止其他会话终端的问题：SQLite按owner通过现有唯一索引选取/计数，Agent只恢复和显示本会话任务；设置页用户显式全局恢复入口保持原语义。修复probe_root/recover_root的任务输出关联被ToolRunStore白名单拒绝，加入linux_environment并仍禁止非命令类图片工具关联。修复显式恢复与PTY退出回调同时收到同一stopped证明时第二次完成误报失败：有活动记录仍严格匹配完整ticket；已完成记录幂等返回，不触碰其他租约。

0.1.32/code33本地core、Debug/testAPK、Release R8和lint通过；双ABI ELF/16KiB通过。新增SQLite双owner筛选和并发完成测试，扩展日志映射持久化覆盖四种工具/重复写拒绝，新增两项真实Agent/PTY的跨会话保护与同会话显式恢复测试。仅编译，尚未运行新增设备项；ADB无设备，已异步请求连接，待用户回复，不重复索要授权。手机仍为已验收0.1.31，未安装新APK。证据evidence/0.1.32。

上一提交60fee70的CI39793142166：已观察所有步骤成功，包括官方源码下载/缓存保存、Android构建/lint、原生资格与源码包；服务聚合状态最后仍in_progress，不能提前记完整成功。新提交CI另查。下一步手机连接后只install-r并执行AgentLinuxDeviceTest、LinuxRunRegistryTest、CommandLogDeviceTest；不使用付费模型，不卸载主应用/清数据。其后PRoot生命周期、Root元数据GC等既有待办仍未完成。
## 0.1.31 Agent Linux 真机接通与同轮环境固定（2026-10-22，优先接续）

已在GT6SE/FolkPatch保留数据覆盖0.1.31/code32。生产AgentExecution +脚本ModelTransport（无网络、无真实密钥）实际调用Root Linux批命令、交互PTY读写、图片发布、SQLite工具记录/协议历史：3项通过；环境固定/取消释放/启动清理保留未确认native租约：3项；最终构建再跑以上6+日志映射5=11项通过。普通91项在本轮环境固定实现后通过，最终状态展示/异常保留修正后针对性11项复验；不冒充最新构建重复跑全套。初始2项失败仅为fixture错把AgentExecution的UI临时投影当成已刷新消息；改查实际SQLite已持久消息后通过，原失败保留。见DEVICE_AGENT_LINUX_2026-10-22.md和evidence/0.1.31。

实现：首个Linux工具调用时懒固定共享Root代，用现有lease保护到整轮结束；每个命令/PTY另有原持久票据/租约，PTY可超出Agent轮次继续，未确认native run不因进程重启被释放。启动ready前仅回收没有linux_runs引用的进程内pin。命令任务卡片采用guest退出状态及有界stdout/stderr投影，超时/中断/未知不误标成功，原始JSON仍保留。Root图片选定导出/Bitmap解码/再次导出通过，传输临时副本已删，guest原图仍UID0。无依赖或schema变更。

核心167：160通过/7POSIX跳过；Debug/testAPK/Release R8/lint通过；双ABI ELF/16KiB与对应源码包通过。CI39790590613失败在Samba源码超时/503，非测试失败；新增官方actions/cache6.1.0精确SHA键的restore→原SHA校验→save，缓存命中和损坏拒绝本机已测，缓存未命中仍依赖有界官方网络。新提交CI另查，不宣称已修复全部外网问题。

下一步：PRoot完整生命周期、Root元数据GC、管理器外部撤权、手机官方镜像下载成功、长期压力功耗/真实性能与供应商缓存验证；不重复询问已批准架构。最终手机留主应用0.1.31，测试辅助包验收后移除；不清用户数据。用户要求常规进度少汇报，持续推进；仅新底层确认/阻碍/验收联系，工具进行中仍必要简短更新。
## 2026-10-22 Agent Linux 交互终端入口

新增稳定 terminal_start_linux 工具，默认/bin/sh、固定guest目录/环境、期限最多270秒、awakeMs默认0且不得超过期限。复用TerminalManager已验证的Root Linux PTY/SQLite租约/前台服务/VT/持久日志；后续由terminal_read/write/resize/stop/list操作，用户在现有终端页观看。与Android terminal_start分别命名，不偷偷切换后端。不新增依赖/服务/数据库schema。修正terminal_stop描述，区分Android进程组与Linux任务cgroup的清理边界。

新增2项核心测试覆盖默认值、参数字面保留、单次启动、参数/尺寸/期限/唤醒预算和禁止自定义环境；本地core、Debug、Release/R8、lint通过。未新增真机/真实模型验收；Linux PTY底层沿用0.1.30既有设备证据，不等同新Agent入口已验收。上一提交CI39790087660推送前仍运行。下一步仍需Agent端到端本地fixture/真机专项、整轮Linux代固定、PRoot完整生命周期、Root元数据GC；手机不覆盖未经本轮专项的构建。
## 2026-10-22 Agent Root Linux 批量工具接入

新增稳定 command_execute_linux 定义，复用已批准/真机验证的 LinuxRunManager、Root task scope、SQLite意图/租约和文件通道。工具在每轮固定列表中提供，运行时重新检查Root及已安装代；缺能力明确失败，无PRoot替代。argv绝对guest路径、最多128参数/64KiB、期限1..270000ms；固定cwd/HOME/tmp；派发前用同一run UUID关联任务输出目录。停止证明与命令成功分别返回，guest非零exit保留，超时/中断/无completion不伪造成功，不自动重放。当前按每条命令预留代及租约，尚未实现整轮Agent固定Linux代。

本地新增4项核心测试：退出与清理语义、参数拒绝、关联先于单次派发；core全套及Debug/Release R8/lint通过。上一轮CI39788741945完整成功。此次未跑真机或真实模型，不把已验证底层等同于新增Agent端到端验收。UI交互Linux终端入口、整轮版本固定、PRoot全生命周期、Root元数据GC仍待做；当前手机仍保留0.1.30已验收版。
# Aurora 接续记录

更新时间：2026-10-22。新对话先读 AGENTS.md、DESIGN.md、本文件和 IMPLEMENTATION.md，再检查源码与 Git 状态。

## 0.1.30 真机Linux与PTY资格（2026-10-22，优先接续）

真我GT6SE API36/FolkPatch保留数据更新完成。普通88、浏览器独立恢复1、Root原生命令/PTY5、Root连接与镜像复核2、实际Ubuntu命令3、实际Ubuntu PTY3、Root服务强杀/新epoch恢复不重放1、限定测试owner租约恢复1通过。详见DEVICE_LINUX_2026-10-22_FINAL.md和evidence/0.1.30。手机留0.1.30/code31主应用和共享Root环境；仅卸载test辅助包，临时传输镜像两份已删，无主应用卸载/清数据。

修复：Context应用基目录先toRealPath再逐组件拒绝链接，解决native state_directory ENOTDIR；UTF8只严格解码显式完整前缀，避免Android ICU内部消费残字节导致next丢字；Android测试去Files.readString/writeString；测试清理不掩盖主错误。手机下载镜像超时未通过，使用电脑已验证的同一官方归档走内部限额流式输入和原SHA/解包/属主发布链；不冒充手机联网成功。原失败证据保留。核心161：154通过/7POSIX跳过；本地Debug/testAPK/R8Release/lint/双ABI ELF/16KiB和源码打包通过。上一版CI39784460008完整成功，本提交CI另查。

下一步：Agent/UI Linux后端选择及工具接入、PRoot完整生命周期、Root元数据GC；仍未验证管理器外部撤权、手机联网镜像下载成功、Root图片实际解码、长期功耗/压力与真实模型缓存。Linux执行入口仍internal、executionReady=false，不需要重问已确认的架构。手机本轮专项已完成，不反复要求重连；后续确需设备新项目再明确说明。

## 2026-10-22 CI下载修复与真机专项准备（优先接续）

694ad43的CI39782148102：核心/签名镜像/PTY8/namespace4/cgroup与挂载31主机检查均通过，Android阶段因Samba下载talloc发生HTTP2 REFUSED_STREAM失败，不记完整CI通过。新增CI一次预下载：官方download.samba.org/pub优先、www.samba.org/ftp传输失败后备用，HTTPS/HTTP1.1、有界重试和原固定SHA256；CMake每ABI/构建类型仍核验同一哈希，只接受绝对本地归档路径。无新依赖或产品行为变化。

本机实际脚本下载及SHA通过，使用AURORA_TALLOC_ARCHIVE的Debug/Release/testAPK/lint通过（outputs/ci-source-prefetch-build-20261022.txt）。用户已授权直接开展真机Linux/终端专项；ADB仍无设备，已通过异步问题请求连接并解锁，等用户回覆即可继续，不重复索要Root授权。只install-r、不卸载/清数据；新Root服务连接需协议cap，若FolkPatch授权失效再告知用户。先Root安装资格/批命令3项/PTY3项，失效数据保留诊断，再处理观察到的失败。暂无新真机证据；Agent Linux仍未开放。

## 0.1.29 Linux PTY应用链路（2026-10-22，优先接续）

上一版7acd39c完整CI39780230312成功。现接AIDL末尾openLinuxTerminal/可选能力1、现有Root终端激活与FD传输、Linux运行票据/租约、guest完成证明校验、取消join后恢复和既有终端记录/UI/前台服务。内部startLinux支持同一套读写/窗口/停止，尚不开放UI新建或Agent后端选择，executionReady=false。原生completion补timedOut区分真正guest期限。无新依赖/数据库迁移/常驻进程。详见LINUX_PTY_APP.md。

核心159：152通过、7POSIX跳过，新增4项完成证明/协议失败测试；本地双ABI Debug/testAPK/R8Release/lint通过（outputs/linux-pty-app-full-20261022.txt）。新增Root实际PTY3项仅编译，普通门槛仍88；普通Binderfixture拒绝新特权接口。ADB无设备，没有新手机结果。下一步查本提交CI及native timedOut更新的主机验证，取得真实Ubuntu批命令3项和PTY3项Android结果，再开放Agent Linux工具；PRoot运行生命周期、Root元数据GC、撤权/服务死亡仍待办。无需重问已确认底层。

## 2026-10-22 Linux PTY原生组合（优先接续）

fc3aab6完整CI39773271882成功。新增原生--run-pty：复用PTY帧传输与scope监督，协调进程只释放自己的控制终端，guest新会话以TIOCSCTTY参数0取得终端/前台组，HUP在guest恢复、TERM显式加入。独立guard继续cgroup回收脱离session的子进程，传输退出不代替stopped证明。见LINUX_PTY_NATIVE.md。

新增Root主机6项及原有25项在CI39779614901通过，包括双监督SIGKILL后新进程恢复、不伪造completion；普通PTY8/namespace4也通过，完整Android阶段仍运行。旧CI39779288350被新提交取消。本地双ABI Debug/testAPK/R8Release/lint通过（outputs/linux-pty-native-build-20261022.txt），双ABI ELF/16KiB和对应源码打包通过。无新Android运行证据，未接Binder Linux终端与应用租约/终端记录，Agent executionReady=false。下一步核对完整CI，再复用现有Root终端FD/激活门接Linux票据，不另建守护进程。上轮真实Ubuntu三项Android专项仍仅编译。

## 2026-10-22 真实guest验收用例（优先接续）

83aa2c1完整CI39755532891已成功。新增RootLinuxRunDeviceTest三项：真实已安装CHROOT非零退出/UTF8续读/导出/版本编辑与旧版本拒绝/删除；超时后台写入停止与租约释放；就绪后取消、活跃编辑拒绝、退出后文件稳定。测试仅创建自身会话，不下载替代环境，缺Root能力或真实代直接失败，恢复失败保留租约。验收命令与范围见LINUX_RUN_QUALIFICATION.md。

本地:app:assembleDebugAndroidTest通过，记录outputs/linux-run-device-tests-20261022.txt（工作区外）。ADB无设备，三项未实跑，普通门槛仍88，不声称Ubuntu/Android链路已通过。下一步按该专项取得真实Root运行证据，再接Linux PTY与PRoot生命周期；Agent executionReady仍false。无需重问已确认的底层方案。

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

继续已批准架构，补齐rootfs数值UID/GID计划和新安装entries.json原子发布，提取时复核属主/权限/精确TAR类型，保持普通解包不chown。旧安装缺失元数据须在后续执行资格阶段补全，不自动视作可执行。新增4项核心用例合计129；本机127通过/2项POSIX跳过，官方ARM64样本扫描通过，Debug/lint通过。详见LINUX_IMPLEMENTATION.md。PRoot构建、Root挂载/进程监督与命令/PTY租约仍待继续，未新增手机验收或性能结论。

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
