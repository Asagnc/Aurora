# 0.1.40 未签名完整审查包（2026-11-09）

目录app/build/outputs/release-review-61f4f6e-complete，精确绑定61f4f6eae745f89dbb24cbb253617f999d0e51d4，工作树clean。主包包含已人工确认的整页Root引导；生产Frida仍17.24.3，未纳入失败的17.25候选。

9项载荷/重建报告2,683,877,687字节，APK45,191,710字节。10条SHA256SUMS独立流式复核全部一致；清单与校验和见evidence/0.1.40/release-review-61f4f6e-complete-*。自有源码快照重新绑定当前提交，固定SDK45项、代理584项及Frida65份来源材料复用经摘要校验的输入。

首轮打包因误选旧的本地Frida资格report而拒绝（APK worker摘要不匹配），未输出目录；改选已审查cba97ab材料内相同生产worker的资格报告，通过源码校验和离线重链接。guard源码51项和APK内置摘要核对、独立profile离线重建通过，仍不声明两profile逐字节相同。

unsigned-review、signature.verified=false、releaseReady=false。未读取用户正式密钥。Debug主包保留数据更新后6项Root回归通过；不将其当默认Release运行或正式签名资格。

最新UI反馈修复后的审查包：app/build/outputs/release-review-85b790e-complete，精确绑定85b790eaf6be441369993d0c12ee843076b0843f、打包时clean。9项载荷/重建报告2683892639字节，APK45192874字节；10条checksum再次独立流式校验通过，来源快照已重绑。61f4f6e包作为前一版本记录保留。仍未签名、releaseReady=false。

61f4f6e完整CI42565543872五个job全部成功。后续85b790e只改SDK页面反馈，主机190任务及实际页面核对通过；将随本轮推送重新触发完整CI，不把前一提交结果算作新提交CI。

最终复核：d4bbf03完整CI42569418729五job全部成功；85b790e之后没有生产应用源码变化，增加的是独立资格夹具、证据及CI传输准备。85b790e审查包仍精确绑定原提交，不重写其来源身份。主包新boot构建/导出/JNI通过100.062秒；真实484MB SAF全安装/清理另在独立空安装区通过，伴随输入为显式固定离线夹具。


最新：完整未签名审查包release-review-a621f7f-complete精确绑定a621f7f06a455b0a968c0a2cfeae701977ea3618、打包时clean；9项载荷/重建报告2,744,829,128字节，APK45,349,526字节/SHA256 271e0eeb725ca0d14afbb728608710af0fb13771a351d92f6308cc05eda0fc52。10条checksum独立流式复核一致，79份Frida输入离线重链接同c9b22da8；SDK45、代理584项固定材料已重绑来源快照。unsigned-review/releaseReady=false，不宣称正式签名或默认Release真机资格。证据见frida-1726-main-integration.json及release-review-a621f7f-complete-*。


最终核对：a621f7f完整CI42602162251五个job全部成功。后续提交仅增加/更新验收文档与证据，产品字节和完整审查包仍精确对应a621f7f，不重写旧包来源。
