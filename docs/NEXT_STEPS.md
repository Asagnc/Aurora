# 当前接续清单（2026-10-23，0.1.38）

本文件是当前摘要；PROGRESS/IMPLEMENTATION中按时间倒序的早期记录可能包含已完成的旧待办，不应将它们重新当作当前缺失功能。细节以对应实现/证据文档为准。

## 工作与验收约定

- 继续已授权实现、测试并推送main，少做常规汇报。新底层/依赖/持久化选择仍须检索比较、形成可审查方案并让用户确认，例行修复不重复问。
- 当前不连接、更新或操作手机，也不重装Android Studio/模拟器/WSL。所有最终手机测试集中进行；保留Aurora数据和安装，测试辅助包完成后清理。
- 手机最后已验收版本0.1.32；代码0.1.38。用户已在FolkPatch应用内授权，不能等Magisk式弹窗。真的需要处理授权/阻碍时优先联系用户。
- 本轮不调用真实付费模型，不要求用户发送密钥。gh已登录，不使用聊天历史泄露过的PAT。
- 不能从编译、主机夹具或UI推断Android性能领先、所有模型最高缓存、任意网站无限访问或全协议解密。

## 本轮已经实现

- Root抓包：libpcap独立worker、严格整包预算/截止/取消、结构化统计/截短计数、Root选定PCAP属主发布与Agent离线tcpdump分析。普通UID不提供实时抓包，无VPN。见ROOT_CAPTURE_IMPLEMENTATION.md。
- 浏览器异步：每次独立WebMessagePort、精确origin/页面revision、awaitPromise选择、一次结果、关闭/截止/取消收尾；同步保持直接路径。加载从50ms轮询改回调StateFlow，外层取消不再被工具/批处理吞掉。见BROWSER_ASYNC_IMPLEMENTATION.md。
- 原有Root命令/PTY、Root Linux执行与Agent入口、会话文件通道、图片发布、事件日志、Skills内容版本管理、后台Agent宿主等已实现，勿从早期记录重新列为从未做过。

- 0.1.38 Agent文件供给/页面流式下载已接入，64KiB背压与会话产物发布，实际事件合并显示字节；用户原生chooser及崩溃遗留回收仍未做。新3项核心含14个生产JS子场景通过；4项Android用例仅编译。

## 当前证据

- 9517ea9完整CI39909014575成功，包含核心/签名镜像/POSIX/PTY/Root原生主机/PRoot内核与生命周期/抓包/Android Debug、Release R8、testAPK、lint及ELF/源码检查。证据evidence/0.1.37/ci-39909014575-passed.txt。
- 本地核心187项：180通过、7个POSIX跳过。新增生产JS主机14场景实际运行，属于同一个JUnit测试中的子场景，不重复加到187总数。
- 随后仅补测试任务对mjs文件的输入追踪，以及Android跨域导航夹具，core/testAPK/lint本地再次通过；2214641完整CI39910230241也已成功，见evidence/0.1.37/ci-39910230241-passed.txt；文件传输提案仍只涉及文档，没有实现。
- Root抓包主机外层12项、内层私有netns10项成功；新Root真机7项与BrowserAsyncTest六项仅编译，没有执行手机。

## 尚未实现，建议继续的顺序

1. **浏览器文件传输**：Agent与用户页面的上传/下载、会话归属、可观察进度/产物、取消/删除协调。先比较onShowFileChooser/精确URI授权与字节传输，核实程序点击文件输入的用户手势要求；下载需比较现有OkHttp、WebView网络上下文与系统下载器，正确处理各会话Cookie、HTTP/HTTPS/SOCKS代理、重定向和POST/blob。不能把GET重发或直连降级当所有下载都支持。新的通道/持久化设计先确认。组合方向已获确认；0.1.38已实现Agent二进制上传/页面下载，详见 BROWSER_TRANSFER_IMPLEMENTATION.md。接续原生chooser/精确URI快照、崩溃遗留回收、诊断与实际WebView资格；勿重复确认同一方案或把Agent路径当全部浏览器传输。
2. **逆向/CTF工具包**：JADX、Apktool、原生库分析、Frida等实际ARM64运行器、版本化共享依赖与Agent工具，不用普通SKILL.md假装这些二进制已安装可用。
3. **手机APK构建链**：实际ARM64可用JDK/aapt2/D8/R8/签名/安装链，含产物和失败诊断；Linux基础环境存在不等于已能编译APK。
4. **网络进阶**：发包、HTTP修改/重放、TLS解密、UID归属和长时轮转。抓包不代表已提供这些功能；按需独立运行，不能给每个模型请求增加重型代理。
5. **模型与缓存**：刷新当前稳定在售型号/端点能力、网关能力探测及各家缓存/期限策略。真实账号路由/命中/费用/延迟验证留用户允许真实连接之后；现有序列化/原生历史/前缀策略不能称全部模型最优。
6. **其他增强**：Skills私有认证/签名、更多结构化系统API工具、浏览器跨站frame与renderer完整矩阵、正式发布签名/图标检查。先核实当前实现，不能因历史待办重复建设。

## 已实现但最终验收尚未完成

- Root抓包7项、异步WebView六项（含跨域与同URL刷新）、后续Skills/图片/日志等累计设备用例的增量集中回归。
- Root元数据回收新增流程、管理器外部撤权、未知恢复、会话删除、熄屏/后台/Thanox组合与长期压力。
- 普通PRoot的Android16完整资格仍不足；本机此前普通硬链接探测失败，executionReady=false，不加link2symlink/QEMU等隐式替代。Linux主机成功不能让其在用户手机上自动可用。
- 固定工作负载的Release性能：Agent任务正确率、TTFT/整轮时延、工具时间、CPU/RSS/耗电、抓包吞吐与内核丢包。未测不宣称优于Eta/DSHA。
- 手机镜像真实联网、真实SAF/提供器/断电及崩溃恢复边界，不能只用本地模拟故障替代。

## 已批准，勿重复确认

浏览器组合传输（原生chooser + 有界二进制Agent供给/页面下载）；原生SQLite WAL；仅任务期间有界CPU锁；前台Agent服务；共享版本化SKILL.md；Ubuntu共享代/Root原属主/chroot私有挂载和任务cgroup；Root批量文件通道；有界旧元数据GC；Root-only libpcap worker+离线tcpdump；浏览器每次独立WebMessagePort。对应方案文档均在docs。

最终手机操作分别参照ROOT_CAPTURE_ACCEPTANCE.md及BROWSER_ASYNC_IMPLEMENTATION.md；不得现在因看到清单就请求连接手机。
