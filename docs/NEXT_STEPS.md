# 当前接续清单（2026-10-26，0.1.40）

只记录当前状态；历史实验/失败/修复见PROGRESS、IMPLEMENTATION和docs/evidence，不把早期缺失当成当前待建设。

## 工作约定

- 在现有main连续实现、验证并推送；例行修复直接做。新底层/依赖/持久化选型先比较确认；已批准方案不重复问。
- 当前不连接、更新或操作手机/模拟器，不重装Android Studio/WSL，不调用真实付费模型。
- 最后集中真机验收，保留安装/数据。手机已验收版本0.1.32，代码0.1.40；FolkPatch已授权，不等待Magisk式弹窗。
- 长期发布密钥在手机公共Downloads，留到最终验收；现在仅unsigned-review，不生成替代正式密钥。
- 只有实际当前Root身份/服务代/环境代/工具版本资格通过才开放执行；安装、编译或主机CI不授予手机资格。
- 失败/取消只清理自己的暂存；未知占用、退出或清理失败保留恢复记录并阻断相应删除。

## 已接入，不重复建设

| 范围 | 当前实现及主机证据 | 尚未手机验收 |
| --- | --- | --- |
| Frida Root Binder/活动账本/Agent | schema8不可变票据、sealed memfd/FD、独立guard/cgroup/journal、恢复/删除屏障；protocol2 spawn两阶段记录；Agent status/recover/probe/attach/spawn。真实Frida28组及spawn记录5组CI40158211528成功。 | Root/ART、真实自有包spawn、服务死亡/撤权/取消完整矩阵。 |
| 网络底层/轮转 | libpcap worker、tcpdump离线；http_request；原始发包/PCAP精确回放；socket_owner_root为真实NETLINK当前socket快照；capture protocol3/schema9同表轮转、BOOTTIME、封口/副本持久后回收。网络/guard/capture专项与完整CI均已通过，见各实现记录。 | Root采集、UID查询、长时/丢包/熄屏、恢复/删除/发布失败矩阵。 |
| 代理 | mitmdump12.3.0 fixed ARM64、不可变regular loopback profiles、HTTP/WS字面修改、严格TLS、自有HTTP/HTTPS/未信任CA/WS自检、产物/离线读；同共享安装器/PTY/Linux租约/Agent、公有CA SHA导出；私钥缓存只凭既有Root ticket/stopped/completed.scopeRemoved清理。ARM64产品专项40294444272成功。 | Android Binder/PTY/chroot、CA及异常清理；不自动安装系统CA/改路由。 |
| 静态逆向 | 固定JADX/Apktool/JRE，共享只读版本/持久租约/当前Root资格与Agent分析。 | 实际JRE27/CLI/自有APK/故障矩阵。 |
| APK构建 | 同共享安装器android-build，JDK25/Gradle9.8.2/AGP9.4.1/SDK与ARM64原生host，外来SDK ELF拒绝入口；实际Java/Compose/双ABI JNI/DEX/16KiB/readonly布局在40294449390通过。Agent apk_build/apk_export（512MiB/120秒）已接。Linux环境页直接SAF选择固定180MiB原生归档，复用同一staging，不走64MiB普通附件、不新建导入缓存；实际固定字节流式导入本地已通过。 | 手机chroot/JVM/构建、JNI加载/故障、正式签名与保留数据覆盖安装；候选不冒充完整Android补丁NDK等价。 |
| Skills | 已批准私有GitHub API/仓库限定PAT、现有KeyStore及schema10来源/秘密归属恢复；用户钉住公钥/JCA Ed25519验签，支持本地ZIP＋本地旁路完全离线验签；原生配置/撤销/指纹、Agent仅选既有profile。发布前重核策略，历史本地版本继承当前绑定，防止回滚脱离验签；已运行快照继续持有旧版。 | 实际Android JCA/KeyStore、v9→v10、恢复/撤销UI、自有私仓；新增用例仅编译。 |
| 浏览器 | 独立WebMessagePort/文档版本、二进制上传/流式下载、原生chooser/精确URI快照、背压/归属/账本恢复。新同源/跨源frame边界、实际renderer死亡用例已编译；后者显式auroraBrowserCrashTest=true且真实页面全关才执行。 | WebView/SAF/提供器/代理/导航/跨源边界/renderer完整矩阵；不绕过同源策略、不重放未知副作用。 |
| 系统快照 | system_snapshot按需普通Android API：系统/硬件、近似RAM、App data文件系统空间、电池原始属性、缓存Root连接状态。无shell/轮询/新增权限/Root连接。 | 两项普通UID用例仅编译；不宣称任意系统写API或包/权限管理已实现。 |
| 其他基础功能 | 普通/Root命令、PTY、Root Linux、文件/图片/日志、后台Agent宿主、模型10家官方目录已接；不从目录推断账号能力、缓存命中或性能。 | 后台/Thanox/长时压力与真实供应商路由/usage。 |

主要入口：ROOT_MANAGED_BINDER、ROOT_MANAGED_TASKS_IMPLEMENTATION、FRIDA_SPAWN_PROPOSAL、FRIDA_AGENT_ACCEPTANCE、CAPTURE_ROTATION_IMPLEMENTATION、NETWORK_PROXY_IMPLEMENTATION、STATIC_REVERSE_IMPLEMENTATION、APK_BUILD_PROPOSAL、SKILLS_IMPLEMENTATION、SKILLS_TRUST_FORMAT、BROWSER_TRANSFER_IMPLEMENTATION、BROWSER_CHOOSER_IMPLEMENTATION、BROWSER_ASYNC_IMPLEMENTATION、SYSTEM_SNAPSHOT、PROVIDER_REVIEW_20261025。

## 发布及最新验证

- 完整代码CI成功基线18f63ef/40322623537；本轮后续变更需以最新core.yml和APK产品专项实际完成状态为准，取消不算成功。
- 本地实际351核心336通过/15跳过（含显式180MiB原生归档输入）；Debug/Release/R8/AndroidTest/lint220任务通过。新增编译后JUnit签名检查覆盖351核心和228 Android @Test，无非void方法；不会再把返回Throwable/Nothing的用例默默忽略。Android用例仍未执行。
- 发布材料已接SDK原生包＋41份固定源码输入、代理官方分发＋584份固定源码/候选wheel/Cargo超集、精确自有git源码快照、Frida/原生源码重链接及重建、实际APK资产/16KiB/身份/图标链与批准像素校验，全部纳入manifest/SHA256SUMS。
- 正在刷新当前完整审查包；旧release-review-25bcbc5-sdk不代表最新代理/Skills接入。保持unsigned-review/releaseReady=false，设备和正式签名未验收。

## 最后集中验收与明确边界

- 集中执行Root/ART/自有spawn、静态逆向、APK构建/JNI/签名覆盖更新、代理、采集轮转、WebView/SAF、管理器撤权、未知恢复、取消/删除/服务死亡、熄屏/后台/Thanox和长时压力。当前不因看到清单就请求手机。
- 固定Release性能工作负载尚未实测：正确率、TTFT/整轮/工具时间、CPU/RSS/耗电、采集吞吐/内核丢包；真实模型调用继续遵守禁止付费调用约定。
- 普通PRoot在该Android16硬链接资格不足时executionReady=false；不加入link2symlink/QEMU冒充成功。
- 历史逐包UID关联需后续独立eBPF资格；当前只做真实socket快照。任意跨源frame访问、私有GitHub release asset API、Sigstore/插件市场及任意系统写API未声明支持，不能用已有命令/公共下载能力冒充完成。
