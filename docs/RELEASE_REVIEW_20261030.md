# 0.1.40 未签名审查材料（2026-10-30）

精确源码16abd2246873591b7df9f49ed4c88e39cecad82c；打包时clean。目录app/build/outputs/release-review-16abd22-complete，历史包均保留。9项载荷/重建报告1,690,959,613字节，APK45,074,402字节；10条SHA256SUMS已独立流式复核。仍unsigned-review/signatureVerified=false/releaseReady=false。

包含App进程死亡后Frida日志重复恢复的修复：复用完整同内容既有副本，拒绝截断/篡改/链接/宽权限，Root暂存完整副本只有重新验字节/身份后才交付App。真机6项文件检查与App死亡verify 1项通过；prepare是预期崩溃，不计测试通过。Root/worker死亡、取消和普通App/ART证据分别记录，不能把打包审计当设备资格。

worker SHA256仍8776f1969d8ff75e168b4c871101a2d95bd624b6f65b93549fe49d4b5fe11607。65份Frida/41项SDK/584项代理来源材料和对应自有git源码快照、固定分发、当前原生对应源码、worker离线重链接与guard重建均纳入审查，16KiB/ABI/资产/图标检查通过。不是完整上游SDK重建或总体许可闭包证明。精确清单见evidence/0.1.40/release-review-16abd22-complete-manifest.json及对应SHA256SUMS。

源码16abd22完整CI40906945348另行跟踪；前置0c057b4完整CI40866353580已成功。尚未使用正式签名密钥或付费模型；原生策略启用入口、撤权/boot失效、后台冻结与其他剩余故障矩阵仍有待完成内容。
