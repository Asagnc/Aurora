# Root 原生抓包组件选择（待确认，2026-10-23）

已经批准 Root-only、独立原生进程、无 VPN/第三方 App；本提案只确认尚未选定的组件和产物接入。未安装或启用抓包。

## 推荐

从上游源码固定构建 **tcpdump 4.99.7 + libpcap 1.10.7**，作为按需进程复用既有 Root 命令监督、会话任务、取消、日志与有界 CPU 租约。libpcap 静态链接进独立 tcpdump 可执行文件；APK 按现有 nativeLibraryDir/16KiB 双 ABI 分发，锁定源码 SHA256、保留许可和对应源码。

官方首页当前分别标记 tcpdump 4.99.7、libpcap 1.10.7 为 stable；libpcap 1.11.0 虽标签更新，但仍为 testing，因此不符合“最新稳定版”的要求。此前 REVERSE_ENGINEERING.md 只记录了 1.11.0 标签，不把它当作稳定依赖。正式接入前复核官网、源码摘要与 Android 构建，不采用第三方预编译下载。

## 比较

| 方案 | 收益与成本 | 决定 |
| --- | --- | --- |
| tcpdump + libpcap | 复用过滤、写盘、轮转、协议读取与成熟错误路径；额外二进制体积，统计输出须按固定版本适配 | 推荐先以成熟组件交付并测量 |
| libpcap + 自写 C 抓包程序 | 可直接返回结构化统计、精细控制事件循环；要自行实现和测试轮转/异常/离线读取 | 合理后续优化，不能未测就声称更快 |
| 直接 AF_PACKET/TPACKET_V3 | 能控制 Linux 环形缓冲；自行处理过滤编译、链路类型和兼容路径，维护面最大 | 当前不重复实现 libpcap |
| Java/Kotlin 每包回调 | 方便 UI，但逐包对象/跨层传输不符合吞吐目标 | 不采用 |
| VPN 抓包 | 不依赖 Root，但与用户决定冲突 | 不采用 |

## 用户要求补充的多源核查

本次进一步读了 Linux 内核 PACKET_MMAP/AF_XDP 文档、固定版本 libpcap 的 pcap-linux.c、tcpdump.c、Android PCAPdroid 原生 pcapd、Termux 当前包配方及 Eta/DSHA 的代码检索。结论仍推荐 tcpdump + libpcap，但理由与资格要求更具体：

1. **底层快路径已经存在。** 内核文档推荐使用高层 libpcap，并说明其已支持 PACKET_MMAP/TPACKET_V3。libpcap 1.10.7 在非 immediate 模式下尝试 V3，内核不支持时再处理其他模式。不能把“自己写 AF_PACKET”直接等同更快，更不能把编译有 V3 当作设备实际启用证明。
2. **tcpdump 写包不等于逐包解析显示。** 固定源码中，使用写文件且不额外打印时选择 dump_packet/dump_packet_and_trunc，省去协议显示路径。自写采集器仍调用同一 libpcap/pcap_dump；没有实测依据把它标成吞吐赢家。
3. **Android 项目的有效经验。** PCAPdroid 固定提交的 pcapd 使用 pcap_create、非 immediate 模式和可选读 FD；源码记录即时交付的上下文切换成本，并对接口关闭时的读取阻塞增加非阻塞处理。它的 1ms 超时服务于自身连接分析需求，Aurora 不机械复制这个参数，也不增加逐包 JNI/模型传输。
4. **较低层方案并非免费收益。** 自建 libpcap worker 对结构化实时状态、严格逐字节预算和定制控制更直接，但要自行承担采集、轮转、恢复及异常组合。直接 TPACKET_V3 进一步重复库内实现。AF_XDP 需要 XDP/UMEM/队列等额外能力，并涉及数据路径配置，不作为普通 Root 手机的默认观察工具。
5. **版本以官方稳定渠道为准。** Termux 当前配方采用 1.11.0，但 libpcap 官网仍把它列为 testing。Aurora 按用户要求选官网 stable 1.10.7，而非盲从最高标签或其他发行渠道。
6. **Eta/DSHA 不作为抓包性能证据。** 本次 HEAD 分别为 5e9d3a7b5a7b9d348f36222a629d0626a98ccd0b、8fea81f01d3c74e3fbc8568b83615bc42171c9c6；tcpdump/libpcap 关键词检索未找到可确认的固定抓包后端。这个有限搜索不能证明它们完全没有相关能力，也不能拿 UI/通用终端替代吞吐对照。

