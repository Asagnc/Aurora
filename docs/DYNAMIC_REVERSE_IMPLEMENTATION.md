## 2026-10-23 主机6项真实会话通过、Java桥开发期构建

eb99c2e的CI39965185125中frida-session job已实际通过全部6项（身份错配含5次快速启动子场景），证据ci-39965185125-frida-job-passed.json及ci-39965185125-frida-tests-passed.txt。该时刻整体workflow尚在运行，不把job成功当完整CI成功。之前39962631872、39963441947、39963997280的失败仍保留；后续39964474273已定位上游半启动关闭竞态。

tools/frida-bridge固定Java桥7.0.13、frida-compile19.1.0和Frida Node17.24.3完整package-lock闭包。编译器19.1.0来自官方npm当前版本，桥/引擎版本编号各自独立，不能互相比数字。WindowsNode24.15使用已核实SHA256的官方N-API8 binding实际编译两次，235893字节Frida bundle、局部相同SHA256。新增prepare-frida-node.py仅安装固定来源的Windows/Linux x64开发期binding，核对摘要、长度与单文件归档；npm一律ignore-scripts，不运行安装钩子的prebuild-install/make回退。手机不新增Node/npm/Python。

Java桥entrypoint只安装Java对象、报告初始availability并等待一次明确aurora:run消息。它尚未与native post通道相连，不能称已能运行AndroidJava脚本。新第7项主机用例将实际加载该bundle到自有普通C目标，要求报告Java不可用，防止只从编译成功误报能力；仍待新CI，AndroidART独立待真机资格。生成bundle和node_modules不入Git/不打包APK，许可证/对应源码仍是发布门槛。

当前ARM64worker strip89134752字节、单库ZIP36053008字节，见frida-attach-current-report.json与readelf；已显式禁止可执行栈，外层16KiB/系统依赖资格不代替注入端资格。Root接入必须在现有270秒总预算中预留清理窗口，不能让观察期+清理超过派发截止；普通Root命令短kill间隔不直接作为此worker的完成证明。

## 2026-10-23 本机设备早返回/其他后端未完成启动的关闭竞态

真实主机第二轮已5项通过，身份错配用例仍SIGSEGV。调试栈定位frida_fruity_pairing_browser_stop(self=null)，由DeviceManager立即关闭时取消未完成的其他设备后端启动引发。Frida17.24.3源码find_device会在本机设备added时早返回，而enumerate_devices等待ensure_service完成；不能把拿到local device当整个manager已启动。

现先等待enumerate_devices的启动完成事件，再选择本机设备；取消发生在启动期间时，清理使用独立有界域再等启动结束后关闭。未能确认启动/关闭时只报cleanupConfirmed=false并退出隔离worker，不在半初始化后端上运行不安全close/finalizer，也不假装有完整收尾。不是固定sleep/轮询，不修改上游或降低用例断言。身份错配用例增加5次快速启动/退出子场景。

源码依据：https://github.com/frida/frida-core/blob/17.24.3/src/frida.vala 的find_device/enumerate_devices/ensure_service/stop_service；失败栈见CI39964474273的frida-session job。修订交叉链接通过；真实主机CI待重跑。Root取消将来必须给此专用通道留出清理窗口并保存未知结果，不能直接套用普通命令的短SIGTERM/SIGKILL间隔就宣称已回收注入。

## 2026-10-23 真实主机用例暴露的事件上下文/关闭顺序修复

94c547f CI39962631872失败：6真实Frida用例中1通过、1失败、4错误，多项退出超时，身份错配仅留下error无completion。不能记成生命周期验证通过。

核实src/frida-glue.c后修复两项：frida_init(FRIDA_RUNTIME_OTHER)在默认GMainContext自建线程，因此Aurora不能再在同一默认context运行控制循环。现为信号/期限/启动/清理计时创建独立control context，避免争用；Frida仍用其默认context。其次frida_deinit会关闭GLib，不能之后再构造JSON/销毁GLib对象；现先frida_shutdown排空其回调/延迟释放并停止线程，在GLib仍可用时记录最终结果和释放控制资源，最后frida_deinit。外层仍须观察进程真实退出，最终JSON本身不代表worker已经退出。

新ARM64交叉链接与外层依赖/16KiB检查通过；真实会话回归推送后重跑，不缩短用例或去掉清理断言。未打包到APK、未操作手机。

## 2026-10-23 Frida附加会话原生流程（未打包，主机运行CI待核实）

原生worker已新增attach <pid> <startTicks> <absoluteScript> <durationMs>入口，固定1..270000ms、1MiB普通脚本快照、最终组件不跟随链接、拒绝空/NUL/非法UTF8。接收已有目标时持有pidfd，附加前和加载脚本前复核PID开始时间/原进程存活；Frida API按PID附加，无法原子传pidfd，因此不能承诺消除“检查与attach之间”所有竞态，但旧目标退出后不会按已知复用PID继续加载脚本。

GCancellable跨线程取消、条件变量等待，无定时轮询。SIGTERM/SIGINT只用自管道通知事件循环；运行期限结束和外部取消分别记录。worker线程同步调用Frida、主循环处理信号/截止，清理使用独立10秒取消域，不复用已取消的执行域。最终硬截止仅用于挂起兜底，触发时不产生清理成功证明。

脚本消息64KiB、二进制16KiB、JSONL总4MiB且预留最终摘要空间；显式序号/丢弃数/预算错误，脚本异常不是成功。额度只限制转发日志，不是注入端脚本CPU/RSS或Frida接收分配的硬沙箱。unload/detach/manager close分别确认，不能用worker退出代替；原有目标不被kill。退出码0仅表示已加载、观察期限结束且完整收尾/输出成功，不代表脚本业务意图已达成。

