# 0.1.40 更新未签名审查材料（2026-10-28）

精确源码提交：c26b70199e867673d803c3d5cb3875ec54c4015b。打包时工作树clean，目录app/build/outputs/release-review-c26b701-complete；保留历史f561e10材料不覆盖。后续验收记录提交不改变这份源码快照身份。

9项载荷/重建报告合计1,690,823,325字节，另附manifest.json和SHA256SUMS；独立流式复核全部10条校验记录成功。APK45,070,798字节，0.1.40/code41、min36/target37、双ABI及16KiB/资产/图标审计通过。仍为unsigned-review、signatureVerified=false、releaseReady=false。

材料含65份Frida固定源码及上游静态重链接输入、当前自有worker/guard原生对应源码、固定180MiB ARM64 SDK、41项SDK源码输入、官方mitmproxy ARM64分发和584项代理源码输入/候选依赖超集。SDK/代理自有源码快照绑定本提交，profiles对应手机修正后的实际project挂载。

当前worker SHA256：fa273f9fe666a8e3a19c49f12ec85f83885c1fafafdd5cc03bfadb81c13d6e9f。离线worker重链接和当前guard对应源码重建成功；不是完整上游Frida SDK或Android补丁NDK重建证明，也不宣称总体许可闭包完成。

精确材料与摘要见evidence/0.1.40/release-review-c26b701-complete-manifest.json及对应SHA256SUMS。主机完整224任务通过，356核心340通过/16跳过，356核心/233 Android编译JUnit签名全部void。该提交的CI另行记录。

真机已有普通/浏览器、静态逆向、代理、预热缓存的标准SDK构建和实际JNI通过证据，详见DEVICE_ACCEPTANCE_20261027及android-build-device-20261028.json；Frida spawn、冷缓存首次预算、完整30分钟/熄屏及剩余故障矩阵仍未全部通过。审查打包不授予设备或正式签名资格，未读取手机Downloads正式密钥，未调用付费模型。
