# COUI 隔离资格预览

固定 COUI／M3 桥 0.0.1-alpha9，仅验证新 UI 依赖和组件。用户已批准先资格再迁移。独立包 `io.github.asagnc.aurora.uiqualification` 无网络权限、不接 Root／数据库／凭据或主 App 设置；页面内容与时间明确标为示例，不发送模型请求。安装需用户连接手机后进行，不能将预览当作主包迁移或验收完成。

使用仓库 Gradle wrapper，指定本目录为 `-p` 工程。本机 Android SDK 通过不入库的 local.properties 提供。执行 `verifyRuntimeVersions assembleDebug assembleRelease lintDebug lintRelease`；解析图落 build/qualification-runtime.txt，仅允许隔离资格已明确固定的 Compose1.13.0-alpha02 与 M3rc01，拒绝其他预发行版本。稳定 BOM 宿主解析已失败并保留记录；该工程不是主包依赖基线。库自身的 alpha BOM 约束须与宿主固定版本一起实际解析，不用 force／exclude 掩盖冲突。

运行时核对明暗、开关、顶栏材质、长按选择、复制预览文本、键盘／滚动／大字体／横屏及日志。只有这些证据和兼容性通过后再迁移主包；发布来源与 NOTICE 另须绑定。源要求见 ../../docs/UI_COLOROS17_PROPOSAL.md。
