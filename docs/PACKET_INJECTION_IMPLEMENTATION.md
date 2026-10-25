# Root原始Ethernet帧发送

2026-10-25。复用已批准的libpcap1.10.7、Root Binder/RootCommandSession、独立受管进程与现有会话活动记录，不新增代理、VPN、路由或常驻服务。

## 已实现

Agent `packet_inject_root` 必须给出明确接口和完整帧的Base64。`any`禁止，实际接口须为DLT_EN10MB；不把Linux cooked捕获、IP-only数据或PCAP文件当Ethernet帧。
每帧14..16384字节，默认发1帧；`packets`明确重复此帧，最多4096次。
默认10包/秒、5秒、1MiB，分别最多1000包/秒、30秒、16MiB。参数在App及Root Binder两侧严格重新解码校验；没有shell插值。

独立打包的 `libaurora_packet_inject.so` 只在Root UID运行，使用非混杂pcap、非阻塞发送、signalfd和CLOCK_BOOTTIME截止/节流timerfd。
完整包预算在发送前检查；每次成功发送后安排最小间隔，不在CPU暂停后突发补发。遇到首次负返回或短写立即停止，不自动重试该帧。

`injection`结果明确 `requestedPackets`、`acceptedPackets`、`acceptedBytes`、`partialBytes`、`reason`、`complete`及errno。
只在完整接受全部请求帧、实际进程正常退出且stdout捕获完整、计数守恒时 `complete=true`。
时间/字节预算提前停止返回非零及已有部分计数；取消、外层硬停止、服务死亡、坏/截短结果不得伪装成功。
Root客户端沿用有限CPU锁及NonCancellable cancelAndWait，活动账本在发送前登记目录，UI区分未知结果。

pcap接受不是送达保证：驱动可能拒绝/改写头部，接收端可能丢包。这里没有TCP状态机、HTTP重放、TLS解密或UID归属。
classic PCAP选定片段回放已接packet_replay_root，范围和拒绝边界见PACKET_REPLAY_IMPLEMENTATION.md。发送结果升级内部protocol2，记录selectedFrames和选定帧总字节，逐前缀核验变长帧计数。

## 验证状态

- 本地NDK30双ABI、16KiB ELF/依赖审计、Debug/Release/testAPK/lint通过；核心284项272通过/12跳过，新增4项参数/证明测试。
- 新Linux实验仅在fresh private netns中创建自有veth对：真实收帧字节与内核时间戳、整帧预算、截止、取消、MTU拒绝和非法输入；额外host-only链接替身验证短写不重试，不将其当真实网卡短写证据。7项已通过CI40185946093；扩展PCAP选择/多帧实验另行追踪。
- 新普通UID拒绝用例和显式Root/loopback/实验EtherType自有帧用例只编译，尚未操作手机。
- 真机用例要求auroraRootTest和auroraPacketInjectionTest显式开启；只选lo、发1个自有实验帧，不改路由/接口配置。不能证明实际结果时保留诊断会话。

源码入口：core/PacketInjection.kt、runtime/PacketInjectionTool.kt、root/RootPacketInjection.kt、packet/Inject.c。
原libpcap许可和精确源码包继续适用；对应源码打包器自动包含新增packet源码，APK审计显式核对新增payload。

依据：[libpcap pcap_inject官方说明](https://www.tcpdump.org/manpages/pcap_inject.3pcap.html)。
