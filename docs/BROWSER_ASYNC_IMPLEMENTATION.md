# 浏览器异步脚本与生命周期（0.1.37，2026-10-23）

用户已确认BROWSER_ASYNC_PROPOSAL的每次调用独立WebMessagePort方案。代码已实现，本地主机测试和Android构建通过；实际Android WebView通道仍待最后集中真机验收。

## 已实现

- `browser_evaluate` 默认同步直接返回；明确 `awaitPromise=true` 才创建系统消息通道，等待Promise/fetch/普通返回值。timeoutMs默认10000，范围1..30000。复用原有WebView引擎，无新APK依赖、服务、数据库表、调试端口或权限。
- 每次独立运行ID/端口，确认当次页面JS接收器安装后才向精确HTTPS origin的顶层页面转交另一端口；不使用通配origin，不注入Java对象或提供Root/文件方法。Native不再关闭已成功转移的端口，接收端由JS完成/清理逻辑关闭。
- 原生结果一次性处理；迟到、重复、附带意外端口、畸形或超预算数据不会成为成功结果。JS接收器也只允许启动一次，错误运行ID不消费它。原生finally关闭本地端口并尽可能清除原文档的接收器，JS另有一次性清理截止；没有定时轮询结果。
- 结果沿用明确JSON/截断标记：编码前后的预算匹配，处理引号/控制字符膨胀和Unicode边界。循环引用、BigInt和不可序列化值是错误；大值返回有标记的文本预览。Native检查回传字段、标志、大小和嵌套深度，解析移至Default线程，解析后再检查页面身份。
- WebView加载/HTTP错误/关闭/导航/历史更新通过现有回调更新StateFlow。加载等待替换原50ms查询循环。脚本等结果同时观察文档版本；同URL重载仍增加revision，页面变更后不把旧结果交给新文档。
- 修复工具入口与批处理的TimeoutCancellationException捕获：只将本次网页截止转成工具错误；外层Agent截止/取消继续传播。释放Agent页面租约、回调等待与端口，不自动重放不确定页面操作。
- 同步返回Promise会提示“执行可能已开始，先检查结果再决定重试”，而不是暗示直接再执行一次。

## 保留的边界

端口及运行ID只隔离运行，不认证网页。网页可篡改内建对象、观察/影响消息与内容，所有结果继续标记untrusted_web_content，不能授予其他原生操作。跨域iframe任意访问不是该功能。

截止/取消只结束等待和结果通道；不能撤销已发出的fetch或副作用，不能强制中断同步死循环。JS清理依赖renderer还能执行；旧文档被销毁或暂时停表时不能声称清理代码已立即执行。Native端口已关闭，迟到数据不接受，下一次恢复或文档销毁处理剩余页面状态。

当前仍保留动作后100ms事件循环观察间隔，防止点击后尚未分派的导航被当成已稳定；这是单次动作收尾，不是持续查询。上传/下载后续接入见BROWSER_TRANSFER_IMPLEMENTATION；任意跨源frame访问不绕过WebView同源策略，renderer真实死亡仍待设备矩阵。

## 验证

- 核心187项：180通过、7项POSIX在Windows跳过；已提供固定签名Ubuntu归档运行对应镜像检查。新增5项生命周期、2项恶意/畸形结果合同和1项生产JS主机执行测试。
- 该JS主机测试实际运行14个场景：同步对象/undefined、大引号字符串、Unicode边界、循环值/BigInt/函数、同步Promise拒绝、Promise成功/拒绝/异步普通值、异步循环值、取消迟到结果、永不完成的接收器清理。还覆盖错误ID与重复启动。证据evidence/0.1.37/browser-script-host-fourteen-passed.txt。使用电脑现有Node24.15.0，CI也需要宿主Node；它仅运行测试，不随APK打包，不替代真实WebView测试。
- Debug、Release R8、测试APK、lint全部本地通过。新增BrowserAsyncTest六项只编译：成功/错误、转义大值/接收器清理、本地/外层截止、导航/跨域/同URL重载、关闭页面、批处理外层取消。
- 实际端口转移/目标origin、迟到回调、renderer退出、UI与后台/会话删除组合仍需Android16真机。当前没有连接/更新手机，仍保留此前0.1.32；没有调用真实供应商或外部网站夹具。
- 9517ea9完整CI39909014575已成功，证据evidence/0.1.37/ci-39909014575-passed.txt。随后补mjs测试输入追踪和Android跨域夹具，本地core/testAPK/lint再次通过，后续提交CI另查。批准、主机CI与构建都不代替设备功能/性能验收。

后续继续浏览器文件上传下载和故障矩阵；新传输/持久化选择按既有要求先研究确认。


2026-10-26新增BrowserBoundaryTest：确定性自有HTTPS拦截页面实际加载同源/跨源iframe，事件驱动等iframe ready后分别确认可读与SecurityError；新增真实WebView renderer.terminate用例，先要求显式auroraBrowserCrashTest=true且Aurora实际浏览器无打开页面，验证未完成Promise因关页失败、无重放，并只删除本fixture owner。用例仅编译，当前不操作手机/模拟器，不声称renderer死亡或跨站矩阵已通过。该测试会终止当前App使用的renderer，最后集中执行前关闭所有真实页面。依据：https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail)。