| 评价维度 | tcpdump + libpcap | 自建 libpcap worker | 自建 TPACKET / AF_XDP |
| --- | --- | --- | --- |
| 当前可复用成熟能力 | 过滤、写盘、轮转、读取、统计较完整 | 复用采集库，其余控制由 Aurora 实现 | 需要更多内核/接口和格式实现 |
| 实际采集吞吐 | 待同条件实测，已有 mmap 路径 | 待实测；不能仅凭代码少判更快 | 待实测且能力前提更多 |
| Agent 结构化状态 | Aurora 薄适配；固定格式解析失败须明确未知 | 直接定义事件，控制更便利 | 同样需自建协议与生命周期 |
| Android 设备差异 | 依赖实际 Root/SELinux/接口资格 | 同样依赖这些资格 | 额外依赖相关内核/驱动能力 |
| 本轮选择 | **默认采集与离线读取组件** | 只有明确控制需求或实测收益时再提案 | 不进入默认链路 |

“最好”在此是兼顾性能潜力、故障面与维护成本的推荐，并非尚未测量的性能第一。验收前不启用未合格功能；若 tcpdump 的统计、退出或文件预算无法达到合同，先报告证据并修订组件选择，不用字符串解析猜成功。

第一阶段五分钟来自现有命令执行合同，是阶段边界，不是最终产品长时抓包的永久上限；持续任务需另行完成生命周期设计。文件轮转的大小阈值不等于严格总字节上限，必须预留最大记录/文件头开销并验收峰值空间。未获实测前不固定“最佳缓冲大小”，也不把 pcap 收包/过滤/丢包计数混成同一含义。

## 接入边界

- Agent 使用结构化参数选择接口、BPF 过滤、时长、快照长度、缓冲和文件总预算；字面 argv 传递，不拼 shell。接口查询是独立短命令。
- 内核过滤、原生缓冲/批量写盘；UI 不接收每个包。默认不使用逐包刷盘模式。吞吐、延迟、丢包和耗电要测量后给出，不承诺零损耗。
- 第一阶段复用现有命令最大五分钟范围，大小/轮转总预算必须明确；持续异步捕获属于后续运行时设计，不冒充已获批准或已实现。
- PCAP 默认归当前会话，工具二进制为共享读。Root 只对本次有界、明确命名的捕获产物恢复 App 可读属主，不扫描整棵会话树，不修改 Linux guest 属主。删除会话先取消并等待文件关闭。
- 停止后读取所选文件的有界摘要，详情按需读取。统计解析失败、Root 失联、超时或强杀均保留真实中断/不确定状态；不把有 PCAP 文件当作成功证明。抓包不等于解密 TLS，发包/重放单独实现。
- 普通 UID 明确不可用。SELinux/接口能力失败返回真实错误，不调整系统策略或增加 VPN 回退。

## 验证

先做官方来源与固定源码构建、NDK 双 ABI/16KiB/许可、离线 PCAP/过滤错误/预算测试，再补 Root 真机 loopback、自有测试端点流量、停止/强杀/删除、产物归属、丢包统计与受控吞吐功耗对照。用户决定所有真机项最后集中验收；当前不连接或更新手机。

来源：[官网 stable/testing 列表](https://www.tcpdump.org/)、[tcpdump 源码](https://github.com/the-tcpdump-group/tcpdump)、[libpcap 源码](https://github.com/the-tcpdump-group/libpcap)、[1.11.0 testing 的维护者公告](https://seclists.org/tcpdump/2026/q3/18)。官网于本次通过本机 HTTPS 读取；GitHub latest-release 接口为 404，不能据此推断项目无版本。

补充一手来源：[内核 PACKET_MMAP](https://cdn.kernel.org/doc/html/latest/networking/packet_mmap.html)、[内核 AF_XDP](https://docs.kernel.org/networking/af_xdp.html)、[libpcap 1.10.7 Linux 实现](https://github.com/the-tcpdump-group/libpcap/blob/libpcap-1.10.7/pcap-linux.c)、[tcpdump 4.99.7 实现](https://github.com/the-tcpdump-group/tcpdump/blob/tcpdump-4.99.7/tcpdump.c)、[PCAPdroid 固定源码](https://github.com/emanuele-f/PCAPdroid/blob/66f7b3514540c20add8655a74dfee93d00a9563b/app/src/main/jni/pcapd/pcapd.c)、[上游接口关闭阻塞问题 #899](https://github.com/the-tcpdump-group/libpcap/issues/899)、[Termux libpcap 配方](https://github.com/termux/termux-packages/blob/master/packages/libpcap/build.sh)。只学习设计，不复制 PCAPdroid 项目代码或引入其 App。
