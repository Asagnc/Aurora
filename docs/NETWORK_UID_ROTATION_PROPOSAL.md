# UID观测与长时轮转：已批准方案

2026-10-25用户确认：采用精确socket快照，逐包关联后续独立资格；轮转采用推荐方案，验证后开放。

2026-10-25。当前HTTP单次请求和libpcap原始帧已有产品入口；它们不证明UID归属或长时轮转完成。
此前NETWORK_ADVANCED_PROPOSAL明确保留这两项的选型，下面分别列出可核验边界。

## UID观测

| 方案 | 证据能力与成本 | 建议 |
| --- | --- | --- |
| 包名/端口或瞬时/proc扫描 | 复用端口、多个UID、短连接和时间差会造成歧义；不能凭一次命中写唯一UID | 不采用猜测归属 |
| Android getConnectionOwnerUid | 官方公开契约限制当前活动VPN；不能为了UID引入已禁止的VPN后端，也不能假定Root必然绕过系统权限 | 不作为通用依赖 |
| Root NETLINK_SOCK_DIAG / inet_diag | 直接取得当前socket的完整地址/端口、协议、UID、inode、cookie和状态；无第三方库或驻留探针。能证明查询时socket信息，不能倒推历史PCAP每包UID | 推荐首阶段，准确标注快照范围 |
| 新eBPF逐包观测 | 可在有真实socket上下文时读取UID/cookie，但需要验证Android具体内核/BTF/helper/SELinux、挂载位置及与libpcap帧的严格关联；入站/无socket包仍可能未知 | 若要求逐包关联，先单独资格，不能拿快照替代它 |

推荐提供Root `socket_owner` 快照工具，明确IPv4/IPv6、TCP/UDP、完整五元组及实际观察时间、boot和网络/用户namespace身份。
返回所有精确匹配socket的cookie/inode/UID，而不是选第一个。多匹配、inode缺失、TIME_WAIT、未连接UDP与权限不足明确为多候选/unknown/unavailable；不从UID猜包名，不把当前快照当历史数据包身份。
原始内核值与可归属状态分开，UID0或溢出值不自动当“Root应用”。限定结果数、响应字节、截止与取消；不改系统网络设置，不持久后台轮询。

采用本推荐意味着“当前socket精确快照”先交付；若产品目标要求PCAP逐包UID，需要选择并资格eBPF方案，不能声称此快照已覆盖该目标。

验收：自有双UID进程/loopback TCP、connected与unconnected UDP、IPv6、复用端口多匹配、关闭后查询、时间/namespace变化、权限撤销和损坏netlink响应；匹配不到时不得返回伪造唯一UID。

## 长时连续抓包与轮转

实际代码目前普通NativeCommandRunner上限300秒，PacketCaptureRequest上限270秒；受管票据虽然已有kind字段，但validate只接受Frida，observeMs上限250秒。
因此不能只放大seconds或反复启动现有捕获命令就宣称无缝长时抓包。

| 方案 | 取舍 | 建议 |
| --- | --- | --- |
| 多次270秒命令串联 | 有捕获空隙和多次启动，跨崩溃所有权难以统一 | 不当连续轮转 |
| 新常驻daemon/第二套任务DB | 重复现有受管资源、删除屏障和恢复逻辑 | 不采用 |
| 复用Root受管引擎，扩展capture任务类型与版本化分段清单 | 延续现有schema8活动意图、封印配置、Root journal/cgroup/pidfd/截止与精确退役，增加捕获专属证明 | 推荐 |

推荐新增capture种类和版本化配置/Root私有段清单，旧Frida protocol1/2票据字节与摘要不变。
捕获任务没有注入目标，退出证明只认可本worker/helpers实际退出、任务域移除和文件状态；不伪造scriptUnloaded/sessionDetached等Frida目标清理事实，也不改变Frida250秒上限或普通命令300秒上限。

首版最大30分钟、512MiB总存储预算、最多64段；段预算包含PCAP头和记录开销，总预算另保留有界清单空间。
同一pcap句柄连续捕获，整包边界换段；不重复开接口、不自动改路由。累计内核丢包计数与段内保存计数分开，未知/溢出不乱做差。
每段完成写入/flush/fsync后才登记sealed事实，绑定runId/Root身份/序号/类型/设备inode/字节数/摘要；只有匹配且不可再写的段才发布给原会话。
取消/崩溃后只恢复已登记且已封口的段，当前partial不能伪装完成；未知活跃任务继续阻断删除/版本回收，不因“暂时没看到进程”清账，不自动重新开始捕获。

验收：无间隙自有序列号流、整包边界/精确预算、单个大包超段预算、慢盘/ENOSPC、封口前后逐阶段崩溃、Root/App死亡、同/旧boot恢复、取消/删除竞争、段被替换/链接和未知任务保留；最后集中手机压力与后台验收。

依据：[内核sock_diag ABI手册](https://kernel.googlesource.com/pub/scm/docs/man-pages/man-pages/+/refs/tags/man-pages-6.13/man/man7/sock_diag.7)、[Linux socket cookie检查源码](https://github.com/torvalds/linux/blob/master/net/core/sock_diag.c)、[Android getConnectionOwnerUid契约](https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress))。
现有实现：core/RootManagedRun.kt、runtime/NativeCommandRunner.kt、ROOT_MANAGED_TASKS_PROPOSAL.md与ROOT_MANAGED_TASKS_IMPLEMENTATION.md。这里只批准候选设计，不能据此显示设备ready或把主机结果当Android结果。
