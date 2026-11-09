# 对话体验与 ColorOS 17 视觉方案（2026-11-09）

状态（2026-11-10）：用户已批准固定COUI alpha9先资格再迁移。独立基础资格后，主包预览已接入同一固定Compose alpha组合；消息时间／复制布局与47项数据回归通过，最终APK和CI收尾中。不是正式发布或完整视觉／性能／无障碍资格。

## 实际对照

- [Eta v3.4.1](https://github.com/Mangi-11/Eta/releases/tag/v3.4.1) 于 2026-11-09 发布，发行说明和 tag 代码确认新增 ColorOS 17 风格、COUI 与 Miuix 切换、收缩大标题、浮动操作、弹性反馈。tag 的 gradle/libs.versions.toml 固定 `io.github.itoseo:coui:0.0.1-alpha7`；回复操作改到正文下沿。当前是源码／发行说明比较，尚未在设备安装新版 Eta 作并排视觉实测。
- [OPPO ColorOS 17](https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/) 描述 Living Flow Design 的半透明层次、光感及连续运动。应用可以采用这种组件和交互方向，不能承诺复现系统私有渲染引擎或系统级性能。
- [COUI-Kit](https://github.com/ItosEO/COUI-Kit) 是第三方 Compose 库，**不是 OPPO 官方 SDK**。最新版已发布 Maven Central 的 `coui`／`coui-material3:0.0.1-alpha9`；v0.0.1-alpha9 的 annotated tag 指向 commit `261658dc200c143c5ea883591662479344d25ba1`。比 Eta 当前 alpha7 更新，仍是预发行，不能称成熟稳定。

## 比较与推荐

| 路线 | 优点 | 代价／边界 |
| --- | --- | --- |
| 沿现有 Material3 只整理布局、时间、操作 | 无新增库，最容易保持已有资格 | 能改善体验，但圆角／配色不能直接变成 COUI 的材质、菜单、动效 |
| 固定 COUI-Kit alpha9，先做隔离预览资格（推荐） | 与新版 Eta 同一组件来源、较新修正；可复用 Compose 与现有 M3 颜色桥接 | alpha 新依赖，需单独批准；源码／编译兼容不等于设备视觉与性能通过 |
| 引入另一套 Miuix 或较旧 ColorOS16 仿制库 | 有独立组件体系 | 多一整套样式／状态维护，与用户本轮 ColorOS17 目标不吻合，不推荐同时引入 |

推荐先固定 alpha9 做资格，不立刻整库替换；范围是对话、抽屉、输入区与设置共有组件。没有默认增加第二套主题选项／持久化配置的授权；先通过预览确定一种完整视觉方向，再决定正式迁移。

## 已查到的实际兼容要求

2026-11-09直接读取 Maven Central metadata、两个 .module 及两个 AAR 的 aar-metadata.properties：alpha9 已发布；minCompileSdk=37、minAGP=9.1.0，当前 Aurora compile37／AGP9.4.1 满足表面门槛。tag 库配置 minSdk31，Aurora min36，不添加旧 Android 路径。Kotlin metadata 基线2.4.20，仍须消费编译验证。

COUI 公开 API 依赖 **compose-bom-alpha:2026.08.01**，Foundation／Runtime1.12.0；当前 Aurora 稳定 BOM2026.09.00／Foundation1.12.1 与显式批准的 Material3 1.5.0-rc01。版本号较低不保证约束无影响；资格必须记录最终依赖图，检查所有 Compose 解析版本，不用 force／exclude 隐藏冲突，也不借此自动升级其他库。core-ktx1.19.1、DynamicAnimation1.1.0、stdlib2.4.20 等传递项同时核对。`coui-material3` 是可选颜色／排版桥，不等于一键让所有 M3 控件成为 COUI。

许可声明 MPL2.0，来源与 NOTICE／图标及第三方说明须保留；本次检索不构成完整发布许可闭包。未在生产依赖或 Application 中初始化 COUI。

## 对话整体验收范围

1. 用户消息：去掉正文上方永久复制按钮；保留原生长按选择／复制。时间放气泡外右下，不挤占正文。
2. 回复：正文下方放低强调操作，复制不再抢占标题右上；图标可以小，触摸区域维持48dp。代码块专用复制保留。
3. 时间：新消息保存真实 wall-clock 时间；回复是完成／中断落盘时间。旧数据库无消息时间，迁移保持 null，不用会话更新时间伪造。抽屉已有真实 updated_at，显示会话最后活动时间，与消息时间分开。
4. 全局比例：顶栏标题／次级信息、消息宽度／行距／留白、用户和助手辨识、输入区控件密度／禁用和发送中反馈、空态／任务状态整体检查，不能用一堆说明文字弥补交互。
5. COUI 预览：顶栏／菜单／列表／控件统一选型；材质保证对比度，避免给长篇 Markdown 背景叠加昂贵模糊。明暗／纯黑／动态色可读，返回、输入法、长文选择、滚动跟随、附件／模型／思考／上下文入口完整。
6. 测试：宿主 Debug／默认 R8 Release／AndroidTest 编译与 lint；手机真实长短消息、代码／长文、时间跨日期、长按、流式及中断、键盘、横屏／大字体／无障碍。无付费模型，可用受控测试消息与既有本地夹具；不得把编译通过当视觉验收。动效／模糊性能须实测，不预先承诺帧率。

## 确认边界

AGENTS.md：`Current stable dependencies and tools only.` 和 `Confirm newly introduced architectural choices before implementing them.` 用户原约定新底层先比较确认。Material3 RC01 的批准不涵盖 COUI alpha9／其 alpha BOM；本方案需确认新增依赖的资格路线。消息时间沿既有 SQLite 增加 nullable 字段、复制位置与普通布局整理是当前需求内的例行实现，继续推进，不等待 COUI 选择。


## 资格初查：稳定宿主不相容（实际结果）

2026-11-09用户确认后创建独立、无权限的 tools/coui-qualification。首次 Gradle 脚本配置时机错误，尚未解析，已修正。随后真实解析被版本守卫拒绝：COUI 的 alpha BOM 将 UI／Foundation／Animation／Runtime／material-ripple 等15个 Android Compose artifact 选为 **1.13.0-alpha02**。因此不能声称“主项目满足最低 SDK，即可兼容现有稳定 Compose”。失败日志保留在 app/build/ui-coui-review。

后续隔离资格明确固定 COUI 要求的 alpha BOM2026.08.01，允许且核对唯一指定的 Compose1.13.0-alpha02 和原已批准的 M3rc01；不是 force／exclude，也不把稳定宿主失败改记通过。主 app 依赖仍保持原样，隔离预览不代表主包迁移完成。主包若采用这套组合，需明示 Compose 基线变化，保留原构建结果，并重新执行完整 CI 与主包真机检查。


## 2026-11-10接入状态

用户已批准路线。独立预览固定alpha组合经Debug／R8 Release／双lint及手机基础交互验证后，主包沿同一固定Compose alpha组合进入集成；主包完整宿主批次224任务127秒成功，后续排版与表面修正221任务62／58秒成功。主包47项数据与恢复设备回归成功，UI检查与剩余边界见UI_CHAT_IMPLEMENTATION。初期“主依赖未改”是隔离阶段的历史状态，不代表当前工作树。尚未正式发布或授予全矩阵资格。
