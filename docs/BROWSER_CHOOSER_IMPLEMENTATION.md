0.1.40接续：下载账本关联的中断恢复已实现，见BROWSER_DOWNLOAD_RECOVERY.md；下述0.1.38/0.1.39的旧缺口由该文档按范围更新，未登记/不明记录及实际设备资格仍保留。

# 原生浏览器上传与快照（0.1.39）

2026-10-23。沿用已确认的组合方案，不新增 Provider、数据库表、引擎或网络栈。Agent 的 File/DataTransfer 路径仍是 32MiB；用户真正触发的原生文件选择走本页路径，两者不能互相冒充。

## 已接入的分工

- WebChromeClient.onShowFileChooser 支持单文件/多文件，最多8项；目录、保存请求明确取消，不启用摄像头采集。不会把 JS click 伪装为可信手势。
- Aurora 内部选择面板可浏览会话文件与共享只读资源，也可打开系统 OpenDocument/OpenMultipleDocuments。尊重页面 accept/multiple；Root Linux 文件需先用现有选定导出通道发布。
- 外部 content URI 必须有明确 READ grant，并拒绝本应用 UID 的 Provider，防止把恶意结果当本应用权限的读入口。原始第三方 URI 不交给网页、不持久化授权；在 IO 上直接流式导入上传快照，无需再复制一份永久 imports。
- 复用现有非导出的 FileProvider，只回调选定快照的精确 URI，保留显示名并按来源 MIME 选择生成文件扩展名。不扩展 Provider 根、不授予整个目录或写权限；allowFileAccess/allowContentAccess 继续 false。
- 快照使用64KiB缓冲，落盘同步后发布，Android/POSIX 上置 OWNER_READ。这是复制完成后的稳定副本，不是对并发修改源文件的原子快照，也不是防任意 Root/同 UID 命令修改的安全边界。
- 保留快照的总预算每会话512MiB，每页最多16组；准备操作串行，取消的旧准备及清理完成后才启动下一组。失败清理中的字节仍计入预算。不把整套空间复制，也不把大文件塞入 Base64/模型上下文。
- 待选择请求在替换、取消、隐藏浏览器、导航或关闭时只回调一次 null；正在准备的 Job 被取消并在会话 OwnedTasks 租约内收尾，删除空间等待它结束。选定成功后只回调一次 URI。

## 对导航释放的保守细化

最初方案提出导航/关闭释放。原生上传读取由 WebView 负责，页面导航开始不是上传已读完的证明。现实现对“尚未提交的选择”导航即取消；对“已经交付的 URI 快照”保留到关页或删会话，避免普通表单导航中抢先删除。UI明确说明该寿命，总量仍受预算约束。

关页后异步清理已知快照，失败记录不丢弃；删会话先等待并重试清理，再删除浏览 Profile/空间。对已打开 FD，删除路径不承诺瞬间撤销或立即归还最后一份存储引用。真实表单上传和关闭取消仍需设备验证。

## 进程死亡与有界恢复

目录为 .aurora-browser-uploads/<process-epoch>/<lease-UUID>，独立进程 epoch、固定版本标记、生成文件名。当前 epoch 永不回收；下一次显式准备上传时，最多检查32个旧租约、枚举128个epoch，只清理结构完全识别的普通文件。链接、未知文件、损坏/缺失标记保留，不递归跟随或删除其他空间。没有常驻线程或定时全盘扫描。

这是上传快照恢复，不包括0.1.38下载任务在进程死亡后残留的 .partial；后者仍需账本关联的有界处理。未知条目或达到上限的旧快照可暂留，不能称全部垃圾已自动清空。文件目录本身的断电持久性没有因同步单个文件而被额外保证。

## 验证

- 核心新增5项：稳定副本/空文件/MIME扩展名；总量/数量/取消无临时文件；旧代有界回收及当前/未知保留；无标记/非法输入；POSIX链接拒绝与外部文件保留。Windows后者跳过，留Linux CI实跑。
- 本地主机196项：188通过、8 POSIX跳过。Node的既有生产脚本场景仍在原JUnit里，不另计。
- Android BrowserChooserTest新增5项：选中URI可读及关页释放；替换/导航只取消一次；已交付URI跨导航保留与删会话释放；不支持模式取消；恶意自有Provider URI拒绝。仅编译，未在手机执行。
- Android测试调用原生回调来验证生命周期，不伪称是真用户手势或Chrome真实文件输入已经成功；最终还要点击页面 input 并向受控服务端逐字节验证，覆盖 allowContentAccess=false、真实SAF授权、MIME/文件名和延迟/重定向上传。
- SAF提供器的打开/查询/阻塞读取可能不响应协程取消；当前保证不在IO未收尾时删除会话，不保证能强制终止任意第三方提供器。真实慢源/取消/断电/磁盘满与长期预算留最终集中手机。

一手合同：[WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient)、[FileProvider精确URI及显示名](https://developer.android.com/reference/androidx/core/content/FileProvider)、[Context.checkUriPermission明确URI授权](https://developer.android.com/reference/android/content/Context#checkUriPermission(android.net.Uri,int,int,int))。
