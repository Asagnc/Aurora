# 对话基础细节修复与 COUI 隔离资格（2026-11-09）

用户明确复制按钮只是问题之一，消息没有时间；这一轮包含消息元信息、操作层级、整体 ColorOS17 方向，不把上一轮设置整理视为视觉完成。

## 已实现的例行修复

- 用户气泡取消正文上方永久复制，保留 SelectionContainer 的原生长按选择／复制；助手复制移到正文下方，仍是48dp IconButton触摸区域、18dp图标，流式中不显示。代码块专用复制与完整回复阅读器保留。
- ChatMessage 新增 nullable createdAt，默认 null 以兼容旧序列化消息。生产用户消息创建时记录系统 wall-clock，完成／中断的回复落盘时记录；不是首 token 时间或耗时。流式占位不反复生成时间。
- 沿用 SQLite，schema10→11 只为 messages 增加 nullable created_at；旧记录不回填当前时间／会话更新时间，读取仍未知。历史1→9路径保持原迁移并增加该列，迁移夹具相应还原真正的旧表结构。没有新增数据库或后台任务。
- 新消息时间在气泡外／回复下方显示，系统日期格式、时区及12/24小时设置生效。抽屉展示原有会话 updated_at，即最后活动时间。长标题／时间单行省略，不挤掉会话操作。

## 已验证与未验证

第一次宿主批次：core:test、Debug、默认R8 Release、AndroidTest编译、双lint，共224任务／66秒成功。core报告362次（346通过、16跳过，0失败／错误），新增两项序列化兼容测试通过；Android新迁移／重开／分页用例仅编译，**尚未在设备执行**。双lint无错误，仅原有NewerVersionAvailable、VectorPath、Release MonochromeLauncherIcon警告。

该批Debug SHA256：86f907ed8c2701c545baaf29e5e745a579039918a88aa97142bf2cb7733a9375。未安装到手机，不能沿用上一轮12类真机证据作新消息布局证明。无模型请求、未触碰Root／签名／用户凭据。用户当前未连接手机，设备选择／视觉／真实SQLite升级资格待补。旧审查包不覆盖这些新字节。

schema11升级后不支持直接降装schema10旧APK；后续视觉回退应在schema11代码上恢复视觉，不能直接以旧包覆盖。当前尚未对用户数据库执行升级。

## COUI

2026-11-10更新：独立预览的明暗／开关／复制／键盘、横屏与130%字体草稿保留已观察通过；首次横屏断言误把滚动区域外的草稿视作丢失，滚动到输入区后确认保留，原失败日志保留。主包已进入集成，固定同一alpha BOM／Compose1.13.0-alpha02／COUIalpha9／M3rc01，不再沿用稳定Compose相容声明。

用户已确认固定0.0.1-alpha9先资格再迁移，详见UI_COLOROS17_PROPOSAL。隔离工程tools/coui-qualification无网络／Root权限，不读取主包数据库与凭据；样例文本／时间明确标注。本轮主app依赖未改。其Alpha BOM将Compose升级到1.13.0-alpha02，原稳定宿主试验被守卫拒绝的结果保留，不能称现有稳定Compose兼容通过。


## 主包 COUI 集成与真机回归（2026-11-10）

- Application每进程仅初始化一次CouiMaterial3Style；AuroraTheme内统一CouiTheme深浅上下文，保留原动态／自定义强调色与色彩风格、纯黑设置。采用COUI中性表面区分正文、气泡／代码与设置卡片，未新增外观持久化项或重建ChatController。
- 对话使用COUI Scaffold／TopAppBar／浮动TopAppBarButton，输入区使用CouiCapsuleShape；原附件、思考、模型、上下文、发送／停止及外侧指标保留。当前输入正文仍为Compose BasicTextField，Markdown／复杂表单保留现有渲染，不能称所有组件已逐个迁移完。
- FullPage使用COUI Scaffold／LargeTopAppBar与其实际scrollBehavior／contentPadding；设置首页使用原生Group／Item／Divider及COUI语义排版。共同状态／详情使用COUI形状、Item与主题角色，外观开关使用原生COUISwitch与整行单一toggleable语义。
- 实拍发现白色气泡在白色正文上失去层次，已改为独立surfaceContainer；色彩风格背景染色保留，不能以统一中性表面使“柔和／鲜明”失效。新样例仅本地注入，不调用模型。
- 当前已装主包的复制正文／代码入口位置、原生长按选择工具条、跨日期消息时间、抽屉最后活动时间、键盘／抽屉不自动弹键盘、明暗／纯黑开关、返回后草稿保留、横屏及130%字体均观察检查。原生浮动选择工具条不在uiautomator根XML中，首轮仅依XML断言误报失败，实际截图已显示“复制／全选”；不记录成应用缺陷或算失败批次整体通过。
- SQLite实际用户库10→11，所有原表原列逐值摘要与升级前快照相同，旧消息时间仍null；原始数据库不入库。独立迁移测试1项0.089秒通过。主包数据／恢复回归47项6.782秒全部通过，含历史迁移、分页、Skills／Root管理记录与浏览器恢复；不是Root特权测试。
- 明暗／纯黑操作后外观配置值恢复原值；字体与旋转设置精确恢复。测试会话、测试草稿与独立资格包收尾后不留在用户日常数据中；最终清理与最终APK摘要记录以精简证据为准。
- COUI源码jar两份摘要钉定在tools/coui-qualification/source-materials.json；MPL／NOTICE／商标与Skia／AndroidX、原资源来源说明保留到APK assets。上游说明中包含第三方原厂资源及未检出独立许可的来源，不能将库的MPL声明扩大为这些素材的独立发布许可或整体法律闭包。完整正式审查包仍待UI稳定后重打，releaseReady=false。

未覆盖：TalkBack完整操作、系统所有字体／窗口组合、流式／长时间复杂页面的性能、默认Release真机、正式签名及真实模型。现有设备样例与47项不替代这些范围。


## 最终包与清理

产品提交a418ded298ec4f709edc2e6b8cea20724a60148a；Debug SHA256 a8f08f9f200fc64c66e5301575faed0bec1446efca6bbc5dc022b869e8d21b52，手机base.apk摘要一致，签名仍46c0eb4f69334514e6ee98f55655f8ac6e5ddb07ca45f346739ba7ad8c765a2c。Frida worker仍c9b22da8，正式门控false。七份COUI许可／来源文件已在精确APK中逐字节核对；新增GradleDependency警告提示有更新的alpha BOM，本轮按已验证上游组合固定，没有自动换2026.11.00。

精确最终APK上再跑47项回归全部通过5.003秒；消息时间／复制／设置／Root引导和Frida关闭入口再次检查。键盘／明暗／草稿／横屏／130%字体的完整观察在此前集成配置上，最终只修正表面染色并补来源文件，相关交互代码未变；不冒称所有历史截图来自同一APK。

仅本轮精确自有会话经显式夹具清理，升级前／最终原表原列值再次摘要相同；旧消息时间保持null。移除本轮安装的uiqualification与main.test两个包，主Aurora保留，当前停在设置页，手机这轮已结束。字体／旋转／外观设置均恢复，未调用付费模型或连接Root。证据见evidence/0.1.40/ui-coui-chat-20261110.json。产品a418ded完整CI42652258424五个job全部成功；后续仅文档与精简证据，不改变已装APK字节。

日期格式依据：[Android DateUtils 官方API](https://developer.android.com/reference/android/text/format/DateUtils#formatDateTime(android.content.Context,%20long,%20int))，使用公开本地化格式API，不写死中文／24小时格式；12小时偏好变更的专项交互尚未单独实测。
