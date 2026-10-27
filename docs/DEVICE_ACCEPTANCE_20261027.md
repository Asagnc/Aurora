# 2026-10-27 真机验收进展

状态：进行中。用户已授权真机测试，保留现有安装和数据。先测试/修复既有功能，再由用户说明UI问题；付费模型调用等待UI处理和用户配置密钥，拓展最后进行。本记录不是全部通过声明。

设备：RMX3850、Android16/API36、ARM64、FolkPatch。原安装0.1.32与本地Debug签名一致，已保留私有备份后覆盖安装0.1.40。原始数据库、日志、设备标识及备份只在被忽略的app/build/device-acceptance/20261026目录，不进Git。

## 已执行与修复

- 普通初测142项：134通过、8失败。包括真实KeyStore/JCA、SQLite迁移、FD socket夹具、tcpdump离线执行；失败没有计入通过。
- 浏览器初测31项：26通过、5失败，真实renderer终止用例通过。已修正Agent调用结束过早停止异步导航，以及Android可信filesDir别名导致下载恢复误判链接；相关异步6项、下载恢复4项复测通过。启动profile清理须在新进程独立执行，独立用例通过。
- FD socket测试先connect再设timeout；合成旧数据库夹具先移除依赖新列的索引；版本断言接受真实封装后的tcpdump argv[0]。针对性35项复测31通过，余4为当时尚未修复的下载恢复3项及平台验签1项。
- APK UID离线tcpdump的无意义setgid/setuid触发真实SIGSYS。仅在Android APK非特权且真实/有效UID、GID已相同时省略；Root和set-ID路径保留。离线过滤复测通过。
- Root基础23项初测5失败、1跳过。内核未启用PID namespace，明确跳过；基本Root/cgroup/私有挂载资格有实际通过证据。补齐自有会话目录及明确IPv4 loopback夹具后，原始发包1项、抓包7项均通过；不改全局网络配置。
- 抓包轮转真实工作进程完成后，清理器因本机缺proc children接口不能确认。增加waitid/ECHILD内核退出证明，活子进程无法枚举时继续失败保留；另修复旧worker PID复用为非leader线程时过早pidfd_open失败。两份原生夹具在手机执行通过；旧任务正常恢复和新的短时轮转合计2项复测通过。没有手删未知Root记录。
- Frida实际原生探针、自有脚本attach、ART String调用探针通过。libsu app_process的ART探针改用VM附着，避免普通App数据目录初始化。Android包spawn的stdio改为inherit；仅保留平台ART运行路径环境，避免helper缺BOOTCLASSPATH时abort。spawn仍在查验，不能宣称通过。
- Socket查询同一NETLINK后端新增活内核CONFIG_USER_NS=n资格：仅Root服务读取有界/proc/config.gz，缓存限服务生命周期；缺文件本身不作为资格。输出userNamespace=null及initial_user_namespace，不虚构inode。Java会折叠IPv4-mapped IPv6字面量，解析已保留AF_INET6；自有Java双栈socket使用真实IPv6族查询。真机当前socket精确UID用例复测通过，仍不代表历史PCAP逐包UID。

## 待完成及阻塞

- Skills发布者签名：平台Ed25519 KeyFactory只有KeyStore入口，无法导入外部SPKI；EdDSA别名不存在。已撤回失败的别名尝试，BC1.86方案待用户确认。既有AES-GCM凭据库可用，与模型密钥配置无关。
- Frida spawn、服务/App死亡和捕获/resume故障矩阵尚未通过。失败任务unknown保留，严禁猜PID回收。两个捕获前失败的自有夹具owner：bba8cd10-a465-4282-bf69-4bd707ef2346、89cde311-80cb-4ba5-8f4a-3700081e5e4c。后续接续必须核实当前记录，不因worker已退出直接删账本。
- Linux/PTY/元数据/离线Agent首批16项15通过、1失败；交互Agent夹具改为先读ready再送输入，并沿next游标增量读取，5项离线Agent复测全部通过。未使用真实模型。
- 正式共享工具安装发现Android NIO getFileStore抛SecurityException，改用File.usableSpace，同样空间门槛且零值拒绝；静态逆向下载切到已限定编译目录的downloadTool，沿用180秒预算。首批传输因超时/冻结连接中止失败，暂存按既有安装器清理，未发布或授予资格；当前前台重试。代理/静态逆向实际运行和APK手机构建仍待验收。
- SAF、长时完整预算、后台/Thanox/撤权、Release性能与正式签名继续验收。
- 不调用付费模型，不读取正式发布密钥，不改全局SELinux/网络/冻结策略。遇到App被冻结或测试进程退出时记录中断，不把未完成批次算通过。

## 本轮主机检查

352核心测试：336通过、16跳过、零失败；本轮未注入大型原生归档输入，故不冒充此前显式归档传输的主机证据。Debug/Release/R8/AndroidTest/lint完整223任务通过。编译后的352核心及231 Android @Test全部void；新增Android安装夹具仅编译不授予运行资格。两份原生清理夹具已在手机执行通过，Linux版本在Windows显式跳过，已加入Linux CI。CI及新的完整源码发布审查包尚待刷新；历史f561e10包保持历史证据。

## 实现依据

现有guard/cgroup/journal与NETLINK路线保持，不新增daemon或任务持久格式。Linux内核/proc children受CONFIG_PROC_CHILDREN控制，且退出并发下空列表不构成无子进程证明：[Linux手册](https://man7.org/linux/man-pages/man5/proc_tid_children.5.html)、[waitid语义](https://man7.org/linux/man-pages/man2/waitpid.2.html)。NETLINK UID按查询socket的user namespace映射：[Linux inet_diag实现](https://github.com/torvalds/linux/blob/master/net/ipv4/inet_diag.c)；USER_NS配置：[Android内核源码](https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/init/Kconfig)。Frida采用固定17.24.3/Java bridge7.0.13，已核对其打包源和[固定Linux后端源码](https://github.com/frida/frida-core/blob/17.24.3/src/linux/linux-host-session.vala)。
