# Root 连续分段抓包实现记录

2026-10-26。采用已批准 NETWORK_UID_ROTATION_PROPOSAL：复用独立guard、现有Root journal/cgroup和活动意图；不是另一套daemon。产品入口尚未开放，Android运行/性能仍待最后集中真机验收。

## 已实现与实际证据

- CaptureRotationOptions 上限30分钟/512MiB/64段，每段最多64MiB，整包包含24字节全局头和16字节记录头。单包超过段预算明确停止，不能无限轮转空文件。
- AURC固定小端212字节配置头＋接口/UTF8过滤器，最多4372字节。绑定run/owner/Root PID与startTicks/boot/epoch；使用既有封印memfd、认证AFD1单次交付，不复用Frida脚本文本解释。
- 同一个libpcap句柄持续捕获；非promiscuous、不改路由，BOOTTIME timerfd和受管取消优先于下一包。配置认证后在Root私有任务目录先留存capture.config，不覆盖既有记录；0400、fsync、关闭、renameat2 NOREPLACE与目录fsync后才开捕获。
- capture-NNN.partial写满整包后flush、校验尺寸/inode/链接数、fsync并关闭写句柄；0400、原子不覆盖改名为pcap，重新打开同一inode流式SHA256。最多1024字节的绑定封口记录通过同样不覆盖/持久化步骤登记为seal；登记前的文件不算可发布片段。
- 文件/清单预算、包计数和内核原始累计计数分开。内核统计不可用或倒退/溢出时保持未知，不编造段丢包差值。
- 独立capture protocol3票据、AEX2退出报告不含Frida attach/unload/detach/manager事实；guard持久化绑定票据的managed.capture并按类型解码。capture使用BOOTTIME观察，launch admission和恢复/kill/reap使用新的MONOTONIC短预算，不混用时钟原点。Frida协议、默认序列化字节与250秒上限保持。
- Root读取须先确认worker/domain真实退出和类型记录，再读唯一有界配置/连续seal前缀；拒绝间隙、其他Root代/票据/序号、inode替换、链接、非0400文件。PCAP流式检查格式、snaplen、link type、全部整包边界、计数/截断和完整摘要。
- RootCapturePublication只复制已验证的原始Root私有片段到原会话segments目录。新目录交付前为Root私有；每份副本同步后才交付UID，全部文件和清单完成后交付目录。多文件复制不是文件系统事务；中断的不同摘要/不完整既有文件保留并报告，不能当完整副本或覆盖。精确完整副本可幂等复核，原始Root封口段不被移动/删除。
- 双ABI载荷libaurora_packet_rotate.so和独立APK摘要已打包，实际ELF16KiB、无WX/可执行栈、系统解释器/立即绑定及依赖审计通过。Root调度仍拒绝capture，防止尚未完成产品整合时开放入口。

CI40224855665实际通过8组封口/故障、5组实时私有loopback握手/连续序列/取消测试；不是模拟libpcap。首次guard联测40225277685发现launcher只接受旧通道，已修复；40225641400完整通过，包括9组报告流、11组pidfd观察、25组journal、12组启动和24组cgroup guard恢复。配置留存的新分支仍需后续CI覆盖。

本地306核心294通过/12跳过；Debug、R8 Release、AndroidTest APK、lint和双ABI实际载荷审计通过。Root Android代码仅编译，未连接手机，不能据此声称captureReady或后台吞吐/丢包已合格。

## 仍需接续

- 接Root Binder捕获调度/服务代资格/恢复结果与产物字段，验证配置留存和发布故障，不把资源清理与产物交付混为一谈。
- 现有schema8表CHECK只允许frida，capture接入须在同一活动表做保留全部旧票据的迁移；不能另建任务DB或直接绕过约束。产物发布失败要保留可显式恢复的记录。
- Agent status/probe/start、准备产物回调、账本投影/删除屏障、Root私有封口文件回收仍未接；旧任务恢复不能重放抓包。
- 最终手机验收覆盖Root/App死亡、旧boot/当前boot恢复、逐阶段封口/磁盘失败、发布中断/篡改、取消/删除竞争、熄屏/后台与实际内核丢包；当前不执行手机操作。
