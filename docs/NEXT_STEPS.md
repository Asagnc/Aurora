# 当前接续清单（2026-10-23，0.1.40）

本文件是当前摘要；PROGRESS/IMPLEMENTATION中按时间倒序的早期记录可能包含已完成的旧待办，不应将它们重新当作当前缺失功能。细节以对应实现/证据文档为准。

## 工作与验收约定

- 用户明确不长期保留无用安装半成品：成功才发布，失败/取消清理暂存；清理失败或未知占用保留并报出。Agent通过管理接口安装/更新/移除共享只读工具，通过普通文件接口管理共享工作区，运行中版本回收需真实退出证明。

- 继续已授权实现、测试并推送main，少做常规汇报。新底层/依赖/持久化选择仍须检索比较、形成可审查方案并让用户确认，例行修复不重复问。
- 当前不连接、更新或操作手机，也不重装Android Studio/模拟器/WSL。所有最终手机测试集中进行；保留Aurora数据和安装，测试辅助包完成后清理。
- 手机最后已验收版本0.1.32；代码0.1.40。用户已在FolkPatch应用内授权，不能等Magisk式弹窗。真的需要处理授权/阻碍时优先联系用户。
- 本轮不调用真实付费模型，不要求用户发送密钥。gh已登录，不使用聊天历史泄露过的PAT。
- 不能从编译、主机夹具或UI推断Android性能领先、所有模型最高缓存、任意网站无限访问或全协议解密。

## 本轮已经实现

- Root抓包：libpcap独立worker、严格整包预算/截止/取消、结构化统计/截短计数、Root选定PCAP属主发布与Agent离线tcpdump分析。普通UID不提供实时抓包，无VPN。见ROOT_CAPTURE_IMPLEMENTATION.md。
- 浏览器异步：每次独立WebMessagePort、精确origin/页面revision、awaitPromise选择、一次结果、关闭/截止/取消收尾；同步保持直接路径。加载从50ms轮询改回调StateFlow，外层取消不再被工具/批处理吞掉。见BROWSER_ASYNC_IMPLEMENTATION.md。
- 原有Root命令/PTY、Root Linux执行与Agent入口、会话文件通道、图片发布、事件日志、Skills内容版本管理、后台Agent宿主等已实现，勿从早期记录重新列为从未做过。

- 0.1.38 Agent文件供给/页面流式下载已接入，64KiB背压与会话产物发布，实际事件合并显示字节；0.1.39已补原生chooser、精确URI快照及旧上传快照有界回收；0.1.40新增账本关联的下载中断恢复；未登记/类型不明记录不自动删除。0.1.38新4项核心含14个生产JS子场景；4项Android用例仅编译。

- 原生chooser现在提供会话/共享资源与SAF入口，选定文件流式复制、原生URI保留到关页/删会话；未交付的选择在导航取消。新增5核心与5项Android回调生命周期用例，详见BROWSER_CHOOSER_IMPLEMENTATION.md；手机仍未执行。

- 下载现在先登记目录再创建文件；启动/会话Agent开始时有界处理已中断账本，保留完成产物和uncertain状态。schema7部分索引避免重复扫描完成历史；见BROWSER_DOWNLOAD_RECOVERY.md。

