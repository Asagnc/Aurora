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

本地 Debug/Release/testAPK/lint、ELF/16KiB与源码包检查通过；新增五项主机运行待 CI，不提前记通过。两项 Android 未运行，手机未更新。

下一步接结构化 Agent 抓包参数、实际 Root 任务/预算、停止/异常落盘、会话产物归属和按需详情；之后做受控流量与吞吐/丢包/功耗对照。长时抓包仍需完整运行时设计；第一阶段沿用现有五分钟命令上限。无 VPN、PRoot 或逐包 JNI/模型传输。
