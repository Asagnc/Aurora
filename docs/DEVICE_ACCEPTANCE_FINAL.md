# 最后集中设备验收（准备完成，当前不执行）

用户当前要求不操作手机/模拟器；本文件是执行顺序和证据门槛，不是开始连接/安装/读取密钥的授权。0.1.32安装与数据必须保留。源代码和材料身份见RELEASE_REVIEW_20261026。

## 顺序

1. 进入设备阶段后先核对安装包身份/签名、现有数据及实际Root状态。沿用已授权FolkPatch，命令/服务只针对Aurora和自有fixture，不等Magisk式弹窗。现有签名兼容才覆盖更新；不卸载Aurora，不用新密钥掩盖不兼容，不复制敏感值到日志。
2. 普通UID回归：数据库v9→v10保留Skills/消息/引用/Root意图、KeyStore与Ed25519黄金向量、离线ZIP＋本地旁路、原生来源页/指纹、系统快照/文件/图片/导出。自有私仓用用户指定的只读凭据，不能拿主机gh令牌冒充手机产品验收。
3. WebView/SAF：真实FormData/ArrayBuffer/Response逐字节、选择快照/导航/关页/删除、提供器拒绝/取消/空间不足、HTTP/HTTPS/SOCKS与错代理、Cookie隔离。同源frame可读和跨源SecurityError不绕过平台边界。renderer用例单独显式auroraBrowserCrashTest=true，先关闭所有真实页面，仅使用自有fixture；不得在用户网页仍打开时终止renderer。
4. Root命令/PTY/Linux：实际UID/身份代、字面argv、终端尺寸/取消/断连、cgroup/私有挂载/FD退出、App/服务死亡、未知恢复/删除屏障、版本租约、环境清理；不把PID消失等同全部资源已退出。普通PRoot硬链接资格失败继续不开放执行，不加仿真/替代。
5. 静态逆向：固定JRE/JADX/Apktool实际CLI、自有APK分析、共享只读挂载/租约、错身份/版本/取消/删除。先实际probe，资格只对当前身份/环境/版本有效。
6. APK构建：Linux页从审查材料SAF选择android-host-arm64.tar.gz，验证固定字节并安装；先当前Root自检Java/Compose/双ABI JNI、真实DEX类/16KiB，再构建自有项目/导出大于32MiB APK。实际JNI加载使用自有辅助包，清理时只卸载自有fixture，不卸载Aurora。正式发布密钥在手机Downloads，最后核实证书兼容后处理；私钥/密码不进入代码、参数日志或模型。
7. 代理：已有Root/chroot与显式auroraProxyTest/相关Root/Linux门控，实际HTTP/严格TLS/未信任拒绝/WS规则、公开CA摘要导出、PTY前台状态、固定产物/离线读取、精确停止/私钥缓存清理；全程显式loopback代理，不改全局路由、不装系统CA。未知Root退出/清理失败保留证据，不能直接删私钥目录冒充闭环。
8. 采集/发包/socket/轮转：自有目标与网络夹具，检查PCAP内容/顺序/计数/原始内核统计、精确socket快照多UID/未知、取消/短写/磁盘满/封口/发布中断、真实副本持久和退出后回收、Root死亡/未知恢复/删除。最长30分钟/512MiB/64段只是上限，长时资格至少覆盖一次完整预算和受控故障，不改全局网络兜底。
9. Frida：自有testOnly目标/ART、Root Binder封印配置与独立worker、attach/spawn、捕获前/后崩溃、resume意图/确认、worker/Root服务/App死亡及版本占用；只清理本任务精确确认且尚未resume的同boot目标。unknown保留，不自动重放或猜PID，禁止拿第三方App作默认目标。
10. 后台/熄屏/Thanox/管理器撤权/长时压力和取消/删会话联合矩阵；恢复顺序保证共享版本保留、用户文件不越界删除。只更改Aurora对应授权/管理状态，结束后清理自己的辅助夹具。

## 记录与通过条件

每批记录实际App/源码/原生包/工具版本、Root身份/boot/环境代、设备/内核/WebView、用例/门控/输入、开始结束/退出/未知/清理证明、产物SHA及失败日志。失败修复后重跑相关用例；跳过、缺资格、未知退出或清理失败不记成功。分开报告“实现”“主机资格”“Android运行”“长期压力”“正式签名”。

Release性能工作负载单列：正确率、TTFT/整轮/工具时间、CPU/RSS/耗电、采集吞吐/内核丢包；使用固定输入与可复查原始记录。禁止付费模型约定仍有效，缺真实供应商usage时继续写未测，不推断缓存/费用/性能领先。设备耗时受Root/内核/提供器实际故障影响，本计划不承诺固定总时长。
