# Root 实时抓包接入（0.1.36，2026-10-23）

用户已批准 ROOT_CAPTURE_RUNTIME_PROPOSAL 的实时 worker / 离线 tcpdump 分工。本轮实现 Root 派发、Agent 工具和选定产物发布；Android 真机和性能资格未验收，不标成已通过设备验证。

## 已实现

- Agent 的 `packet_capture_root` 只在已连接 Root 且服务声明独立协议1时注册；旧服务不获得该能力。请求由核心合同与 Root 接收端共同严格检查，不接受自选输出路径、任意 argv 或 shell 插值。默认 any / 15秒 / 16MiB / 100000包；最大270秒 / 512MiB / 10000000包，最先满足的预算终止。文件预算包含24字节全局头和每包16字节记录头，不截半包。
- RootService 复用现有 RootCommandSession 的两个任务上限、Binder死亡取消、原生监督与有界唤醒锁。新 worker 单进程不创建后台守护进程。硬截止比采集时长多10秒用于收尾，总范围仍低于五分钟。
- 普通 NativeCommandRunner 的工作目录行为不变；抓包专用实例以本次运行目录为 cwd，并以保持打开的目录描述符固定子进程工作位置。没有复制环境或扫描会话。
- worker 使用已锁定静态 libpcap，非阻塞事件FD、poll/signalfd/timerfd，CLOCK_BOOTTIME 截止包含挂起。每批最多64包，控制事件优先；不支持事件FD/需要额外 select 超时的后端明确拒绝。4MiB缓冲、100ms批量交付为待测初值，不宣称性能最优。
- worker 最后 flush/ferror/fsync/fstat；输出 fileComplete、保存包数/字节、文件设备号/inode与 received/dropped。未知内核统计为null。进程日志完整不等于PCAP完整，核心完成校验同时要求监督器成功、日志未截断、已知预算原因和文件完成。
- 只有进程已退出并通过合同后，Root 才重新以不跟随链接的描述符逐级固定 runs/id，打开唯一 capture.pcap；核实普通文件、单硬链接、Root原属主、设备/inode、大小和预算，只恢复该文件的App属主。保存 capture-status.json 并同步文件/目录，再返回 capture.path。离线 packet_inspect 始终普通UID。
- 日志、工具账本、中文任务标题和现有详情复用。采集未知死亡明确 capture_outcome_uncertain；超时、取消、写盘及发布错误不转成成功或自动重放。未正常发布的文件可保留在该会话目录，不能根据存在性当成完整PCAP。

## 验证

- 先前76eb16a CI39833896207完整成功：原有离线十项 + worker整包预算/I/O项，共11项主机用例；其中四个预算边界和/dev/full缓冲失败实际运行。证据见 evidence/0.1.36/packet-worker-host-eleven-passed.txt。
- 本轮本地核心179项：172通过、7项POSIX在Windows跳过；已提供签名Ubuntu归档实际运行镜像检查。新增6项覆盖参数、字面过滤、UTF8预算、完成证明、取消/超时与未知统计。
- Android Debug/Release R8/测试APK/lint全部编译通过；两种ABI的Debug和Release ELF依赖、16KiB页及对应源码包检查通过。Debug APK SHA256：607fdab8a2da9345db6b5cd144339ee843dc1175948eba88dd508e04bba9aa54。
- 本轮新增私有网络命名空间内9项受控测试：空闲截止、24字节预算、下一包放不下、包数预算、空闲取消、SIGKILL、非法过滤、接口down、输出符号链接。通过CI实际执行后再补结果，不把Windows编译代替Linux运行。
- Android新增普通UID拒绝和产物cwd检查、Root专项4项（仅头文件发布与离线读、空闲截止、loopback受控流量、非法过滤），加上扩展后的账本映射与普通Binder拒绝测试，目前仅编译。仍须最终集中真机检查Root断连/取消、属主、会话删除、熄屏、吞吐/CPU/丢包/内存与功耗。手机未连接、更新或操作，仍为此前0.1.32。

## 后续边界

未增加VPN、UID归属、TLS解密、发包引擎、长时轮转或按应用流量隔离。上述能力不能由“已接libpcap”推断已完成。浏览器/代理与模型连接也不因本轮变更而获得未测保证。
