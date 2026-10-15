# Aurora 接续记录

更新时间：2026-10-15。新对话先读 AGENTS.md、DESIGN.md、本文件和 IMPLEMENTATION.md，再检查源码与 Git 状态。

## 产品与最新确认

Aurora 是以 Agent 完成任务为中心的 Android 16+ harness，包名 io.github.asagnc.aurora。用户主要对话、观看进度与结果；已有工具应可由模型直接调用。Kotlin/Compose、SQLite WAL、libsu RootService/Binder、系统 WebView 与三类空间已确认。不要要求 Shizuku；抓包只做 Root，不做 VPN。新底层选型需研究并确认。性能和缓存目标必须有实测，不能承诺全网站可用、100% 命中或领先 Eta。

已确认新界面：docs/assets/approved-chat-refinement.png，保留原 Aurora 图标，使用单层圆角输入框，三指标在输入框内，详情紧贴对应项、位于输入框上方。移除抽屉顶部新建按钮，保留右上新会话入口。全新安装不播种会话，不按默认标题清理用户现有会话。外观采用完整浅/深配色、模式缩略图、配色预览、预设/系统/自定义和柔和/鲜明/单色风格；仅配色，不做壁纸。

## 已实现（0.1.3）

- 三协议流式网络、协议原生历史、工具循环、完整终态校验、取消、工具账本与中断恢复；重复调用 ID 在一轮内和数据库账本中均拒绝重放。
- 十家 BYOK 官方预设与自定义中转，密钥 Keystore AES-GCM 保存；型号目录核查日仍为 2026-10-12，尚无账户动态能力校验。
- SQLite WAL 会话与原生历史追加保存、搜索、置顶、重命名、多选删除、分页。
- 会话/共享只读资源/共享写工作区，批量文件列举/读取/版本校验编辑、导入与恢复；删除协调任务、文件和浏览器数据，不跟随外部链接。
- Agent 浏览器工具：list/open/read/action/evaluate/close、批量 open/read；复用用户可观看的会话页面。最多 8 个活页，批量两个操作并发。页面读写由主线程协同，导航版本和 URL 校验、取消、输出截断、独立 Profile、全浏览器代理与清理恢复。
- DOM 包含文本、CSS 选择器和截断标记；点击/填写/滚动、同步 JS 返回 JSON。网页内容标为不可信；无原生 JS Bridge。异步 Promise、上传下载、跨站 iframe、任意网站成功率和后台常驻仍未验收或未实现。
- 新输入框和就地指标面板、纯文本助手回复、折叠执行记录；记录界面仅取最近 50 项及有界预览，避免每次执行加载完整历史结果。
- 7 套配色、系统动态配色、自定义 HSV/颜色值、纯黑选项与配色风格；完整角色映射与记忆化，旧偏好保留，无壁纸依赖。
- 多次模型调用的输出用量累计后计算整轮端到端估算速度；缓存和上下文仍明确是最近请求。缺失计数不作为 0 相加。
- Root 只读连接/断开/重连在 FolkPatch 应用内授权后已于 0.1.2 真机验证；0.1.3 无新增特权操作。

## 当前验证与环境

0.1.3 核心 45 项通过；API 37 模拟器和 API 36 ARM64 realme GT6 SE 各通过 18 项普通设备测试与独立进程恢复 1 项。浏览器 Agent 使用本地 HTTPS 拦截夹具和确定性模型测试，不消耗付费 API。验证开页、填写、点击、实际 DOM 读回、跨会话拒绝、过期操作、脚本错误、输出预算、批量顺序/部分失败与取消；主题检查主要文本角色对比度。

本地 debug/release/lint 构建已通过；release 未正式签名。最后资源清理后 debug/release/lint 再次通过（0 错误、2 项既有警告）；补充账本重复 ID 断言后真机持久化 9 项复测通过。真机已覆盖更新保留数据；最后仅移除 io.github.asagnc.aurora.test 辅助包，绝不卸载主应用。

JDK25（Android Studio/jbr）、Gradle9.8.1、AGP9.4.1、Kotlin2.4.21、compile/target37、min36；具体稳定依赖固定在构建文件。GRADLE_USER_HOME 为 ../gradle-cache。真机 ADB 6c72c941；模拟器 emulator-5554。

默认 scripts/verify-emulator.ps1 只接受模拟器，要求至少 18 项普通测试，再单独进程运行 BrowserRecoveryTest。该恢复测试不能与会创建新 Profile 的用例放在同一进程末尾运行。不要使用 connectedDebugAndroidTest 对日常真机测试，Gradle 收尾可能卸载主应用；真机用显式序列号 install -r 和 am instrument，排除 RootRequired，普通组排除 BrowserRecoveryTest。

## 下一步（未完成）

1. 真实模型和网关的工具任务验收、能力探测、最新型号刷新；不能用本地夹具替代真实供应商计费和缓存结果。
2. 缓存按协议、精确模型、实际供应商/网关、账户路由与参数匹配；目前仅基础前缀/范围/Claude 断点，最新各家缓存模式和期限策略尚未全面适配，未做到所有模型最优。
3. Root 系统操作/撤权/子进程取消，原生抓包/发包、逆向工具（见 REVERSE_ENGINEERING.md）。非 Root 不增加高开销替代抓包路径。
4. Skills 安装/运行、校验回滚；PRoot/chroot 和手机 APK/NDK 编译。当前 Skills 入口仍是只读资源目录。
5. 后台任务服务、原生图片/PDF 理解、浏览器上传下载/异步脚本/renderer 故障注入、任务成功率/延迟/内存/耗电基准。
6. 正式签名与合格单色启动图标。MiMo 长矢量警告保留，不为消警告改坏品牌形状。

GitHub 使用本机 gh 登录，不使用聊天中曾暴露的 PAT，不记录或上传密钥。继续把实现、验收与未完成项分别写清。