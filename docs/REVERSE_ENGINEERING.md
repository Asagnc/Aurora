# 逆向与网络工具实施记录

2026-10-15。此文件是范围和候选方案记录，不代表工具已经安装或可运行。

## 已确认的约束

- 抓包只提供 Root 原生后端，暂不开发 VPN 抓包，不依赖第三方抓包 App。
- 普通文件、对话与浏览器保持 Android 原生路径；不为缺少权限的能力自动增加转发、模拟或高开销替代层。
- Root 权限由用户明确授权。当前 RootService 只实现只读握手，尚无抓包或任意命令执行能力。
- 抓包、分析与发包是不同能力。浏览器代理不是全机抓包，也不代表自动解密 TLS。
- 用户要求 APK/DEX、原生库、动态分析、抓包和发包均纳入后续范围；不能以一个空白插件入口代替实现。

## 候选工具分工

| 能力 | 当前主流候选 | 尚需验证 |
| --- | --- | --- |
| APK/DEX 静态分析 | JADX；Apktool 处理清单、资源与重建 | Android ARM64/JVM 运行、内存预算、反编译失败及产物保存 |
| ELF/原生库 | radare2 | ARM64 构建、符号/架构识别、批处理与大文件开销 |
| 动态分析 | Frida，按任务显式启动 | Android 16/SELinux、版本匹配、附加失败、退出与子进程清理 |
| Root 原始抓包 | libpcap + tcpdump 原生进程 | NDK 构建、接口枚举、过滤、丢包计数、文件轮转、停止后落盘 |
| HTTP 调试、修改与重放 | 原生请求工具；mitmproxy 为独立候选插件 | 头/体保真、流量导入导出、TLS 配置和 Android ARM64 环境成本 |
| TCP/UDP 与原始发包 | 普通套接字与单独的 Root 原始套接字后端 | 协议类型、批量预算、取消、错误与实际发送计数 |

上述第三方运行器与抓包二进制尚未接入。特别是 mitmproxy 的依赖与执行环境还未测量，不能默认开启或因为功能丰富就放到所有网络请求链路中。版本安装应在采用时重新核实稳定发布、来源、校验和与许可，固定每次任务使用的版本。

## Root 抓包实现验收点

1. 模型发起结构化抓包任务，绑定会话 ID、接口、过滤式、时长/大小预算和输出位置。
2. 独立原生进程执行，优先在内核过滤；批量落盘，不逐包转 JSON 或传给模型。
3. 需要时才解析有界片段，返回计数、过滤范围、丢包情况和可继续读取的文件位置。
4. 停止、取消、撤权和会话删除必须等待进程结束与文件关闭。中断结果明确标记未完成。
5. 原始流量默认属于会话空间；显式保留或导出后才跨会话存在。工具与依赖属于共享只读资源。
6. 在真实 ARM64 Android 16 上验证普通流量、代理流量、并发任务、持续运行的 CPU/内存/耗电与丢包。没有实测不能宣称零开销或全协议可解密。

## 一手资料

- [tcpdump 源码与标签](https://github.com/the-tcpdump-group/tcpdump)，[libpcap](https://github.com/the-tcpdump-group/libpcap)：核查到 tcpdump-4.99.7、libpcap-1.11.0 标签；尚未核验 Android 构建。
- [JADX 发布](https://github.com/skylot/jadx/releases/tag/v1.5.6)、[Apktool 发布](https://github.com/iBotPeaches/Apktool/releases/tag/v3.0.3)。
- [radare2 发布](https://github.com/radareorg/radare2/releases/tag/6.2.4)、[Frida 发布](https://github.com/frida/frida/releases/tag/17.23.3)。
- [mitmproxy 发布](https://github.com/mitmproxy/mitmproxy/releases/tag/v12.2.3)、[功能和限制](https://docs.mitmproxy.org/stable/overview/features/)。
- [Android VPN 生命周期与单实例限制](https://developer.android.com/develop/connectivity/vpn)：已据此讨论并排除当前 VPN 抓包方案。

2026-10-16 / 0.1.6：普通 UID 原生命令已接入（NATIVE_COMMANDS.md），提供监督执行、输出产物、超时和取消基础；不是 Root 抓包/逆向插件已安装的证据。RootService 仍只读握手。

2026-10-17 / 0.1.7：独立 Root 命令接口已接入，真实 Binder/原生清理通过普通 UID 跨进程用例；真正 UID 0 的执行/产物归属尚待真机验证。参见 ROOT_COMMANDS.md。Root 命令入口不等于抓包/发包、Frida、Linux 或编译工具包已安装。
