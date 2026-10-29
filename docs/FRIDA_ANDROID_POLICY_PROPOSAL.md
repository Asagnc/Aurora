# Frida Android 专用 SELinux 适配比较（2026-10-28，已确认先资格再开放）

用户已确认“采用最小适配，先验证再开放”。本授权覆盖下述最小方案；未通过资格不开放，不扩成官方完整宽规则。

## 已观察的失败与只读证据

普通Root/ART自检通过，实际自有包spawn和Aurora普通App进程attach均在attach阶段返回Agent connection closed unexpectedly，用户脚本未加载。后者目标存活时保持unknown，目标退出后通过原精确恢复路径确认；未以worker退出冒充脚本清理。

本机FolkPatch的实际Root安全上下文名为magisk，不等同更换Root管理器。只读资格程序经验证PID、精确App进程名和UID，仅查询当前SELinux决策：Enforcing=1；Root创建的memfd标记为tmpfs；Root读写/执行/map均允许，Aurora的untrusted_app对该memfd的open/read/write/execute/map/getattr均EACCES。App自身execmem及使用Root fd已有权限。查询命名frida_memfd上下文返回EINVAL，不把此结果当任何成功权限证明。未修改策略、标签或全局开关，诊断程序和临时memfd已清理。

固定Frida17.24.3源码57161b0ddd2311adf18e84f4680f9cb935ce8376的src/linux/linjector.vala在AgentResource.get_memfd创建memfd后，尝试fsetfilecon为frida_memfd；该调用没有检查返回值。官方server/server-glue.c另调用lib/selinux/patch.c装载专用类型和规则。当前嵌入式worker没有调用服务器的全局策略修改函数。

这条权限/标签链与实际失败一致；尚未抓取失败现场的agent FD标签，最终因果仍须用修复前后同一自有目标对照验证，不能仅凭静态源码宣布修好。

## 比较

| 方案 | 范围与代价 | 结论 |
| --- | --- | --- |
| 保持当前策略 | 无全局变化；Root自检可用，但普通App注入仍未通过 | 如不批准策略变化，应明确阻断未资格的App注入，不能冒充完整Frida完成 |
| 直接调用官方完整patch_policy | 已有固定SDK路线；包含domain到domain通用execmem、zygote/系统服务等多组规则 | 本机已有execmem/fd权限，不直接照搬宽规则 |
| Frida专用类型和必要规则的最小适配 | 保留Enforcing；只为专用对象及经核实的执行域补必要权限；需真实策略装载和退出矩阵资格 | 推荐，先资格再接入；修改本次启动的全局策略，必须用户确认 |
| Gadget/重打包目标或改Root管理器 | 改变目标/后端及一般Root attach范围，额外安装/签名流程 | 不作为本问题的例行替代 |

## 推荐范围及边界

- 复用固定Frida SDK的已携带SELinux/sepol实现和固定上游代码，先验证可链接、读取、编辑、序列化及当前Android16内核接受性；不悄悄下载另一套policy工具或安装Root模块。若需要新增依赖，再比较确认。
- 仅增Frida专用类型/必要属性和对应访问规则。以只读决策与实际标签资格为依据，已有权限不重复开放；不允许普通App执行通用tmpfs，不新增全域execmem，不切permissive，不改普通App数据目录标签。
- 修改的是内核当前启动的全局策略，不宣称按单个PID隔离；SELinux规则以域/类型为粒度。专用对象仍须既有Root所有权、只传本任务FD、票据/身份/脚本摘要约束。不能把专用类型访问规则当成其他App的数据访问授权。
- 不安装持久模块或启动项。记录当前boot、Root身份、输入/输出策略摘要、实际添加规则和资格结果；发布入口仅接受明确启用及当前身份/工具资格，不由Agent猜测授权。
- 不自动恢复旧整份策略快照，以免覆盖其他Root组件的后续变更。读写期间检测变化尽力拒绝竞争；内核接口没有跨Root组件的事务/CAS，不承诺消除此竞态。失败/部分成功记录真实当前状态，不能写成回滚完成。
- 不改既有Frida spawn两阶段、账本、取消/退休/unknown、版本租约和删除屏障；不复用普通命令执行器冒充Root注入。

## 开放前验收

1. 策略编辑为固定受审源码；先离线验证只添加批准类型/属性/规则，既有allow规则/策略数据不被删除或放宽为通用权限。
2. 当前Android16 Kernel接受后，Enforcing仍1；专用memfd重标记成功，目标域仅对专用类型的必要权限通过，普通tmpfs执行仍拒绝。Root/普通UID基础行为回归。
3. 同一自有普通App原生attach、ART脚本、真实自有包spawn早期hook均通过，脚本卸载/分离/worker及任务域退出、精确spawn目标恢复都有实际证据；失败和unknown不重放。
4. Root/App/worker死亡、取消、同boot/新boot资格失效、用户未启用/拒绝路径与活动删除屏障通过后再开放。正式密钥、付费模型及第三方目标不参与资格。

