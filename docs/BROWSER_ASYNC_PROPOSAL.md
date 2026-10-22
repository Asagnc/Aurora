# 浏览器异步 JavaScript 结果通道提案（待确认，2026-10-23）

目标：Agent可等待Promise/fetch等网页结果，复用现有会话WebView、任务取消与JSON预算；不重载页面、不安装新引擎，不为等待结果反复evaluateJavascript。仅扩展脚本结果传输，不提供Root/文件/模型原生方法。

## 比较

| 方案 | 优点 | 代价/边界 |
|---|---|---|
| 反复evaluateJavascript查询Promise结果 | 接入简单 | 空闲IPC/定时唤醒、共享全局槽清理和导航竞争，违背低开销目标 |
| addJavascriptInterface | 传统回调方便 | 对象暴露所有frame，缺少可靠来源约束，容易扩大原生能力面，不采用 |
| AndroidX addWebMessageListener | 支持来源规则/主frame回调，适合固定站点长期桥 | 注入面向后续加载，任意当前页面临时执行需管理注入/页面生命周期，不作为本次默认 |
| **每次调用独立WebMessagePort** | 系统已有API、事件返回、指定顶层目标origin、及时关闭，无新依赖 | 需要启动握手、导航版本检查与端口/JS监听清理；网页结果仍不可信 |
| CDP/第二浏览器引擎 | 扩展调试能力丰富 | 新引擎/服务/更多资源与维护成本，本项不引入 |

## 推荐合同

1. 保留同步evaluate路径；为需要异步的调用明确启用Promise等待及有界deadline。脚本依然使用实际页面环境，原有页面/会话所有权及精确URL/revision校验都适用。
2. 原生createWebMessageChannel每次建立独立端口；注入当次JS接收函数，确认安装后postWebMessage只向当前精确HTTPS origin的顶层页面转交另一端口，不使用通配目标origin。每次调用ID隔离不同运行，不把随机ID当成可信网页认证。
3. JS接到匹配的启动消息后移除监听，执行脚本并Promise.resolve等待，返回一次有界JSON数据或错误；没有逐帧/定时轮询，也不提供JS调用Root/文件等原生能力。
4. Native同时监听结果、页面生命周期与任务取消。导航/关闭/renderer退出、deadline或用户停止后，关闭本地端口，清理可清理的JS监听/一次性计时器；迟到或重复结果丢弃。端口转移后按官方API所有权规则处理，不假定仍能用Native端调用已转移端口。
5. 页面导航前后，即使URL相同也通过revision区分；结果接收再次验证会话/页面/版本。页面和iframe仍可影响网页结果，因此始终标记untrusted_web_content，不作为Root或其他工具的授权依据。跨站iframe任意访问不是本次能力。
6. 超时只代表停止等待：关闭端口不能撤销已经发送的fetch/页面副作用，也不能中断同步死循环。未知操作不自动重放，不能承诺网站无限制或任意JS可强杀。对显式提供的AbortSignal只能作协作取消，不虚构原生强制取消。
7. 输出沿用明确的JSON/截断预算，异常、循环引用、不可序列化数据和页面变化不能伪装成功；网页被篡改的内建对象不建立安全保证。
8. 不新增依赖、常驻服务、SQLite表、调试端口或权限。Android16起使用现有系统WebView API；实际设备WebView拒绝该功能时返回能力缺失，不偷偷增加轮询替代。

## 实施前可独立完成

现有加载等待改用WebView生命周期事件，移除50ms轮询；修复工具/批处理吞掉外层TimeoutCancellationException的风险，页面关闭/导航后不接收旧JS结果。这些使用现有回调和协程，不依赖新增Promise通道的批准。

## 验收

本地/主机验证生命周期、取消、序列化和迟到/重复完成规则；Android测试覆盖Promise成功/拒绝/一直pending、同步兼容、导航与同URL刷新、页面关闭、会话删除、renderer退出、取消/超时收尾、相邻会话隔离和预算。只有实际执行过的测试记通过；真机仍最后集中验收，不调用真实供应商API。

## 一手资料

- [WebView evaluateJavascript / createWebMessageChannel / postWebMessage](https://developer.android.com/reference/android/webkit/WebView)
- [WebMessagePort 的转移、回调与关闭规则](https://developer.android.com/reference/android/webkit/WebMessagePort)
- [AndroidX WebViewCompat.addWebMessageListener](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
- [Kotlin withTimeout 的取消与协作边界](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout.html)

新结果通道属于原生/网页通信架构调整，按用户最初要求先确认；当前不提前宣称Promise已支持。
