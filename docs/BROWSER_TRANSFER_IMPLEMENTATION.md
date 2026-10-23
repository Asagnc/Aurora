## 2026-10-23 下载展示元数据与产物详情

下载握手单独验证 mime（128字符）与 Content-Disposition（512字符），超长提示整体丢弃；上传 ready 仍只允许 kind。复用已有 AndroidX URLUtilCompat 解析 filename/filename*，再移除路径片段、控制/格式字符及孤立代理，名称最多120个UTF-16单元且不截断代理对；MIME只保留规范化类型，不用于内容验证/自动打开。原始头、Response URL均不写入结果。

displayName/mimeType进入已有工具结果，无数据库迁移、旁路文件或新依赖；磁盘名仍 runs/UUID/download.bin，原中断恢复协议不变。下载详情展示名称、类型、字节与产物入口；只允许规范UUID下载路径，旧结果回退download.bin。下载不再打开不存在的命令输出日志。

新增4项核心测试，生产传输JS增加2个元数据场景共18个（属于一个JUnit）。核心205项：196通过、9 POSIX跳过；Debug、Release R8、Android测试APK与lint通过。新增1项Android文件名扩展参数端到端用例仅编译，最终真机统一执行。未调用付费模型或操作手机。

官方依据：https://developer.android.com/reference/androidx/webkit/URLUtilCompat 。名称/MIME始终是网页提供的不可信提示；跨域未暴露的头不可读取，不重发网络请求猜名称，不承诺所有下载来源可用。

## 2026-10-23 浏览器传输错误码与 MIME 通配符修复

页面传输仅返回白名单错误码：脚本、Response 可读性、源类型、读流、字节预算、上传目标/accept/多选与协议等。原生只接受精确错误字段；未知代码/额外字段统一降为 page_transfer_failed，不把页面异常文本、签名网址或请求内容写入账本。页面代码属于未可信诊断提示，不作为原生事实。工具继续保留方向 phase 与 unknown/outcome，不自动重试；超时新增 transfer_timeout。

