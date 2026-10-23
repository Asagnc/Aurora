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

1. 原生 onShowFileChooser：Aurora 会话文件选择、外部 SAF 导入、精确 URI 与文件快照生命周期；目前默认 chooser 仍取消，不能声称用户上传入口已完成。
2. 快照与进程死亡遗留 .partial 的有界显式恢复。当前普通退出清理已做，进程死亡遗留文件仍会留在所属会话，删除会话可清理；不自动重放任务，也不声称崩溃回收完成。
3. 丰富可诊断错误码、文件 MIME/展示名与独立传输详情；当前使用生成路径、字节/耗时和安全阶段错误。
4. 大文件 Agent 原生表单路径、无 CORS 跨域附件、已有导航 POST 响应、跨源 frame、断点续传不在当前已实现范围。不能通过暗中转直连、Base64 或复发请求掩盖缺失。
5. 最终集中手机验证 ArrayBuffer、真实 FormData 服务端逐字节、Cookie 隔离、HTTP/HTTPS/SOCKS 与错误代理、取消/导航/删除/死亡/磁盘满，以及 Release RSS/CPU/速度/卡顿。手机仍 0.1.32，本轮未连接或安装，也没调用真实模型。