- 传输诊断新增精确白名单错误码，网页异常原文不入账本；修复 Agent 上传 accept=*/*，新增生产脚本通配符/错误形状验证。下载展示名/MIME已补齐，严格握手、规范路径产物入口和名称/类型提示，不改固定磁盘名；新增1项Android仅编译。真实WebView/SAF资格仍待最终手机验收。

- 静态逆向方案已批准；来源/准备与共享安装管理已实现；Agent可安装/更新/删除/清理，持久引用与删除屏障已连接，原生只读挂载与reverse_analyze已接入；runtimeReady必须经过当前Root身份/环境代/bundle的真实CLI启动资格，手机尚未执行。

- 探测/分析统一无图形JVM参数；已做固定归档43 ELF名称盘点（不是装载验证）。清理失败现在在Agent错误和状态中可见，源码获取失败也清理自己创建的partial。

- 修复任务固定Linux代与逆向probe的全局当前代错配；状态观察不取租约，probe与分析共同固定同代。扩展Android版本切换断言，最终设备执行仍待进行。

- 逆向资格现按Root身份/环境代/bundle隔离，最多32项；旧probe完成必须匹配当前尝试，删除/重试/淘汰后迟到结果不能恢复资格。原持久任务删除屏障不变；新增3项核心回归。

- Linux执行/逆向分析须明确完整退出字段才判成功；非法超时和多余参数在产物登记前拒绝。新增2项核心回归已通过，原生协议与持久记录不变。

- 已补JADX按类分析与5项静态逆向Android资格用例；自有DEX的Windows全量/按类实际解码通过。Android用例仅编译，见STATIC_REVERSE_ACCEPTANCE.md。

## 当前证据

- 7e750f0完整CI39956810632成功，覆盖此前并发资格与严格退出证明；e2f14b4的39955989822被后续提交取代而取消，不记成通过。证据evidence/0.1.40/ci-39956810632-passed.txt。

- 上轮任务固定代修复3beb4c7完整CI39953323831成功，证据evidence/0.1.40/ci-39953323831-passed.txt。

- 原生挂载bedcfd3完整CI39925157055已通过，3新挂载测试在日志中实际成功；无图形profile与清理诊断556ecf6完整CI39947239121也已成功，见evidence/0.1.40/ci-39947239121-passed.txt。

- 静态来源/准备层d16f49d完整CI39920712690已通过；后续共享管理/挂载/无图形profile完整CI亦已通过（见上项），Android安装/运行仍未测试。

- 9517ea9完整CI39909014575成功，包含核心/签名镜像/POSIX/PTY/Root原生主机/PRoot内核与生命周期/抓包/Android Debug、Release R8、testAPK、lint及ELF/源码检查。证据evidence/0.1.37/ci-39909014575-passed.txt。
- 当前本地核心235项：225通过、10项平台跳过（含新POSIX清理失败用例）。异步JS14、传输JS18场景分别属于各自一个JUnit测试，不重复加到235总数；下载恢复原5项与本轮新增1项元数据Android测试仍仅编译。
- 随后仅补测试任务对mjs文件的输入追踪，以及Android跨域导航夹具，core/testAPK/lint本地再次通过；2214641完整CI39910230241也已成功，见evidence/0.1.37/ci-39910230241-passed.txt。0.1.38实现40d96bc完整CI39913505934已通过，见evidence/0.1.38/ci-39913505934-passed.txt；0.1.39实现d403a4f完整CI39915958302也已通过，见evidence/0.1.39/ci-39915958302-passed.txt；0.1.40下载恢复b75113f完整CI39917911407已通过；后续传输错误码849561c的CI39918760978被新推送取代而取消，4c60026的CI39918856612同样被后续推送取代而取消；跟踪最新main运行，不能把cancelled记成通过。
- Root抓包主机外层12项、内层私有netns10项成功；新Root真机7项与BrowserAsyncTest六项仅编译，没有执行手机。

## 尚未实现，建议继续的顺序

1. **浏览器文件传输**：Agent与用户页面的上传/下载、会话归属、可观察进度/产物、取消/删除协调。先比较onShowFileChooser/精确URI授权与字节传输，核实程序点击文件输入的用户手势要求；下载需比较现有OkHttp、WebView网络上下文与系统下载器，正确处理各会话Cookie、HTTP/HTTPS/SOCKS代理、重定向和POST/blob。不能把GET重发或直连降级当所有下载都支持。新的通道/持久化设计先确认。组合方向已获确认；0.1.38已实现Agent二进制上传/页面下载，详见 BROWSER_TRANSFER_IMPLEMENTATION.md。原生chooser与精确URI快照、旧上传快照有界回收已接入0.1.39。0.1.40下载中断恢复已接入。错误诊断、文件展示/MIME已接入，接续实际WebView资格；勿重复确认同一方案或把Agent路径当全部浏览器传输。
2. **逆向/CTF工具包**：动态Frida新运行器组合已比较并写入DYNAMIC_REVERSE_PROPOSAL.md，待用户确认；未开始接入，不能把文档算成动态逆向已完成。静态运行方案已研究并写入STATIC_REVERSE_PROPOSAL.md，JADX1.5.6+Apktool3.0.3、Temurin27+35共享Root chroot按需CLI，用户已确认新增工具安装/只读挂载与租约布局。编译期目录、校验与暂存准备层已实现，官方包实际字节验证通过；共享安装发布/移除/清理及Agent管理接口已接入；持久任务bundle引用、精确退出证明和删除屏障已接入；Root只读挂载/能力协商3/固定CLI资格/Agent分析执行现已接入；最终手机上实际JRE27/CLI/自有APK资格与故障验收仍待进行。详见STATIC_REVERSE_IMPLEMENTATION.md。JADX、Apktool、原生库分析、Frida等实际ARM64运行器、版本化共享依赖与Agent工具，不用普通SKILL.md假装这些二进制已安装可用。
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
