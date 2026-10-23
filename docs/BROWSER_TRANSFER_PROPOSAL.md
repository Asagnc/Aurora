# 浏览器文件传输方案（组合方向已确认）

研究日期：2026-10-23。用户回复“多个方面组合取最优”，据此确认本文推荐组合方向。实现与证据分阶段记录在 BROWSER_TRANSFER_IMPLEMENTATION.md，不能把方案或编译当成功能已完成。沿用现有 WebView、AndroidX WebKit、会话任务宿主和文件空间，不新增浏览器引擎、下载守护进程或网络依赖。

0.1.39实现细化：已交付的原生URI快照保守保留到关页/删会话，导航只取消未交付选择，以免表单导航后仍需读取时抢先删除。预算与代价见 BROWSER_CHOOSER_IMPLEMENTATION.md；不将URI回调等同于真实网页上传验收。

## 结论与比较

建议组合使用系统文件选择回调与页面二进制传输，先保证浏览器登录态、代理和任务归属正确，再按实测扩展大文件能力。

| 路径 | 适用优势 | 代价和边界 | 建议 |
| --- | --- | --- | --- |
| onShowFileChooser + 精确 content URI | 浏览器原生表单上传；不需要把整份文件 Base64 化 | 只在真正的 chooser 请求出现时有效；不能用 JS click 假装用户手势；文件可能延迟读取 | 用户页面原生上传路径 |
| ArrayBuffer 消息端口 + File/DataTransfer | Agent 可向明确的文件控件供给文件，无需每次让用户选文件；不需要 CDP | 构建 File 仍需保留文件数据，存在复制/内存成本；脚本触发的事件不是可信用户事件 | Agent 有界文件上传路径 |
| 页面 fetch/ReadableStream + 二进制端口 | 使用页面自己的网络上下文；HTTP/HTTPS/SOCKS 代理不需要在第二套网络栈中重新解释；可处理页面可读的响应与 blob | 受同源/CORS、页面生命周期和服务端行为约束；不能读取 opaque 响应 | 登录态下载和页面产物优先路径 |
| 原生 OkHttp 下载 | 可流式处理大文件；可以不依赖页面存活 | 不自动继承 WebView Cookie/代理/服务工作线程；HTTPS 代理不能简单转成 Java HTTP Proxy；重发不等于原请求 | 后续独立明确请求工具，不能悄悄当浏览器下载替身 |
| Android DownloadManager | 系统后台队列成熟 | 不共享 WebView 配置，也不天然服从会话删除、现有 Agent 取消和任务归属 | 本轮不选 |
| CDP / 独立 Chromium / 新增 libcurl | 可增加文件注入或网络控制能力 | 新调试面、引擎或 HTTP/TLS 栈、包体和维护成本，需要独立验证 | 当前不增加 |

“组合”不意味着失败后轮流重试所有后端。一次性链接、POST、上传都可能已经产生服务器效果，失败返回阶段和结果未知状态，交由 Agent 观察后决定。

## 拟实施合同

