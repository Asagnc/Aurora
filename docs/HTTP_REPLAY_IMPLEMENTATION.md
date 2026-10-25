# 单次 HTTP 构造与显式重放

2026-10-25，沿用 NETWORK_ADVANCED_PROPOSAL 已批准的 OkHttp5.5.0。
Agent 已注册 `http_request`，普通权限即可使用；不是 Root 抓包、TCP 包重发、WebView 登录态下载或 TLS 代理解密。

## 当前契约

- `url`、大写 `method` 和 `route` 必填。路由明确选择 direct/http/socks；代理要求 host/port，无自动直连回退，也不继承系统/浏览器/模型代理或凭据。
- 有序请求头允许重复，最多64项/32768字符。拒绝 CRLF、手动 Content-Length/Transfer-Encoding 与 hop-by-hop/proxy-authorization 头。
- 请求体为最多256KiB的内联 Base64，或本会话最多8MiB文件；文件必须提供当前字节的 SHA256，读取后在内存固定并验证。GET/HEAD 不接受请求体。
- 不自动重定向、传输重试、认证挑战重试或保存 Cookie；TLS 使用正常证书/主机名验证，没有 trust-all。每次独立连接池避免从其他请求继承 HTTP/2 合并连接。
- OkHttp5.5 的源码会在503/Retry-After:0时独立于 retryOnConnectionFailure 决定 follow-up。只对库内部决策临时抑制该头，最终记录恢复原始值；额外 network-interceptor 屏障拒绝第二次网络交换。真实服务器回归确认 GET 仅收到一次并返回原503及头。
- 网络调用默认30秒，允许1..60000毫秒；响应预算默认1MiB，最大8MiB，按 OkHttp 解码后的字节计数，包含解压后的超限检测。超限、超时、连接或TLS失败关闭本次调用，返回明确白名单原因和 `http_outcome_uncertain`，不自动重发。
- HTTP 3xx/4xx/5xx保留实际响应，`httpSuccessful` 单独表明2xx；不能把“收到响应”当业务动作成功。

## 归属与收尾

发送前沿用 `tool_runs` 登记 `runs/<UUID>`，不新增数据库布局，schema仍8。
同一 callId 的重复由现有账本拒绝；新的 callId 是新的显式请求，HTTP本身没有跨崩溃 exactly-once 保证。

完整收到响应后，复用现有 `BrowserDownloadFile` 写入临时文件、force及原子发布，路径固定为 `runs/<UUID>/download.bin`。
返回字节数、SHA256、最多2048字节文本预览、最多128项/32768字符响应头及截断标记；完整二进制留在会话文件。
发布失败保留已知状态码/摘要和明确错误，不声称文件已发布，也不重做远端动作。

取消保持 CancellationException，并等待本次阻塞读/调用结束；普通失败关闭响应，不发布不完整文件。
会话删除复用 OwnedTasks quiesce 屏障。进程硬崩溃后的既有 uncertain 意图和不明暂存保留，不通过猜测删除或自动重放；会话删除负责最终归属清理。
命令和文件空间沿用既有 UID/Root 实际权限约定，不增加安全沙箱承诺。

## Android 网络策略

显式 HTTP 请求需要 manifest 允许 cleartext；这只允许选定 `http://` 地址，HTTPS 不自动降级。
模型网关仍由 HttpModelTransport.endpoint 强制HTTPS，Skills及图片下载的既有HTTPS检查保持有效。

Android17/API37的局域网连接需要 ACCESS_LOCAL_NETWORK。已声明权限并在设置提供用户主动申请/打开系统设置入口；返回前台重新观察实际授权。Android16不显示不适用入口，不在启动或Agent任务期间自动弹权限请求；未授权连接由系统拒绝。
不修改系统代理、路由或证书，不安装CA。

## 实际验证与待验收

- Windows真实JDK25下11项自有 loopback 测试通过：二进制POST/302不跟随、503不重发、文件摘要与非法参数、gzip解压预算、实际读取消、截止、HTTP代理、SOCKS远端DNS、自签名TLS拒绝、Cookie/401隔离、发布失败不重发。
- 完整核心280项：268通过/12跳过；Debug/Release/testAPK/lint本地通过。没有访问外部业务网站或付费模型。
- 新增2项Android资格用例已编译：真实账本/二进制会话产物，以及删除会话必须先取消实际HTTP读。未执行手机。
- Android17授权/撤权、真实Android TLS/代理/网络切换仍留集中设备验收；主机结果不代替设备证据。

依据：[固定OkHttp重试/跟随实现](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt)、[Android网络安全配置](https://developer.android.com/privacy-and-security/security-config)、[Android局域网权限](https://developer.android.com/privacy-and-security/local-network-permission)。
