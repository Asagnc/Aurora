# Root精确socket快照

2026-10-25，沿用用户已批准的NETLINK_SOCK_DIAG方案。socket_owner_root已接现有Root命令监督/Binder/任务账本/会话产物；不新建daemon、数据库或后台轮询。仅Root就绪时向Agent暴露。

数字IPv4/IPv6、TCP/UDP、完整本地/远端地址端口必填；不查DNS、不猜包名、不接受zone。原生worker最多3秒、16MiB netlink输入、100000记录、32精确匹配；App监督5秒和8192字节完整预览。所有匹配均保留，不选第一条。超限、截断、序号/发送者错误、DUMP_INTR、坏属性或未完整DONE均整体不可用，不从部分扫描推唯一UID。

记录查询BOOTTIME区间、结束Unix时间、boot UUID、net/user namespace设备inode及overflowUid；前后namespace一致才发布。扫描不是原子快照，socket可在期间或之后变化。UID仅在记录的user namespace解释；UID0也不等于某个应用身份。返回每项UID/inode/cookie/state/interfaceIndex；多项multiple、无项unknown，未连接UDP/TIME_WAIT/inode缺失/cookie不可用/溢出UID保留原始信息而unknown。

普通UDP/TCP命令不改路由/接口/权限。失败保留原命令未知状态；每次独立查询，不自动重试。此接口不能证明历史PCAP逐包归属，后续eBPF仍须独立资格。

本地核心291项279通过/12跳过（含3项socket参数/证明测试），Debug/Release/testAPK/lint通过。新增7项Linux实验：双真实UID connected UDP、关闭后unknown、同tuple跨UID所有候选、unconnected UDP、IPv6、TCP双向不同socket、普通UID拒绝和损坏netlink解析；结果追踪实际CI。新增2项Android仅编译：普通UID拒绝、Root Binder查询自有loopback TCP的实际App UID与账本。当前未操作手机，SELinux/Root管理器与设备内核资格不作通过声明。

依据：Linux官方include/uapi/linux/inet_diag.h、sock_diag.h及net/ipv4/inet_diag.c；详细比较见NETWORK_UID_ROTATION_PROPOSAL.md。