1. 每次传输绑定会话、页面、精确 origin、文档 revision、不可预测传输 ID 和任务；使用独立 AndroidX 消息端口及 WEB_MESSAGE_ARRAY_BUFFER 能力检查。现有字符串结果通道仍保持原用途，不把文件塞进模型上下文或 JSON 大字符串。
2. 下载从页面可读取的 Response.body/Blob 流分块读取，原生落盘确认后才允许下一块；建议初始块 64KiB、每路最多一块待确认、每会话最多两路。这是可调资格起点，不是性能最优结论。进度事件合并展示，避免每块刷新 Compose。
3. 页面下载明确选择目标和方法。GET 是一次新请求；需要 POST 的操作由 Agent 明确给出请求且只执行一次。普通 DownloadListener 只作为下载意图提示，不自动从其 URL 重发，因为它没有原请求方法和请求体。不全局劫持页面 fetch/XHR 或预先缓存所有响应。
4. 页面内 blob 可直接导出；已有 Response 必须由调用路径保留可读流，已消费的流不能倒流重建。跨域 opaque、导航附件、浏览器内部下载和服务工作线程情况逐项验证，不能声称全覆盖。需要跨源 frame 的传输另行资格验证，首轮明确返回不支持。
5. 下载在会话产物目录创建排他临时文件；实际字节累计控制预算，不信任 Content-Length；名称由 Aurora 生成，服务端名称只作清洗后的展示元数据。完成校验、关闭、同步与原子发布后才返回产物。截断、磁盘满、网络失败不发布成完整文件。
6. Agent 上传使用显式选定的会话文件或共享只读文件，经现有文件通道验证；Root 文件先走已批准的选定产物导出。通过二进制端口分块构建 File/DataTransfer，设置目标 input.files 后派发 input/change；只报告控件已设置，不能当服务端上传成功。确认 multiple/accept/实际文件大小；不接受目录和任意本机路径。
7. Agent 的 File 路径初始总量建议 32MiB，下载初始每任务 512MiB、最长 270 秒，全部由参数预算再收紧；这些是资源保护与测试起点，超限提前给出明确能力说明，不自动转 Base64 或占满内存。大文件 Agent 原生表单路径仍列为后续资格项，不用“已经支持任意 APK 上传”代替实测。
8. 用户真正触发 chooser 时，在 Aurora 会话文件选择界面选择；外部 SAF 文件先流式导入会话。回调仅返回选定文件的精确 URI；需要稳定内容时建立传输快照，不复制整个会话。生命周期从选择延续到页面释放、导航、关闭或会话删除，不能在 callback 返回时就删掉。保持本地 URL 访问关闭，原生上传在该设置下实际验证后才宣称可用，不通过全局开放 content/file 解决。
9. 收到的页面消息始终不可信：校验序号、类型、长度、总量、状态和文档；网页只能向本次已选定目标传字节，没有任意文件路径、Root 或系统方法。已经供给网页的字节无法撤回，端口保密也不是对同一页面恶意脚本的隔离保证。
10. 取消/截止/关闭/导航/删除时 abort 受 Aurora 控制的 fetch、取消 reader、关闭端口与 FD、移除临时文件；页面已发出的业务请求不能撤销。进程死亡不自动重发 POST 或上传；使用现有任务中断记录及可识别临时文件做有界恢复，不引入常驻轮询。清理协调沿用会话删除租约，原生已打开 URI 的 FD 不承诺撤权瞬间强制失效。
11. 详情显示文件名、字节、阶段、耗时、来源站点、失败原因与产物；不记录 Cookie、授权头、签名 URL 查询参数或请求正文。进度先在任务事件中复用，不新增独立长期传输数据库；恢复是标记中断/清理，不是断点续传。

## 先行验证和停止条件

- 核心/JS：字节与序号预算、单块背压、重复/错误消息、取消与迟到消息、目录穿越、文件名、完整发布与磁盘错误；真实发出的生产脚本用于测试。
- Android 编译门槛：Debug、Release R8、testAPK、lint；不能用 Node 的 File mock 证明实际 WebView 文件表单成功。
- 最终集中手机：系统 ArrayBuffer 能力、真实 input.files/FormData 上传接收端逐字节校验、多文件/空文件/32MiB边界；真正 chooser 与 allowContentAccess=false；Cookie 会话隔离、代理三种协议及错误代理不直连；下载分块、blob、CORS 失败、POST 只一次、重定向、页面替换、取消/删除/进程死亡、磁盘满。
- Release 测 RSS/CPU、传输速率与 UI 卡顿；File/Blob 的整文件存储成本单独报告，绝不把单块背压描述为整次上传常数内存。
- 若平台能力不支持或生命周期/代理测试失败，相应路径保持不可用并继续研究；不更换传输方式冒充成功。大文件 Agent 原生上传、无 CORS 附件等保留明确未完成条目。

## 主要一手依据

- [Android WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient)：客户端可以程序化响应 chooser；需自行检查 URI；默认取消。
- [Chromium FileInputType 源码](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/core/html/forms/file_input_type.cc)：打开文件选择器前检查并消费 transient user activation；以查阅当日 main 为研究依据，不等同于手机 WebView 版本实测。
- [WHATWG input.files](https://html.spec.whatwg.org/multipage/input.html#dom-input-files)：可以赋值 FileList；showPicker 另有用户激活要求。赋值文件不等于服务端收取成功。
- [Android DownloadListener](https://developer.android.com/reference/android/webkit/DownloadListener)：仅 URL、UA、Content-Disposition、MIME、报告长度，无原方法/请求体。
- [AndroidX ProxyController](https://developer.android.com/reference/androidx/webkit/ProxyController)：覆盖作用于应用 WebView；等待应用完成回调。没有对其他 HTTP 客户端的继承承诺。
- [AndroidX WebMessageCompat](https://developer.android.com/reference/androidx/webkit/WebMessageCompat)：提供 ArrayBuffer 消息，必须检查对应 WebView feature；文档未承诺跨原生边界零复制。

以上是基于一手接口合同的工程选择；实际吞吐、内存和网站兼容性仍须资格测试。
