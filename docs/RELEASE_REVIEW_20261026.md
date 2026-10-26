# 0.1.40 完整未签名审查材料（2026-10-26）

源代码提交：f561e1007557465ad9ad7272123416c0e618ff97。打包时工作树clean。本次材料在该提交产生，后续只补验收记录不会改变已生成材料中的源码身份；精确自有源码快照校验需在该可信提交进行，不能把后续文档提交的HEAD当作同一个快照。

本地目录：app/build/outputs/release-review-f561e10-complete。共9项载荷/重建报告，另有manifest.json及SHA256SUMS；载荷总量1,690,661,029字节。所有SHA256SUMS已用独立流式读取逐一复核。目录不覆盖旧审查包、不安装/签名/上传手机。

| 材料 | 字节 | 对应范围 |
| --- | ---: | --- |
| Aurora.apk | 45,018,519 | 0.1.40/code41、min36/target37、非debuggable、双ABI/ELF/16KiB及资产审计 |
| frida-source-materials.zip | 349,809,636 | 65份固定源码材料、官方重链接输入及自有worker/桥对应 |
| native-corresponding-source.zip | 5,522,944 | 受管guard及Linux原生对应源码 |
| android-host-arm64.tar.gz | 180,242,303 | 已资格的固定ARM64原生SDK包；其余官方JDK/Gradle/SDK仍按固定目录直接下载 |
| android-build-source-materials.zip | 377,568,841 | 41项固定源码输入＋本提交自有源码快照 |
| mitmproxy-linux-aarch64.tar.gz | 98,927,179 | 官方12.3.0 ARM64三成员分发，产品仅导入固定mitmdump |
| network-proxy-source-materials.zip | 633,570,965 | 584项固定输入、候选wheel/Cargo-lock源码超集＋本提交自有源码快照 |

另附frida-relink.json/native-rebuild.json：离线worker重链接和guard源码重建均成功。Frida worker SHA256 396b31f9205598b66409584f7f843ee17633a3f9cf6c7dccfce081b828673dbb；不等同重建完整上游Frida SDK。代理材料不等同所有静态对象可复现重建或总体许可闭包已验证，SDK候选不冒充完整Android补丁NDK等价。

APK SHA256：a9dcb548bdbbca5cfc1cc3170d759ee38f6ff2b7f7d6950a7de861afda8bca41。实际图标引用链已检查，解码像素匹配批准原图；不是手机launcher mask实际视觉验收。完整清单与各项SHA见evidence/0.1.40/release-review-f561e10-complete-manifest.json及对应SHA256SUMS记录。

当前signatureVerified=false、releaseReady=false，APK为unsigned-review；禁止把材料打包/主机CI当成手机Root/ART/性能/真实私仓或正式签名验收。手机长期密钥仍留在公共Downloads，当前未读取，未生成替代正式密钥。

接续集中验收见DEVICE_ACCEPTANCE_FINAL.md；当前仍不操作手机/模拟器，不调用付费模型，不重装IDE。

同一实现提交的完整CI40327134461成功；ARM64 SDK产品专项40327139608成功，8项生产Kotlin测试无跳过，实际Java/Compose/双ABI JNI readonly探针和私有运行库源码审计通过。精确报告与测试XML已归档到evidence/0.1.40/apk-installed-sdk-40327139608*。这些不代替手机执行。
