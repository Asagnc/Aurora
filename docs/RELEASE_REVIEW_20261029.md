# 0.1.40 未签名审查材料（2026-10-29）

精确源码提交9ceed074a5fad9fa10f176561eefbecd7f7bad0d；打包时clean。目录app/build/outputs/release-review-9ceed07-complete，保留全部历史包。9项载荷/重建报告合计1,690,890,277字节；APK45,070,914字节。全部10条SHA256SUMS已独立流式复核。仍unsigned-review/signatureVerified=false/releaseReady=false，未读取手机正式密钥。

实际worker SHA256为8776f1969d8ff75e168b4c871101a2d95bd624b6f65b93549fe49d4b5fe11607，包含spawn用户脚本顶层求值确认后才resume的修复。离线Frida对应源码重链接、当前guard源码重建、APK双ABI/16KiB/资产/批准图标审计通过。65份Frida源码、41项SDK源码、584项代理源码/候选依赖超集及自有源码快照均纳入校验。此包不包含临时策略资格程序作为App运行入口，也不宣称完整上游SDK重建/许可闭包或Android运行通过。

主机完整224任务通过；356核心340通过/16跳过、356核心/236 Android编译JUnit均void、API36 Files守卫通过。9ceed07 CI40863121380目前专项Frida30项及网络/guard已通过，整体仍等待；最终状态另归档。实际设备证据与失败边界见DEVICE_ACCEPTANCE_20261027及frida-minimal-policy-device-20261029.json，不把打包检查当正式签名/全部设备资格。

精确清单见evidence/0.1.40/release-review-9ceed07-complete-manifest.json及对应SHA256SUMS。后续仅测试/文档提交不改变此份快照所绑定的提交身份。


9ceed07完整CI40863121380成功，五个job均成功，包含真实Frida/Linux30项（新增延迟payload首次调用和求值异常不resume）。组合超时后续直接观测：App所在/apps/uid_10429父cgroup.freeze=1、events frozen=1，进程自身freeze=0但events frozen=1，设备仍Awake；Root原生worker日志已有native_hook_observed及成功卸载/分离/退休。宿主仅启动Aurora MainActivity恢复前台后，App继续收到结果；不改cgroup/Thanox/系统策略。此受干预批次不作为独立后台或亮屏完整通过证据。FridaManaged亮屏夹具增加显式自身Activity及原flag恢复，继续无干预复测；背景冻结能力仍未通过。
