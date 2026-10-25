# 网络进阶：分工与新增 TLS 后端（已确认）

2026-10-25。用户确认“采用推荐组合，先验证后接入”。此前已实现的是Root-only libpcap采集＋tcpdump离线读；方案获批不等于发包、HTTP重放、TLS解密或UID归属已经完成。

| 工作 | 推荐复用 | 关键边界 |
| --- | --- | --- |
| 有界原始发包/选定PCAP重放 | 已锁定libpcap的pcap_inject与现有Root任务监督 | 必须显式接口、帧/字节/速率/截止；caplen不足或链路类型不匹配拒绝，cooked捕获不能直接伪装以太网；内核/驱动可能拒绝或改写链路头。 |
| 单次HTTP构造/重放 | 已有OkHttp5.5.0、连接代理与会话文件/产物通道 | 明确目标/方法/请求体及重定向，不自动带其他站点Cookie/凭据；不能把TCP包重发叫完整HTTP会话重放。 |
| HTTP(S)/WebSocket观测和规则修改 | 新增按需mitmproxy12.3.0 Linux ARM64独立包，在现有Root chroot受管执行 | 完整TLS/HTTP状态机交给成熟工具，App只传有界规则与索引；不进入UI进程，不给每次模型请求增加代理。 |

## TLS后端比较

| 路线 | 取舍 |
| --- | --- |
| 自写TLS/HTTP2/WebSocket代理 | 依赖少，但协议状态、证书和流控维护面大，不选。 |
| OkHttp拦截器代替通用代理 | 适合Aurora自己发出的请求，不能代表其他App或浏览器全部流量；保留单次重放用途。 |
| 桌面Java图形代理或完整Python/pip环境 | 增加无关UI或依赖安装链，不作为首版。 |
| mitmproxy官方ARM64 standalone，仅按任务启动 | 成熟协议/修改/重放能力，沿用共享版本/只读挂载/持久租约/退出证明，推荐。仍须验证ELF、内嵌运行库、许可与实际Root运行闭包。 |

官方最新非预发布12.3.0于2026-10-22发布。官方下载目录列出`mitmproxy-12.3.0-linux-aarch64.tar.gz`为98,927,179字节；已实际下载并固定SHA256 f8756fa831c4abe431dd922ae7291b158b465285562b35d12e5669cbfc29d1f3。只读检查识别121内嵌条目、14 ARM64 ELF与Python3.14；运行和完整许可闭包另验，不显示proxyReady。真实ARM64自有loopback资格入口见scripts/qualify-network-proxy.py。

首版选regular显式代理，仅绑定loopback并在输出中明确实际地址/端口，默认不改系统代理或全局路由；由用户为选定客户端设置代理。证书只导出公有CA证书，CA私钥保持任务/所有者私有；不自动安装系统CA。TLS观测要求客户端信任该CA，证书绑定或不使用代理的应用必须明确报出限制，不默认加全局Frida解除绑定模块，不用VPN替代Root抓包。

规则在启动前选定并固定为结构化数据，支持请求/响应头与有界正文修改；不把未审查任意Python脚本当规则接口。流量与大正文保存在会话产物，Agent按索引读取；事件/文件/总字节/截止/取消均有预算。失败/取消后worker与辅助进程退出必须经过原有Linux资源屏障，工具版本回收按已有持久租约；停止不承诺撤销已发出的请求或服务端副作用。

## 其余要求继续保留

UID归属需要另核实Android16的socket/内核观察能力，不能用包名、端口或瞬时/proc扫描猜一个唯一UID。长时轮转需要持久分段清单、总空间上限和崩溃恢复；不通过取消预算或无限常驻进程伪装完成。它们仍在待办，未因选定TLS后端而自动获准新持久格式。

验收先用自有网络命名空间/loopback HTTP、HTTPS和WebSocket服务，测试改写后的长度/编码、二进制正文、背压、证书失败、取消/崩溃和所有者隔离；再集中手机验证。无真实模型、账号凭据或用户外部业务流量作为默认夹具。

依据：[官方12.3.0发布](https://github.com/mitmproxy/mitmproxy/releases/tag/v12.3.0)、[官方下载入口](https://www.mitmproxy.org/downloads/)及其官方目录API、[代理模式](https://docs.mitmproxy.org/stable/concepts/modes/)、[证书与绑定限制](https://docs.mitmproxy.org/stable/concepts/certificates/)、[libpcap发包说明](https://www.tcpdump.org/manpages/pcap_inject.3pcap.html)。这是工程分工建议，不是手机性能或全协议能力证明。
