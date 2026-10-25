# Root 连续分段抓包实现记录

2026-10-26。采用已批准 NETWORK_UID_ROTATION_PROPOSAL：复用独立guard、现有Root journal/cgroup和活动意图；不是另一套daemon。Agent与Binder入口已接，实际当前Root服务的一秒loopback自检通过后才允许start；Android运行/性能仍待最后集中真机验收。

## 已实现与实际证据

- CaptureRotationOptions 上限30分钟/512MiB/64段，每段最多64MiB，整包包含24字节全局头和16字节记录头。单包超过段预算明确停止，不能无限轮转空文件。
- AURC固定小端212字节配置头＋接口/UTF8过滤器，最多4372字节。绑定run/owner/Root PID与startTicks/boot/epoch；使用既有封印memfd、认证AFD1单次交付，不复用Frida脚本文本解释。
- 同一个libpcap句柄持续捕获；非promiscuous、不改路由，BOOTTIME timerfd和受管取消优先于下一包。配置认证后在Root私有任务目录先留存capture.config，不覆盖既有记录；0400、fsync、关闭、renameat2 NOREPLACE与目录fsync后才开捕获。
- capture-NNN.partial写满整包后flush、校验尺寸/inode/链接数、fsync并关闭写句柄；0400、原子不覆盖改名为pcap，重新打开同一inode流式SHA256。最多1024字节的绑定封口记录通过同样不覆盖/持久化步骤登记为seal；登记前的文件不算可发布片段。
- 文件/清单预算、包计数和内核原始累计计数分开。内核统计不可用或倒退/溢出时保持未知，不编造段丢包差值。
- 独立capture protocol3票据、AEX2退出报告不含Frida attach/unload/detach/manager事实；guard持久化绑定票据的managed.capture并按类型解码。capture使用BOOTTIME观察，launch admission和恢复/kill/reap使用新的MONOTONIC短预算，不混用时钟原点。Frida协议、默认序列化字节与250秒上限保持。
- Root读取须先确认worker/domain真实退出和类型记录，再读唯一有界配置/连续seal前缀；拒绝间隙、其他Root代/票据/序号、inode替换、链接、非0400文件。PCAP流式检查格式、snaplen、link type、全部整包边界、计数/截断和完整摘要。
- RootCapturePublication只复制已验证的原始Root私有片段到原会话segments目录。新目录交付前为Root私有；每份副本同步后才交付UID，全部文件和清单完成后交付目录。多文件复制不是文件系统事务；中断的不同摘要/不完整既有文件保留并报告，不能当完整副本或覆盖。精确完整副本可幂等复核，所有会话副本和清单持久后，按已确认退出与Root私有配置回收Root原始载荷；保留小型封口/退役记录。恢复时若原载荷已回收，须对会话副本重验PCAP结构/计数/摘要，不能只看文件存在。清单device/inode描述原始Root封口文件，不是后续会话副本的物理inode。
- 双ABI载荷libaurora_packet_rotate.so和独立APK摘要已打包，实际ELF16KiB、无WX/可执行栈、系统解释器/立即绑定及依赖审计通过。Root调度严格验证封印配置/票据/包摘要，普通start须当前服务代probe通过；probe只接受固定lo/less 1/1秒配置，只有封口、副本发布和真实退出全部通过才置资格。

CI40224855665实际通过8组封口/故障、5组实时私有loopback握手/连续序列/取消测试；不是模拟libpcap。首次guard联测40225277685发现launcher只接受旧通道，已修复；40225641400完整通过，包括9组报告流、11组pidfd观察、25组journal、12组启动和24组cgroup guard恢复。配置留存的新分支CI40226368338也已整体通过。

本地307核心295通过/12跳过；Debug、R8 Release、AndroidTest APK、lint和双ABI实际载荷审计通过。Root Android代码仅编译，未连接手机，不能据此声称captureReady或后台吞吐/丢包已合格。

## 产品整合与恢复

- schema9仅迁移同一root_managed_runs表的kind约束；旧Frida票据文本和工具归属逐字保留，索引/外键和活动删除屏障保持。SQLite实际执行原始DDL的保留/边界/回滚3组通过，Android8→9与旧版本迁移用例已编译。
- packet_capture_rotate_root提供status/probe/start，Root快照公布不可变包摘要和当前服务资格；准备产物回调和既有SQLite账本先于副作用。普通命令315秒唤醒租约不变，只有typed capture使用30分钟加清理保留的有界租约，取消/退出释放。
- Root发布失败即使资源已退出也保留同一活动意图，root_managed_tasks recover只重验/重试发布，不重新抓包。显式删除会话传入typed discardCapture；仍须真实worker/domain退出，之后只清理本任务Root私有载荷并退役。未知占用或清理失败继续阻断删除。
- Root载荷回收可跨中断继续；原载荷不存在时只接受与Root封口记录完全相符的完整会话副本。App可编辑自己的产物；副本被改动会明确失败，不能冒充原始捕获。Root小型不可变退役/封口记录保留，延续已有防重放设计。

## 仍需验收

最新产品整合CI待跟踪。Android迁移、发布失败保留和真实Root loopback Agent/账本用例已编译，未安装或执行；最后覆盖Root/App死亡、旧boot/当前boot恢复、逐阶段封口/磁盘失败、发布中断/篡改、取消/删除竞争、熄屏/后台与实际内核丢包。当前不执行手机操作，不能把Linux主机通过当成Android资格。
