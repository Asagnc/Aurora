# 可审查发布材料包

`scripts/package-release.py` 使用已有 Python、NDK 与 Android SDK。不安装、不签名、不上传，不接触设备；输出目录必须不存在。实际输入先复制到本次私有临时目录，再验证该份字节，失败清理本次暂存，保留原输入和已有输出。目录复制期间不是原子可见；只有命令成功及 SHA256SUMS 完整验证后才交付。

打包检查 APK 内的双 ABI 原生依赖/ELF、ARM64 Frida 摘要、固定桥与许可；Frida 材料必须匹配 APK worker 及当前可信构建参考；两份源码包分别在现有 NDK 上离线重链接/重建。材料包包括 APK、两份源码 ZIP、两份重建报告、manifest.json 与 SHA256SUMS。

```text
python scripts/package-linux-sources.py
python scripts/package-release.py --unsigned-review --apk app/build/outputs/apk/release/app-release-unsigned.apk --frida-sources app/build/outputs/frida-source-materials-final.zip --native-sources app/build/outputs/linux-sources/aurora-linux-corresponding-source.zip --frida-reference app/build/frida-worker/report.json --guard-reference app/build/outputs/managed-qualification/report.json --ndk PATH_TO_EXISTING_NDK30 --build-tools PATH_TO_EXISTING_BUILD_TOOLS36 --output NEW_OUTPUT_DIRECTORY
```

未签名审查模式不能同时指定证书或宣称签名通过。正式签名后去掉 `--unsigned-review`，传入 `--apksigner` 与明确选定的 `--certificate-sha256`，验证 APK 签名且只接受该单一证书。私钥/密码不写入此工具、不生成替代正式密钥；保留现有安装数据需要先核实更新签名兼容，不能用卸载掩盖签名不符。

材料打包不会授予设备验收资格，manifest 的 releaseReady 保持 false。最终设备和签名发布是后续独立步骤。源码 ZIP 包含完整官方 devkit 静态重链接输入，但没有从源码重建完整 Frida SDK 的证据；原生 guard 重建报告也不等于所有上游组件从源码重建。不得扩大这些证明范围。

官方依据：[apksigner 签名与验证](https://developer.android.com/tools/apksigner)。zipalign 应在签名前执行，签名后修改 APK 会使签名失效；此工具完整复制输入 APK，不重压缩 APK 条目。

新增aapt2包名/版本/minSdk36/target37与非debuggable检查，zipalign -c -P 16 4验证，以及工作树dirty标记。签名器可用SDK的lib/apksigner.jar配合--java，密码仍不进入此工具。用户已说明长期签名密钥位于手机公共下载目录，明确留到集中验收时处理；当前不读取手机、不生成替代密钥。


APK共享构建工具可作为配套审查材料：先运行package-apk-materials.py，传入--resources（固定资源源码目录）、--compiler（LLVM源码目录）、--runtime（SDK私有运行库inputs目录）、--host（CMake/Ninja源码目录）和新的--output ZIP。它逐项验证41份源码输入，附精确git archive HEAD快照，并再次流式校验生成ZIP；不执行上游源码。快照属于打包时可信checkout，改变HEAD后需重新打包再验证。

package-release.py同时传入--android-build-bundle与--android-build-sources，才会复制原生SDK tar.gz及SDK源码ZIP到材料目录并纳入manifest/SHA256SUMS。缺一项拒绝；原生SDK字节、APK assets/apk-build/native-bundle.json、可信源码目录和自有源码快照须全部对应。官方SDK/JDK/Gradle分发仍从已固定官方下载入口获取，不夹入自制SDK原生包。上述验证不等于手机buildReady或完整Android补丁NDK等价。


代理离线配套使用package-proxy-materials.py：--upstream为固定mitmproxy提交源码目录，--packages为Python包downloads，--rust为crate downloads，--python为CPython inputs，--gcc为Debian inputs，--crypto为OpenSSL inputs，--output为不存在的ZIP。它校验584项固定输入（含上游自身、候选wheel和Cargo-lock源码超集），附当前git archive HEAD；逐项有界流式摘要检查，不执行归档代码。原始源码和自有快照内的许可/补充notice一并保留，仍不宣称完整静态链接可复现或总体licenseClosureVerified。

package-release.py成对接收--proxy-distribution与--proxy-sources：验证官方独立分发精确字节、编译安装器pin、APK实际八份profiles、固定源码清单和当前源码快照。代理分发和源码ZIP纳入manifest/SHA256SUMS。配套校验不执行下载的程序，不把主机资格写成手机proxyReady。增加4组惰性fixture覆盖完整/篡改/缺失/多余/重复/错源码快照/错APK profile/错分发拒绝。发布stage函数沿用单次临时目录中按顺序复制—审计—重建—发布的责任，新增可选材料仍在同一生命周期内；超过普通函数行数目标的例外保留以避免拆散失败清理范围。
