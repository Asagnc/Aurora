# Root 抓包最终集中验收清单

仅在最终集中验收阶段执行。当前手机保持0.1.32，不为编译/主机CI连接或更新手机。真实供应商与付费模型连接本轮不测。

## 安装与范围

- 确认现有会话没有未确认的Linux任务，保留应用数据覆盖更新Aurora及测试APK；不卸载Aurora、不清数据。沿用FolkPatch应用内已有授权，需要用户处理时立即联系，不绕过管理器。
- 使用普通开发构建先验功能；吞吐/CPU/功耗需要注明具体原生编译优化和负载，不能拿Debug数据声称Release性能领先。
- Root抓包只产生本次测试会话的PCAP和状态/日志文件。loopback只发送本地自造UDP，不向外部目标发包，不抓第三方真实业务或供应商凭证，不改全局策略。

## 自动测试组

项目根目录构建：

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
```

安装时只用保留数据的覆盖更新。设备序列号须从最终阶段的实际设备列表选择，不照抄历史序列号。

普通组重点：PacketNativePackageTest（离线三项 + 普通UID拒绝实时采集/固定产物cwd）、CommandLogDeviceTest（日志事件/数据库恢复/六类工具产物映射）、RootCommandProtocolTest（普通跨进程端点拒绝Root采集）。

Root专项：

```text
adb -s DEVICE shell am instrument -w -e auroraRootTest true -e class io.github.asagnc.aurora.root.RootPacketCaptureDeviceTest io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner
```

该组7项：24字节仅头预算并普通UID离线读取；无流量时长截止；准备事件后发送loopback受控UDP并达到包数预算；非法过滤不成功且保留其他文件；取消后原生监督停止证明且不发布成功；Root断连停止、重连后只运行新任务；删除会话先取消工具和日志观察再删除全部本次产物。

## 仍需补充的实际证据

- Root被拒绝/撤销、SELinux拒绝返回明确错误，没有VPN/PRoot替代或全局策略修改。
- selected PCAP、capture-status.json及日志App可读；失败/未知状态不因有文件转成成功。外层硬截止与用户取消保留不同状态。
- 熄屏/亮屏对照、有界CPU租约释放，Root断开/应用停止/会话删除后没有该测试的采集进程或FD残留；Thanox主动冻结单独记录，不归咎于未持锁。
- 固定同一流量、过滤、时长/预算，对比libpcap worker与同源tcpdump：吞吐、保存量/内核丢包、CPU时间、RSS、产物大小、启动/取消延迟。缓冲/交付初值4MiB/100ms只能按实测优化。
- any与指定接口、实际接口停用/切换、空间不足、最终flush/fsync失败，必须保留真实结果。SIGKILL无收尾保证。
- 只清理测试会话与测试APK，保留Aurora和用户全部原有数据；测试APK是Android自动测试入口，不属于最终用户功能。

主机测试已经覆盖私有netns十项及I/O边界，但不能代替这些Android证据。结果写回PROGRESS/IMPLEMENTATION及evidence，不自动重放不确定采集。
