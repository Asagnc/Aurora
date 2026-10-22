# 原生抓包组件构建（0.1.35，2026-10-23）

用户已确认组合推荐：libpcap 负责底层过滤/采集，tcpdump 负责写盘/离线读取，Aurora 负责 Agent/任务/产物适配。当前先接源码构建，不将包装成功冒充 Root 实际抓包完成。

## 固定来源

| 组件 | 官方归档 SHA256 |
| --- | --- |
| libpcap 1.10.7 | 68fa62cffb974f4275641ce14c2e2d75739251f30e00e6a0900903b247d76a03 |
| tcpdump 4.99.7 | 39675dfa06fb0e99be8da5d4a1784acbaab58ae0870231d956ff7870cb99eec1 |

官网仍标 libpcap 1.11.0 为 testing，本轮使用其 stable 1.10.7。适配器 packet/Capture.cmake 锁定官方 HTTPS 归档与摘要；原始依赖缓存不修改。BSD 许可与源码头部告知随 APK assets/licenses/packet 打包；对应源码 ZIP 包含原始树和完整适配器。

## 实现

- libpcap 静态链接到独立 tcpdump PIE，双 ABI arm64-v8a/x86_64、Android API36+、16KiB。最终文件 libaurora_tcpdump.so 由已签名 APK nativeLibraryDir 分发，不下载可写目录可执行程序。
- ExternalProject 隔离上游配置与 Aurora 主 CMake。libpcap 使用 Linux packet backend，启用 IPv6；未增加蓝牙、USBmon、D-Bus、RDMA、远程 rpcap 或额外驱动依赖。tcpdump 未接入可选 OpenSSL/libsmi/libcap-ng/Capsicum；这不阻止原始包保存，但不宣称已支持这些可选解码/解密功能。
- tcpdump 的派生 FindPCAP 只接受本次安装的目标 ABI 静态库；不执行宿主 pkg-config/pcap-config，不误链宿主库。
- Windows 生成器检查按 CMAKE_HOST_WIN32 选择 NUL；win_flex 的固定临时名由每 ABI/构建类型私有 TEMP/TMP 隔离。已发现并修复首次并行检查冲突。宿主 flex2.6.4/bison3.8.2（Windows 官方 winflexbison2.5.25）只参与构建，不进入 APK。
- 主机离线五项：准确版本、匹配保留原包字节、不匹配仅文件头、非法过滤失败、截断 PCAP 失败。Android 普通 UID 两项：启动版本及同一真实 PCAP 过滤/字节检查。后者仅编译，等最后真机集中验收。

## 重建

完整工程使用 Gradle；Windows 需将 flex/bison 或 win_flex/win_bison 加入 PATH。源码包可分别构建独立 Linux 和 packet 目录：

```sh
cmake -S app/src/main/cpp/packet -B build-packet \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build-packet --target aurora_tcpdump
```

离线复建可传 FETCHCONTENT_SOURCE_DIR_AURORA_PCAP_SOURCE 与 FETCHCONTENT_SOURCE_DIR_AURORA_TCPDUMP_SOURCE，指向对应源码 ZIP 的原始树。NDK30.0.16248370/CMake4.1.2；Release 与 Debug 保持同一来源。

## 资格与下一步

### 写盘边界适配

源码检查显示常规退出路径和部分轮转关闭没有显式核实最后的缓冲写入结果。增加只作用于派生源码的 PatchTcpdump.cmake / DumpWrite.h：普通文件在退出和轮转时检查 flush/ferror并fsync，再关闭；轮转同步当前dumper，关闭前清空信号处理器使用的指针；回调保留缓冲写盘，仅检查已有stdio错误，不逐包fsync。非普通输出跳过fsync。所有替换严格核对出现次数，原始上游缓存不改。

主机资格先编译未修改上游并记录其/dev/full退出码，再只重编派生主文件，验证写满不能返回成功、/dev/null不因fsync失败、两段轮转保留20000个完整原包。与已有七项合计十项；493cc86的CI39831312910已完整成功：未修改上游/dev/full退出码0，派生版写满返回失败，非普通输出和轮转通过，共10项主机用例。普通退出检查不覆盖SIGKILL；强杀/异常仍不得根据文件存在认定成功。最终Root任务发布还需父目录与属主/状态协调。

已接 Agent `packet_inspect`：普通 UID 读取会话内 PCAP/PCAPNG，字面 BPF 通过 `--` 与程序选项隔离；默认100、最多5000个匹配包，最多512MiB输入/30秒处理，关闭名称/服务解析。计数限制匹配包而非扫描字节；预览及完整日志仍服从现有命令产物预算。路径拒绝越界、符号链接和非普通文件，保留实际解码错误/超时，不承诺全部PCAPNG组合都可读。复用应用级原生命令限流、任务宿主与日志映射/简洁详情，不新增守护进程或逐包跨层回调。它仅做离线解析，不代替尚未完成的Root实时抓包。

Android普通UID检查现为3项：准确版本、真实PCAP过滤及Agent字面过滤/原包检查、越界在派发前拒绝；CommandLogDeviceTest扩展第五种工具的持久产物映射。均仅编译待集中真机。主机已有五项步骤成功，另补Agent风格单个BPF参数和`--version`不能作为过滤器变成选项两项，待新CI。

本地 Debug/Release/testAPK/lint、ELF/16KiB与源码包检查通过；源码组件提交e04bbba的CI39828476867完整成功，主机离线五项通过；后续Agent工具的两项新增主机用例待新CI。当前三项Android未运行，手机未更新。

下一步接结构化 Agent 实时抓包参数、实际 Root 任务/预算、停止/异常落盘、会话捕获产物归属和按需详情；之后做受控流量与吞吐/丢包/功耗对照。离线解码保持普通UID独立进程，Root采集时只走过滤/写盘链路。长时抓包仍需完整运行时设计；第一阶段沿用现有五分钟命令上限。无 VPN、PRoot 或逐包 JNI/模型传输。

实时控制的新worker分工待确认，见ROOT_CAPTURE_RUNTIME_PROPOSAL.md；当前不改变已实现组件或宣称实时采集完成。