核实Frida17.24.3 src/frida-glue.c：frida_unref可能把释放排到Frida私有context，不能用普通g_object_unref假设同步清空。当前对Frida对象采用frida_unref，并在最终结果/状态销毁前调用frida_deinit排空其私有上下文；避免迟到回调访问已清除的锁。依据：https://github.com/frida/frida-core/blob/17.24.3/src/frida-glue.c 与 https://docs.gtk.org/gio/class.Cancellable.html 。

Android ARM64交叉链接、系统依赖/外层16KiB检查通过；strip89134368字节，单库ZIP36052667字节。本机Windows没有执行这些ARM64字节。新增3项纯Linux输入测试和6项真实Frida/Linux自有目标测试（hook/取消/语法错误/身份错配/消息预算/目标退出），Windows按平台跳过，完整CI推送后才可核实实际运行。

Linux主机Frida devkit固定17.24.3，78853964字节，SHA256 9a11abf474899a33af6dd9af8a064448b23947a29a3af747eb3c7851591ffec2，仅CI夹具用，不随APK交付。测试目标为仓库自有C程序，finally只杀该测试自己创建的进程，不能拿用户应用代替。主机通过也不等于Android16注入通过。

上轮526c4c4完整CI39960629598已成功，证据evidence/0.1.40/ci-39960629598-passed.txt。当前仍未接APK/Root协议/Agent；spawn/Java桥/持久恢复与许可证闭包继续保留，不显示动态ready=true。未操作手机或真实供应商。

# 动态逆向实施状态（2026-10-23）

状态：用户要求多处比较取优，按DYNAMIC_REVERSE_PROPOSAL的原生独立worker路径先构建资格。仅源级/主机交叉链接已实现，未加入CMake/APK/Root协议/Agent工具，不能使用动态逆向，也未执行手机。不是安装半成品，没有在手机上放置文件或常驻服务。

## 已完成

- 官方Frida17.24.3 Android ARM64 devkit压缩60903232字节，下载后实际重算SHA256与固定值一致；归档只有根目录、静态库、头文件、GIR及示例。静态库342232926字节，不把源包压缩量当安装增量。
- 新app/src/main/cpp/frida/worker.c最小构建资格程序：--version或Root本机设备/进程探测，使用官方local device C API，不开启server端口、不附加/启动目标。进程记录最多512条，名称按1024输入字节截断并修复UTF8，JSON-GLib编码；20秒原型探测截止。此截止不是将来脚本卸载与detach的完整收尾协议。
- scripts/build-frida-probe.py固定摘要/长度，仅接受该固定归档清单与普通文件，拒绝链接/路径/重复条目，临时展开在受控构建目录、finally自动回收；复用现有已测试的来源下载/失败partial清理函数。显式--download才下载缺失官方包，没有未验证latest。
- 使用现有NDK30.0.16248370/API36在Windows交叉链接通过，-Wall/-Wextra/-Werror、栈保护、RELRO/NOW、16KiB段对齐。strip后89123808字节；模拟单个lib ZIP压缩36051409字节，未去符号150256512字节。只有libc/libdl/liblog/libm系统依赖。这只是当前最小worker，完整脚本/Java桥及APK/安装空间还会变化，不能称完整动态模块体积。
- llvm-readelf确认外层ELF64 AArch64、/system/bin/linker64与所有LOAD 0x4000对齐；没有证明内嵌注入agent、符号加载、SELinux、Root权限或实际Android运行。证据evidence/0.1.40/frida-probe-report.json与frida-probe-readelf.txt。
- 新2项Python离线测试覆盖未知/重复清单与已知名称链接。CI已增加同一NDK交叉构建和报告产物，不上传巨大devkit/原型二进制、不把它混入已安装Aurora功能。
- 原型构建后清理重复展开开发包与旧strip二进制，释放434501141字节；保留已验证压缩源归档与当前交叉构建产物供后续实现。临时展开/未strip/ZIP测量文件由脚本自动回收。

## 来源与许可状态

frida-core17.24.3原始COPYING已读取，含wxWindows Library Licence3.1的例外；不能仅套用此前其他原生组件的许可记录。devkit归档本身未携带全部第三方许可，发布前必须核对Frida及静态闭包、Java bridge许可证与对应源码交付，不用单份core COPYING冒充全部已完成。尚未向APK分发此worker。

官方C示例仅用于核实链接参数/API；其示例JS仍出现旧版Module.getExportByName，不能直接复制当Frida17脚本兼容证明。后续脚本按当前JS API与自有目标资格。

## 必须接着完成

1. 固定Java bridge7.0.13来源完整性/依赖/许可、开发期脚本组合及原生脚本/Java各自能力资格；没有在手机安装npm/Node。
2. worker attach/spawn/script/message与错误阶段、目标PID/开始时间校验、有界事件流；已有目标和本任务spawn目标的退出策略必须区分。
3. 生命周期协议：取消/超时/服务死亡时卸载、detach、worker退出；spawn尚未resume的恢复，记录未知状态与禁止重放；脚本造成的副作用不能承诺自动回滚。
4. 接现有Root任务监督与Agent/UI产物，完整许可/对应源码、打包实际APK体积与16KiB注入端资格。仅当前构建通过不能提前展示ready=true。
5. 自有native/Java目标、并发、日志风暴、断连/取消/删除/后台/熄屏最终集中手机测试及Release RSS/时延。

本轮无手机操作、无真实供应商请求。当前CI推送后另查。