依据：[固定Linux injector](https://github.com/frida/frida-core/blob/17.24.3/src/linux/linjector.vala)、[固定server初始化](https://github.com/frida/frida-core/blob/17.24.3/server/server-glue.c)、[固定SELinux适配](https://github.com/frida/frida-core/blob/17.24.3/lib/selinux/patch.c)、[Android16平台SELinux接口](https://android.googlesource.com/platform/external/selinux/+/android-16.0.0_r4/libselinux/include/selinux/selinux.h)。这些来源支持接口与上游行为，不等同手机策略修改已获授权或资格已通过。


后续实测：spawn增加等待固定bridge既有user_script_evaluated的预算内屏障，失败/取消会在resume之前退出并沿用精确自有目标回收；确认只作顺序信号，不是业务成功/清理证明。修复后自有包早期Java方法hook实际1项通过33.759秒，包含真实hook事件及原卸载/分离/资源退休/会话删除。新增Linux宿主“延迟bridge仍捕获main首次调用”和“求值异常不resume”两项回归，待Linux CI；Android新增resume前异常用例正在设备复测。默认核心/Debug/Release/R8/AndroidTest/lint224任务通过，356核心/236 Android编译JUnit均void，API36守卫通过；不能据此宣称所有Frida故障矩阵或自动策略入口完成。资格摘要见evidence/0.1.40/frida-minimal-policy-device-20261029.json。


2026-10-29最新：所有Frida验收夹具各自持有临时MainActivity/KEEP_SCREEN_ON并恢复原flag、Root在finally断连后，无宿主中途唤醒干预的5项亮屏批次全部通过111.298秒：早期ART spawn hook、resume前脚本异常精确回收、Root native/selected attach/ART/status、普通App native attach、观察到实际注入事件后取消并确认卸载/分离/worker及任务域退出，原App PID/start ticks存活。原失败/受干预批次保留，不把结果扩成后台/Thanox或App/worker/Root死亡完整矩阵。另一个冻结失败自有App owner经精确恢复/删除1项通过1.285秒，两个早期spawn捕获前unknown仍保留。当前产品worker仍绑定9ceed07及其成功完整CI/审查包；最新改动仅测试夹具/验收记录。新增显式64段/512MiB配置预算设备夹具正在运行，尚未计通过。


Root服务实际死亡专项1项通过15.566秒：已观察真实注入后，宿主使用本次测试明确提供的Root PID/startTicks、Root UID校验及pidfd_send_signal终止该确切进程，先验证错误ticks拒绝且不发信号；不按猜测PID发kill。新Root身份不同、native/ART资格均归零，旧票据只恢复不重放，实际卸载/分离/worker与任务域退出、原App PID/start ticks存活及自有会话删除通过。scripts/owned-root-signal-fixture.c仅显式测试临时二进制，不打包。首轮夹具误要求必须抛异常，生产实际也可保留结构化不确定结果；改为拒绝正常worker成功并独立要求恢复证明后通过，失败自有owner经既有恢复/删除2秒通过。App进程死亡专项继续；此项不冒充Root管理器撤权或boot变化资格。


App进程死亡两阶段专项通过：prepare仅在自有fixture已捕获/注入/持久resume确认后自杀，预期Process crashed，不计JUnit通过；重启verify 1项通过3.061秒。启动未自动重连/重放，原票据保留，Root旧任务卸载/分离/worker与域退出证明成立，已resume的目标PID/startTicks仍存活，脚本事件仅1次，pending归零，自有会话/checkpoint清理。首次实际发现Root日志已发布但O_EXCL导致恢复不返回artifact；现仅对完整同字节的既有副本复用，检查regular/nlink1/0600/允许UID与GID、流式逐字节和前后stat/目录条目身份，同步后交付；拒绝不完整/篡改/链接/宽权限，不删改现存不匹配文件，诊断失败不编造清理事实。新建副本也补handoff目录条目身份检查。6项真机文件检查通过1.934秒，含实际Root创建硬链接拒绝；cache特殊GID/普通UID禁硬链的初始夹具失败已明确修正，未放松产品检查。

本地主机完整核心/Debug/Release/R8/AndroidTest/lint224任务通过，356核心340通过/16跳过，356核心/247 Android编译JUnit均void，Android36 API守卫357源通过。一次构建重试过早与未退出旧构建重叠导致lint临时输出冲突，已串行重跑通过；不将失败记录算通过。0c057b4完整CI40866353580成功；新的日志恢复产品修复还需新CI和新精确源码审查包。


2026-10-30 worker实际SIGKILL专项1项通过17.373秒。仅自有fixture持久resume确认且已真实注入后，从绑定票据/boot的Root READY记录取worker PID/startTicks；宿主pidfd校验并拒绝错误ticks再发SIGKILL。原目标同PID/ticks仍存活时，恢复保持unknown、pending1，删除被阻断，资源退出不冒充脚本卸载；只在显式停止该固定fixture后，以真实targetExit证明清账，cleanup仍null，pending归零并删除本次自有会话。没有强制清未知记录或重放，两个历史捕获前unknown保持。复用测试调用辅助类，不改产品协议/持久格式。

更新完整审查包release-review-16abd22-complete绑定16abd2246873591b7df9f49ed4c88e39cecad82c且打包时clean；9项载荷/重建报告1,690,959,613字节，APK45,074,402字节，10条校验独立复核，worker离线重链接及guard重建通过，unsigned-review/releaseReady=false。参见RELEASE_REVIEW_20261030。当前主线继续接续策略显式启用及身份/boot门控，不把本次临时全局策略资格当已有产品入口。
