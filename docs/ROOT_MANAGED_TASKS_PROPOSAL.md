# Frida接入Root/Agent：受管原生任务方案（待确认，2026-10-23）

Frida原生附加/脚本/桥/取消已在主机分阶段验证，尚未放入APK或注册Agent工具。接入前需要确定下面新增持久化与文件描述符通道；不能直接把已编译worker当普通Root shell命令执行就宣称完整恢复。

## 推荐组合

1. 原生SQLite WAL新增`root_managed_runs`活动/未确认任务表，schema7→8。首批kind=frida；与tool_runs显示历史关联，但退出证明独立。继续保留现有linux_runs/linux_leases，Frida不需要安装Ubuntu，也不伪造Linux版本租约。没有新数据库框架。
2. 复用已批准的task cgroup v2/pidfd/原生监督算法，增加Android原生任务入口；只收纳Aurora本任务worker及其继承的helper，不移动已有目标App、不改系统调度/控制器、不要求chroot或PID命名空间、不新增常驻守护进程。设备资格失败则此受管后端不可用。
3. 选定脚本和打包Java桥用只读封印memfd快照传递，Binder只承载元数据/PFD，不传大段base64或反复落盘。Root到原生worker新增一次性AF_UNIX/SCM_RIGHTS控制握手，使用peer credentials、任务nonce及捕获的进程身份校验后给出FD；传完即关。只使用任务选定数据，不开放网页调用Root/文件方法。
4. 专用退出通道留出事件驱动的清理窗口。Root总预算沿用最多270秒，先为结束/清理预留20秒，观察参数最多250秒；完成即结束，不固定等满。普通命令的现有250ms TERM→KILL策略保持原义，不能套到Frida的独立10秒unload/detach期限上。

## 比较

| 方案 | 收益 | 取舍/结论 |
| --- | --- | --- |
| 只用现有tool_runs | 无新表，能显示调用历史 | 当前start只存tool/callId/状态，没有不可变Root身份/目标/派发票据；App崩溃后无法据此确认注入任务，且不涵盖独立原生入口。不单独采用 |
| 扩充tool_runs为所有原生任务账本 | 可少一张表 | UI历史与执行事实生命周期耦合、历史级联删除与未确认任务屏障容易冲突，迁移面更广。首批不选 |
| 把Frida塞进linux_runs | 已有退出证明/租约 | 该表外键依赖真实CHROOT Linux代和linux_leases；会把Android本机注入错误绑定到Linux安装，增加无关依赖。不选 |
| 独立受管Root活动表+原生cgroup入口 | 小范围增量，复用已验证监督，和历史/普通命令分开；未来相同状态机可复用 | 多一次小型派发事务与迁移/删除协调，需要测试。推荐 |
| 只靠JSON文件 | 不改App数据库 | Root文件仍需保留，但单靠文件难与App会话删除事务/待处理索引协调。不代替App意图账本 |

| 脚本通道 | 收益 | 取舍/结论 |
| --- | --- | --- |
| Binder内嵌脚本/base64 | 调用直接 | 接近IPC预算，多次复制/编码；1MiB脚本不应作为普通Binder消息。不选 |
| 每任务磁盘临时副本 | 最直观、易诊断 | 增加写盘/清理，与更新时版本一致性需要额外协调；不作为产品默认 |
| 可修改普通文件FD | 避免路径再次解析 | 捕获后仍可能被修改或缩短；不是固定字节快照 |
| 封印memfd+受管FD传输 | 数据只读、关闭即由内核释放，无脚本暂存残留；不需要Node/Python或额外App | 每活跃任务有界内存与一次握手；必须实测Android封印/FD/SELinux能力。推荐 |

这些是按正确性、依赖和I/O路径的判断，不是已测手机速度/RSS最优结论。不会因为原型89MB就宣传完整安装/RSS数据。

## 活动账本与恢复事实

App同一写事务内确认owner仍可用并登记不可变派发意图，再调用Root。建议列为id主键、owner、kind、ticket JSON及可选tool_run_id，按owner/待处理建立索引；只存活动/未确认记录，确认结束后移除，历史仍进tool_runs。票据带runId、Root PID/开始时间/bootId/epoch、App构建/worker版本、目标身份/操作、选定脚本版本/摘要描述、期限。实际快照摘要和阶段作为Root执行事实关联，不覆盖原始意图来假装重试。

