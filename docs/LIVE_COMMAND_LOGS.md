# 事件驱动的命令日志 · 0.1.13

2026-10-21用户明确认可：复用落盘输出，展开时事件监听与增量读取，不定时轮询、不新增常驻进程。实现进一步简化：现有Binder start已有唯一ID，由应用和普通/Root执行器共同用作产物目录名，无需新增逐行或目录回调。

## 选择与边界

| 方案 | 取舍 |
| --- | --- |
| 定时检查文件/数据库 | 不采用。空闲也反复工作，时延取决于轮询频率 |
| ParcelFileDescriptor管道 | 适合后续交互式输入输出；当前要额外处理未打开界面时的持续排空、背压、断连和持久化，不能直接用“管道最快”作结论 |
| Binder逐段传输出 | 不采用。控制面不承担大量输出传输；仍需另存日志并处理回调/队列压力 |
| 现有产物文件+FileObserver | 本轮采用。保留当前执行器输出，观看时监听并增量读取；没有同负载实测性能领先结论 |

## 实现

- CommandToolExecutor生成UUID，工具账本启动后、执行前附加`runs/UUID`。NativeCommandRunner用同一UUID；Root复用已有start ID。Root命令握手/请求协议升为2，拒绝旧版服务，基础身份协议仍为1；没有新增权限、日志RPC或native采集线程。
- SQLite从1/2非破坏迁移至3，新增artifact_directory与presentation。消息、密钥、原生协议历史和完整工具结果保留。presentation是有界有效JSON，避免直接截断大JSON后丢失退出码、错误及保存预算；历史命令在读取最近记录时按需投影，不重写原结果。
- UI生命周期至少STARTED且工具详情展开时收集。FileObserver最多监听会话根、runs及对应UUID目录；父目录监听覆盖“先展开、后创建目录”的窗口，挂监听后再读现有数据，结构变化时重建监听。
- 仅监听修改/创建/移动/删除/写关闭/属性事件，不监听自身读取事件。CONFLATED事件/快照，真实事件后最多100ms合并；空闲不安排循环计时读取。收起、离页、后台或删除会话会取消收集并finally释放监听。
- 单消费者文件游标，只读新增部分，突发过大直接取末尾。每流最多保留64KiB显示字节，UTF-8未完成序列延后显示；结束时明确解码不完整序列。文件变小或inode变化重置；同inode同长度的任意覆盖不属于追加日志契约。
- 日志文本可选择/复制、stdout/stderr切换、默认跟随末尾、拖动后暂停及回到末尾。视图有高度/文本预算，不把日志作为Markdown/脚本执行。完整工具结果仍可展开，产物目录仍可浏览。
- 现有采集器仍每流最多保存前8MiB，其后排空并计数，不会因为UI慢阻塞命令。最终详情显示实际输出量/保存量/超额未保存；“实时日志”不等于无限保存或PTY。
- FileObserver通知只是刷新提示，不能保证每个中间事件都送达；重新展开/最终状态重新读取文件。主动系统冻结、进程死亡和Root撤权不由文件监听解决，不自动重放命令。

## 验证

核心67项通过，其中本轮新增6项日志读取测试、2项大结果投影测试：现有输出与追加、分流、跨写入UTF-8、大突发、截短、未创建目录、不完整终态、带大量引号/括号的长日志及深度预算。

新增5项CommandLogDeviceTest仅编译：真实FileObserver先订阅后建目录、原生命令退出前可见、映射完成/重开、v2迁移和删除取消。既有v1迁移夹具同步重建旧字段，Binder夹具校验固定ID产物路径；都需要新设备运行，不能沿用0.1.10通过记录。ordinary gate升至51。新版Root协议2、界面手势、屏幕生命周期与耗电仍待集中真机验收。PTY、独立持久命令和日志轮转未实现。

## 一手依据

- [Android FileObserver：inotify、持有引用和停止后可能有迟到事件](https://developer.android.com/reference/android/os/FileObserver)
- [ParcelFileDescriptor：普通/可靠管道](https://developer.android.com/reference/android/os/ParcelFileDescriptor)
- [AIDL并发与调用线程](https://developer.android.com/develop/background-work/services/aidl)
- [Binder事务大小异常](https://developer.android.com/reference/android/os/TransactionTooLargeException)

实现没有引入新依赖；日志观察器将初始化、事件循环和finally释放保留在同一作用域，便于审查资源所有权。
