# 组合抓包的运行控制细化（已确认，2026-10-23）

已经确认 libpcap/tcpdump/Aurora 的组合与 Root-only。本项细化实时采集进程的控制方式；现有原生组件、离线 packet_inspect 与写盘检查已实现，不重新审批这些部分。

## 新发现的控制需求

- Agent 的“采集指定时长”是预期完成条件，需要与取消、硬超时和强杀区分。
- tcpdump 的轮转大小是阈值，可能多写一个包；简单命令超时加轮转参数不能直接等同“到字节预算完整停止”。
- stderr 文本统计与文件存在不足以证明写盘和任务完成；不能静默把未知结果转为成功。
- 当前适配已检查 tcpdump 的退出/轮转 flush/ferror/fsync，主机十项步骤通过，完整 CI 与上游 /dev/full 对照日志待最后归档。这解决 I/O 边界，尚不提供完整业务控制协议。

## 推荐的组合分工

**实时采集：libpcap + Aurora 小型原生 worker；离线过滤/解析：已接入的 tcpdump；任务/产物：现有 RootService、命令监督和工具账本。**

采集仍使用同一个已锁定 libpcap1.10.7，复用其 BPF、mmap/TPACKET 支持、标准 PCAP 写盘与计数，不自写 AF_PACKET/TPACKET 或协议解码器。tcpdump4.99.7继续承担成熟离线读取/解析。运行时只启动一个采集进程；没有第二套采集库、VPN、额外守护进程或全局内核策略。

| 控制方式 | 优点 | 问题与取舍 |
| --- | --- | --- |
| tcpdump + 现有命令截止 | 接入代码最少，成熟组件 | 预期停止与命令超时重叠；严格整包预算、结构化状态仍需适配 |
| 继续向 tcpdump 加自定义时长/预算选项 | 保留其写盘回调 | 增加长期上游补丁和全局状态维护面 |
| **小型 libpcap worker** | 直接定义任务条件和结构化结果，检查下一个包后才写入 | 新增少量 C 控制代码，必须覆盖失败/取消/异常/预算/接口事件 |
| 自写 PACKET_MMAP/AF_XDP | 更多底层控制 | 重复成熟采集库，能力要求和维护面更大，不采用 |

选择依据是业务控制清晰与可验证性，不宣称小 worker 已实测比 tcpdump 更快。上线资格仍要求吞吐/CPU/丢包对照；如果不能达到合同则保留不可用并报告实际原因。

## 控制与存储合同

1. 独立 NDK C 进程链接同一静态 libpcap；复用已批准 RootService/NativeCommandRunner 的派发、进程死亡/取消、输出日志、任务生命周期与有界 CPU 租约。不新增 SQLite 表或常驻服务。
2. `pcap_get_selectable_fd`、非阻塞读取、poll，加 signalfd 接收取消、timerfd/CLOCK_BOOTTIME 表示一次任务截止。时间包含系统挂起；使用普通 timerfd，不增加 ALARM 能力。没有逐包 Binder/JNI/JSON、忙等或应用层定时扫文件。无法提供可用事件 FD 的接口明确不支持当前后端。
3. libpcap保持缓冲交付；每轮处理有界批次，在批次间检查取消/截止。缓冲大小与延迟参数按测量确定，不复制别的 App 的 1ms 常数。
4. 第一阶段一个标准 PCAP 文件：全局头、每条记录头与caplen均计入总字节预算，写入前检查下一条完整记录，达到预算就停，不截半包。分别记录保存包数、存储字节、库报告的接收/丢包，不混淆含义。文件轮转作为后续能力，不通过覆盖旧包假装保留完整历史。
5. 时长/包数/字节预算是明确完成原因；取消、硬超时、I/O失败、未知服务退出为不同状态。结束检查flush/ferror/fsync；SIGKILL不能运行收尾，不承诺完整。现有命令宿主硬超时留额外收尾余量，第一阶段仍服从五分钟总执行范围。
6. 输入由严格结构化请求生成字面参数，接口/过滤/时长/预算有界。输出只在本次会话运行目录，不接受任意Root输出路径。Root在确认进程结束后，通过O_NOFOLLOW打开本次唯一产物，复核类型/链接数/范围，仅恢复该文件的App属主、同步目录并发布元数据。失败保留真实状态，不递归chown、扫描整个会话或自动重放。
7. 离线分析维持普通UID；Root不承担完整协议显示/解析。实时采集缺权限或SELinux拒绝就返回不可用，不修改系统策略。UID归属、TLS解密和任意发包另列能力，不附加到每个包的热路径。

## 验证顺序

先编译NDK双ABI和来源/ELF/许可；主机离线重放验证完整包预算和I/O错误，再在私有网络命名空间中验证受控loopback流量、空闲截止、取消、接口事件、掉包计数与强杀。最后按用户要求统一Android16 Root真机：Root拒绝/断连、文件归属、会话删除、亮灭屏、受控流量及吞吐/CPU/内存/功耗，证据不足就不标可用。

来源：[libpcap FD合同](https://www.man7.org/linux/man-pages/man3/pcap_get_selectable_fd.3pcap.html)、[signalfd](https://man7.org/linux/man-pages/man2/signalfd.2.html)、[timerfd/CLOCK_BOOTTIME](https://man7.org/linux/man-pages/man2/timerfd_create.2.html)、[已核查的Android原生采集实现](https://github.com/emanuele-f/PCAPdroid/blob/66f7b3514540c20add8655a74dfee93d00a9563b/app/src/main/jni/pcapd/pcapd.c)。仅学习架构，worker由Aurora实现并复用已锁定libpcap。
