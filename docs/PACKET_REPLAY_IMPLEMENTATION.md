# 有界classic PCAP片段回放

2026-10-25。Agent packet_replay_root读取会话私有文件，要求明确sourceSha256；32MiB内快照校验摘要后交普通UID独立native解析器。解析器只读取stdin，封印memfd；libpcap离线解析完成全部选择后才允许Root发送。

firstPacket从0开始，count精确1..64，总帧数据最多16384字节。支持classic PCAP四种字节序/时间精度；只接受完整Ethernet且未标记FCS。拒绝cooked、截断帧、缺帧、超预算和PCAPNG。固定libpcap的PCAPNG读取路径不处理IF_FCSLEN，因此当前不开放该格式回放；packet_inspect仍可读取。

沿用已有Root Binder注入入口、账本和取消协议，Root重解码内部有序批次，不接收文件路径或shell。每帧一次，不按捕获时间戳调度；显式固定速率，最多30秒。protocol2核对选定总字节/帧数、已接受前缀及下一帧短写，首次错误停止，不自动重发。pcap接受不能证明交付，也不重建TCP状态。

普通解析器具有15秒alarm和实际创建父进程死亡SIGKILL；App最终等待真实退出并关闭流。解析失败返回injectionStarted=false。Root请求开始后的未知结果仍保留既有账本屏障。

本地核心选择/参数/变长帧证明回归、Debug/Release/testAPK/lint通过；Android普通UID解析用例只编译。新增Linux13项实验含私有veth真实帧序、四种classic格式、全选择拒绝及父死亡；结果以实际CI为准。尚未操作手机。
