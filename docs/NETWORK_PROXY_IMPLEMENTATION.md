# 显式网络代理：实现与资格（2026-10-26）

用户已批准 NETWORK_ADVANCED_PROPOSAL 的固定 mitmproxy12.3.0 ARM64 standalone／现有 Root chroot 路线。此文件区分产品实现与设备资格。

- `NetworkProxyPreparation` 验证固定98,927,179字节归档和精确3成员，校验ELF64 AArch64，仅保留32,495,056字节/SHA256 3adf33868f5477ff65d954b3fbe5dc5eca9703b5f02692500e880b8789caccb1 的mitmdump。
- 生产profile来自APK资产，8项固定SHA包含7份自有脚本与上游LICENSE；改动产生不同bundle。与JADX/SDK共用单一安装器、原子发布、暂存清理和持久Linux任务租约，没有第二个daemon或任务表。
- `shared_toolchain family=network-proxy install/probe/status`；probe仅自有loopback HTTP、HTTPS、未信任CA拒绝和WebSocket，随后检查真实worker/domain结束与绑定session/config摘要的Root产物。资格仅属于当前Root身份、Linux代和bundle；host CI不预置手机资格。
- `network_proxy action=start` 复用现有PTY宿主/前台任务服务。记录持久Linux意图及工具bundle后派发，Root身份须与资格检查相同。实际监听确认来自固定profile的session/port标记和仍运行的会话快照。返回异步会话，不冒充任务已经结束。
- `stop`仅停止所选终端及恢复同一run，不恢复或停止其他会话。Root未知退出/未确认清理继续保留原意图和版本租约。对话删除仍先停终端、取得Linux关闭事实，再删除Root文件。

## 规则与预算

显式regular代理，仅127.0.0.1和指定1024..65535端口；不改全局路由，不安装系统CA，不自动让模型请求走代理。客户端须显式选择代理并信任该会话CA；不宣称绕过证书绑定或覆盖不使用代理的流量。

字面精确小写ASCII host、pathPrefix、可选method；HTTP头设置/删除、正文替换，以及显式client/server方向的WebSocket替换。没有任意Python/正则/脚本入口。禁止手工修改framing/routing头；正文替换由成熟HTTP层重设长度，并移除旧编码/摘要/实体标签。无正文的响应不插入正文。

最长240秒、512个HTTP流、最多16个同时连接/活动流、1024个保存事件、32MiB保存产物；正文上限1MiB、单规则替换16KiB、配置32KiB，均保留既有Linux argv/270秒任务预算。保存字节包括公有CA/流/索引/WS消息并预留最终记录空间。上游WebSocket组装消息的瞬时内存不属于硬heap配额；Android性能/RSS待测。

## 产物、CA与读取

每会话CA和PyInstaller私有运行目录位于Root工作区的`.aurora-proxy-private/<session>`；标准Linux文件API仅允许home/tmp/project，不能从此私有位置导出密钥。公有证书和产物在`project/.aurora-proxy/<session>`；没有自动信任安装。

`flows.mitm`保存有界完成HTTP流；`index.jsonl`保存索引；已接受WS消息逐个保存及SHA，避免在内存保留无限历史。流文件中的WS历史只保留末条，完整已保存消息来自独立索引/文件。取消/崩溃可能只留下完成前缀，缺最终记录不冒充完整会话。

`inspect`须先停止/恢复同一工作区任务，使用固定只读脚本和有界FlowReader；返回最多10个流及有界原始正文预览，敏感头/query隐藏。用户数据仍可能存在于正文预览。文件大小/物理属性变化、解析错误/截断明确报告，不重放任何请求。

`export_ca`先严格验证单个X.509公有PEM，拒绝私钥、多块、非ASCII/超预算数据，再将选定SHA和16KiB预算交给Root流式导出。源FD不跟随链接，检查前后物理大小/时间，最终副本须摘要一致并fsync后交接；普通文件32MiB上限保持。

## 实际证据与设备待验

生产profile ARM64专项40294444272全部通过，实际Root私有mount/network namespace、readonly `/opt`、HTTP/严格HTTPS/未信任CA/WS修改、公有证书与消息字节/离线回读。此证明不等于Android Binder/PTY/cgroup运行资格。固定源码/14内嵌ELF对应及静态OpenSSL来源见现有materials文件和专项；不扩大成全部目标可复现构建或整体法律结论。

本地334核心319通过/15跳过；Debug/Release/AndroidTest/lint共220任务通过。新增普通UID FD导出用例和Root/Linux/proxy联调用例仅编译。Root设备用例要求预先明确安装固定bundle与Root Linux，并同时设置auroraRootTest/auroraLinuxImageTest/auroraProxyTest，使用自有临时对话及loopback，不自动下载，不触及其他对话或签名密钥。单个有序设备场景保留探测/PTY/公有CA/停止步骤，以验证同一持久租约的完整生命周期。

后续集中验证Android真实CA行为、PTY/撤权/进程死亡/后台/取消与删除矩阵、丢失最终产物及私有运行目录清理；不操作手机来代替当前主机开发。
