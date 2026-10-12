# Aurora 接续记录

## 下一次对话从这里开始

1. 阅读 `AGENTS.md`、`docs/DESIGN.md` 和本文件。
2. 检查实际源码及 Git 状态，不把计划当作已经实现的功能。
3. 对照下方验收结果继续工作；所有未验证项保留明确状态。

## 已确认

设计基线、Root/普通权限路线、三类空间、界面、多选图标、最新稳定版策略、手机 APK/JNI 编译范围、缓存切换策略、批量文件合同和 WebView 浏览器路线均已获确认。

## 实现待办

- [ ] 最新稳定构建组合通过 debug、release、lint 和核心测试。
- [ ] 真实会话界面、抽屉多选/置顶/重命名/删除、底部输入框及指标详情。
- [ ] 官方供应商及自定义中转配置，密钥通过 Android Keystore 加密保存。
- [ ] Chat Completions、Responses、Anthropic Messages 的流式解析、工具循环及原生状态保留。
- [ ] 缓存策略、配置切换确认及实际用量统计。
- [ ] 会话持久化、三类空间、删除日志和恢复。
- [ ] 批量读取、版本检查、批量编辑和中断恢复。
- [ ] 多页面浏览器、Profile 隔离、代理、JS、文件上传下载及 renderer 恢复。
- [ ] RootService、系统 API 和系统操作，撤权与进程取消。
- [ ] PRoot/chroot 安装、运行、挂载及清理。
- [ ] Skills/工具插件安装、依赖锁定、校验和回滚。
- [ ] ARM64 原生 APK/NDK/JNI 编译工具链及实际构建验收。
- [ ] 设备操作、无障碍控件和视觉补充。
- [ ] 原生 launcher 图标层、真实供应商品牌图标及来源说明。
- [ ] Macrobenchmark、Baseline Profiles、内存/耗电/任务成功率和 Eta 对比。

## 真实环境

- 开发机：Windows，Android Studio Java 25、SDK API 37、Build Tools 36 已存在。
- 基准手机：realme GT6 SE，骁龙 7+ Gen 3，16 GB/256 GB，Android 16、realme UI 7.0。
- Root：FolkPatch 115356，KernelPatch 0.13.9；LSPosed 2.2.1。
- 当前未连接 ADB 设备。Root、ARM64 工具链与手机性能结果尚未产生。

## 验收结果

核心编译通过；16 项协议/文件测试通过。Android 应用尚未构建，没有可安装 APK。详细合同和验收证据见 `docs/IMPLEMENTATION.md`。

## 当前源码与下一步

- `core`：三种协议请求编码、SSE、流式组装、原生状态、工具循环、缓存影响分类、批量文件读取/编辑/恢复均已实现并有核心测试。
- `app`：Android 16+ 构建配置，尚未创建 Application/Activity/服务实现。
- GitHub CLI 已完成 Asagnc 登录；凭据由本机系统保存，不写入项目。
- 下一步：网络传输与凭据保险库、持久化及模型能力目录，然后真实 Compose 界面与 Android debug/release/lint。不能把核心测试结果当作手机验收。