Root继续保存自己创建的任务域/票据/阶段文件，先发布受管意图和域，再放行子进程；查询/取消与迟到start必须以同一任务锁和退役标记协调，不能把一次“没找到正在运行”当成将来不会开始。持久记录按owner恢复，不能停止另一会话。App退出/Root断连仅标未确认，不自动重放脚本、spawn或网络副作用。

正常完成必须分别确认脚本卸载/分离、manager关闭、worker退出、任务域回收；证明匹配原runId和Root身份，事务移除对应待处理项。恢复/回收仅检查有索引的未完成记录及符合原GC条件的本任务目录，不扫描全部历史/文件、不轮询保活。

### 不能混淆的目标进程

已有目标App位于自己的系统cgroup，不能为了好回收就迁移到Aurora任务组。`cgroup.kill`/空组证明只覆盖worker/helpers，不能证明另一个进程里的注入脚本已经卸载，也不能回滚脚本的文件/内存/网络副作用。

缺失最终卸载证明而原目标仍活着时，保留injection outcome unknown；不因worker已死就清掉该事实或再注入。目标原身份已退出/更换启动或设备重启，是不同的可核实事实。若需要结束仍活着的原目标，必须走明确的停止目标操作，不能后台恢复时自动杀原有App。spawn得到的暂停进程和已有App分开记录；若在取得/记下PID前发生崩溃，结果仍可能未知，不能靠包名猜一个用户进程去杀。

会话删除先停止/恢复并核实相关任务；未确认状态沿用原待清理/退役语义保留，不把带未确认执行事实的会话记录级联删光。向用户显示具体待恢复状态，不把这种异常状态称作“已清理干净”。如何由用户授权结束目标或后续确认原目标退出，属于恢复动作，不是每次正常hook加审批弹窗。

## FD与快照

数据源按现有会话/共享空间与Root批量文件规则选定，不接受任意宿主路径来绕过归属。源版本在捕获时核实；memfd填充后加WRITE/GROW/SHRINK/SEAL封印并验证，worker只接受规定数量/类型/大小的FD，读取后关闭，其他FD不向helper/目标泄漏。票据/Root记录绑定实际捕获字节摘要。失败/取消关闭全部快照FD，不留下下载半成品。

Android公开Os.memfd_create在本机SDK API历史中since30，最小36无需旧版分支；公开OsConstants并未暴露所有封印常量，实现要用NDK/UAPI核实的命名常量及设备测试，不通过反射隐藏API。公开LocalSocket支持传FD，SCM_RIGHTS需要分帧、控制消息截断/额外FD/凭证/提前断连测试；普通ProcessBuilder不能假设会保留任意FD。现开发CLI的普通文件路径仍只作为夹具，不直接当成最终产品通道。

## 资格与实施次序

1. schema8新建/1..7迁移、事务失败、未确认任务保留、精确证明、会话删除/退役与查询索引；不迁移/破坏原Linux租约。
2. memfd封印/不可修改/关闭回收、SCM_RIGHTS分帧与恶意/重复/额外FD、Root凭证/nonce/迟到启动；不把seal失败默默降级为可写路径。
3. Native task cgroup+父死/取消/截止；保留原目标与已知helper回收证据，专门验证worker崩溃后目标注入状态，不能只验证空cgroup。
4. 关联Agent/UI日志与最终结果小通道，不从可能截断的stdout头部猜清理成功。按需读大日志，不逐事件Binder回调。
5. 原生/Java自有目标、并发/断连/熄屏/删除/故障最终手机集中验证；完成许可证闭包/对应源码与APK实际体积检查后才打包并显示ready。

## 一手依据与已检查实现

- https://www.sqlite.org/lang_transaction.html — 派发意图事务、WAL并发写的实际约束。
- https://docs.kernel.org/admin-guide/cgroup-v2.html — 进程归属、继承与组回收的范围，不代表目标App会自动加入。
- https://man7.org/linux/man-pages/man2/memfd_create.2.html — 匿名FD/封印/关闭释放。
- https://developer.android.com/reference/android/net/LocalSocket — 公共FD传递接口。
- 本机platform android-37.0 api-versions.xml和javap：memfd_create since30、Os.fcntlInt、LocalSocket接口；仍不代替Android16设备资格。
- 仓库ToolRunStore.start、LinuxRunRegistry.begin、AuroraDatabase(schema7)、RootCommandSession、capture.c：分别核实历史无原生票据、Linux外键依赖、既有取消回收与250ms普通命令窗口。

本方案待用户确认；本轮只修复已批准功能的登记/日志问题，没有创建schema8表或启用新的Root执行后端。