修复 Agent 文件输入 accept=*/* 被误拒绝的问题，保留 application/* 与扩展名匹配。新增通用与类型族 MIME 通配符场景；生产 JS 夹具逐一验证失败代码和精确消息形状。

本地核心201项：192通过、9 POSIX跳过；生产传输JS16子场景属于一个JUnit用例。Android Debug Kotlin编译、lint通过；没有操作手机。文件展示名/MIME元数据仍待后续，不更改下载固定磁盘名和已批准恢复协议。

0.1.40接续：下载账本关联的中断恢复已实现，见BROWSER_DOWNLOAD_RECOVERY.md；下述0.1.38/0.1.39的旧缺口由该文档按范围更新，未登记/不明记录及实际设备资格仍保留。

# 浏览器文件传输实现与证据

2026-10-23，源码版本 0.1.38。用户已确认“多个方面组合取最优”；推荐分工保持 BROWSER_TRANSFER_PROPOSAL.md。以下严格区分已实现、主机验证和最终设备资格。

## 当前实现：Agent 二进制路径

- 新增 browser_download/browser_upload，复用现有工具账本、前台 Agent 宿主、会话任务所有权和浏览器页面。没有新增网络栈、下载服务或数据库表。
- 系统 WebView + AndroidX WebKit 升至已于 2026-10-21 发布的稳定版 1.18.0；[官方发行记录](https://developer.android.com/jetpack/androidx/releases/webkit)。不启用新版本的跨源隔离放宽 API。
- 每次使用独立 AndroidX ArrayBuffer 端口、精确 HTTPS origin、页面 revision 和随机 ID。检查实际 WebView feature；库升级不代表手机运行时自动有全部能力。
- 下载 script 只执行一次，返回 Response/Blob。页面可读的响应在原网络上下文处理；fetch 应使用提供的 signal。CORS、跨源 frame、页面授权和网站策略不被绕过。DownloadListener 仍只提示尚未接入，不自动重发 GET/POST。
- 64KiB 块、原生写盘后 ACK、无排队消息的 rendezvous 通道；未收到 ACK 不发下一块。非法消息、额外端口、尺寸异常和重复阶段失败；不会因网页声明长度而预分配大文件。WebView 把恶意大消息送入应用之前的平台分配不在这个预算保证内。
- 下载默认 64MiB、最高 512MiB；默认 60 秒、最高 270 秒，包括任务内部文件准备，排队另受 Agent 外层期限。总共最多两路传输，不启动轮询。初值是可测资源预算，不宣称性能最优。
- 产物 runs/<UUID>/download.bin，先排他创建 .partial，逐块累计实际字节；完成计数必须匹配，force/关闭后原子发布。普通失败/取消/页面变化关闭 FD 并删除临时文件；若已发布后取消，产物保留、结果需核实。父目录持久化和断电恢复没有因 ATOMIC_MOVE 自动获得额外保证。
- Agent 上传读取显式 conversation/resources 文件，拒绝目录、穿越和链接；合计最高 32MiB，1–8 个文件，支持空文件。上传路径在 app-owned namespace 内，不是面对任意并发 Root 程序的文件系统沙箱。
- 二进制分块供给网页，检查目标 file input、multiple/accept，构建 File/DataTransfer，派发 input/change。只返回 input_set，不能当网站已收到文件；网站可拒绝非可信脚本事件。File 构建有整文件存储/复制成本，并非全程常数内存。
- 页面导航/关闭、任务取消/超时终止本次结果通道；尽力 abort 受控 fetch、取消 reader、释放 File 引用。页面脚本若忽略 signal，其已发业务请求不能强制撤销。进程被杀不自动重放。
- 浏览器页显示当前传输方向与字节，最多每 200ms 由实际事件合并更新，不额外轮询；工具详情保存字节、阶段、耗时和下载产物目录。错误不返回任意页面异常文本，避免签名 URL/请求秘密进入账本。

BrowserBinaryTransfer.execute 超过普通函数目标长度，原因是端口转移前后所有权、文档监视和 finally 关闭必须保持在一个可审查作用域；Session/消息发送/资源关闭已拆分。不新增忽略 lint 的注解。

## 验证与范围

- 本地主机核心 191 项：184 通过、7 POSIX 在 Windows 跳过。
- 新增 4 项 JUnit，其中一个实际执行生产传输 JS 的 14 个子场景，不重复加进 190：Response/Blob 分块、空文件、预算/HTTP/脚本/reader 失败、取消、错误 ACK、单/多文件和 accept 不匹配。Node 使用真实 File/Blob/Response/ReadableStream/MessageChannel，但 DOM 是夹具。
- 下载文件测试验证超限/报告长度错误不发布，失败清理、空文件和完成后拒绝继续写；现有 readBinary 默认空文件拒绝不变，仅传输显式允许。
- Android BrowserTransferTest 新增 4 项：实际 Response/Blob 字节、真实 input/FormData 文件内容、失败/外层取消清理、导航/跨会话拒绝。只编译，未运行手机；原生 chooser 与真实网络服务端上传不是该夹具覆盖范围。
- Debug、Release R8、testAPK、lint 本地构建证据及 CI 状态见 evidence/0.1.38 与 NEXT_STEPS.md。不得把库编译成功写成 WebView 端口实际转移、Cookie/代理正确或 Release 性能已测。

控制包另有严格字段、类型和深度校验；页面错误文本不进入异常或账本。新增第4项核心测试覆盖恶意深度、伪造字段、负数/字符串计数和敏感错误内容。

## 继续实施的明确缺口

1. 0.1.39原生onShowFileChooser、会话/资源/SAF与精确URI快照已接入，见BROWSER_CHOOSER_IMPLEMENTATION.md。真实WebView消费URI、手势与提供器仍待验收，不把回调测试当真实上传。
2. 上传快照的旧进程有界回收已接入0.1.39；下载进程死亡遗留 .partial 的有界显式恢复尚未完成。当前普通退出清理已做，进程死亡遗留文件仍会留在所属会话，删除会话可清理；不自动重放任务，也不声称崩溃回收完成。
3. 丰富可诊断错误码、文件 MIME/展示名与独立传输详情；当前使用生成路径、字节/耗时和安全阶段错误。
4. 大文件 Agent 原生表单路径、无 CORS 跨域附件、已有导航 POST 响应、跨源 frame、断点续传不在当前已实现范围。不能通过暗中转直连、Base64 或复发请求掩盖缺失。
5. 最终集中手机验证 ArrayBuffer、真实 FormData 服务端逐字节、Cookie 隔离、HTTP/HTTPS/SOCKS 与错误代理、取消/导航/删除/死亡/磁盘满，以及 Release RSS/CPU/速度/卡顿。手机仍 0.1.32，本轮未连接或安装，也没调用真实模型。
