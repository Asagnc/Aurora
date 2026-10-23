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
