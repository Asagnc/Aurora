# 当前接续清单（2026-10-31，0.1.40 真机验收中）

这里只列当前实现、实际证据和未完成工作。历史失败、修复及各提交的构建记录保留在PROGRESS、IMPLEMENTATION、DEVICE_ACCEPTANCE_20261027和docs/evidence；不得把历史缺失再次当作待建设。

## 工作约定

- 在现有main连续实现、验证并推送，例行修复直接做。新底层/依赖/持久化选型先比较确认，已批准方案不重复问。
- 用户已授权真机测试，并已自行重启、撤销/恢复FolkPatch授权、解除Aurora的Thanox冻结/后台限制。不自行再次重启或改Root管理器/Thanox配置。
- 当前顺序：先测试既有能力；用户随后细说UI问题；UI处理后由用户配置密钥再测试付费模型；最后拓展。不调用真实付费模型，不重装Android Studio/WSL。
- 保留原安装和数据，0.1.40沿用既有调试签名。正式密钥在手机公共Downloads，最终签名阶段再由用户配置；不读密钥、不生成替代正式签名。
- 只有当前Root身份/服务代/环境代/工具版本实际资格通过才开放执行。编译、CI、安装、旧连接的资格都不替代手机当前资格。
- 未知占用、退出或清理失败保留恢复记录并阻断删除；不强删unknown，不重放未知副作用，不以资源退出冒充脚本卸载。

## 当前状态与缺口

| 范围 | 已实现并取得的证据 | 接续工作 |
| --- | --- | --- |
| Frida Root Binder/活动账本/Agent | 独立活动意图、guard/cgroup/journal、protocol2 spawn两阶段目标记录、FD/sealed memfd、恢复/删除屏障与Agent接入已完成。Root native/ART、普通App attach、自有包早期ART hook、resume前异常、取消、Root/worker/App死亡恢复均实际通过。 | 早前受限配置下出现App原生SIGSEGV/PC0x320，根因仍未确认。解除限制后六项组合已连续三个独立批次通过（133.086/133.790/134.105秒），不能当因果修复；继续复现/排查与剩余压力矩阵。 |
| Frida最小策略入口 | 纯差异编辑、单次load/读回、Root私有boot基线、Binder身份/protocol、当前身份资格清零、设置页均已接。用户手动新boot后从零捕获基线并启用通过；撤权时普通功能保活、恢复/死亡后资格不继承通过；实际UI禁用入口和只读状态通过。 | releaseQualified=false继续保持；不因当前配置测试通过就开放正式策略启用。无permissive、通用tmpfs执行、全域execmem、持久模块或旧整份策略回写。 |
| 网络捕获/UID/轮转 | libpcap worker、离线PCAP、原始发包/精确回放、HTTP重放；NETLINK当前真实socket UID快照；protocol3/schema9轮转。30分钟低流量1800.018秒/16段/dropped0，以及64段/512MiB预算封口/发布/清账均通过。 | 高负载实测kernel dropped6678，明确非零丢包。熄屏/后台长时与故障完整矩阵仍需补齐；不冒充逐包历史UID或线速资格。 |
| TLS/HTTP代理、静态逆向 | 固定mitmdump12.3.0 ARM64及JADX/Apktool/JRE共享只读版本/租约/Agent已接；实际HTTP/HTTPS/WS/未信任CA、CA导出/私有缓存清理及自有DEX/APK分析通过。最新Root工具回归合计14项51.824秒通过。 | 继续补全任务死亡/取消/恢复矩阵，不自动安装系统CA或改路由。 |
| APK构建 | JDK25/Gradle9.8.2/AGP9.4.1/ARM64原生host、Java/Compose/双ABI JNI/DEX/16KiB/只读布局/Agent构建导出已接，固定180MiB归档导入已主机验证；实际手机标准构建/APK导出/普通UID JNI通过。 | 公共只读依赖基线已接入；新会话标准完整探针98.354秒通过，未配置宿主relay。历史无基线冷缓存超时保留。大归档可视SAF、故障矩阵、正式签名仍待，见下节。 |
| Skills | 私有GitHub API/仓库限定PAT、现有KeyStore/schema10归属恢复、用户钉公钥/BC1.86轻量Ed25519、离线验签、来源/撤销UI与Agent profile已接。实际黄金向量/凭据库/迁移/恢复与信任回归通过；opt-in R8验签smoke通过。 | 自有私仓/撤销UI与默认正式APK、正式签名的最终验收；R8专用测试保留规则不等于正式包逐字节资格。 |
| 浏览器/系统快照/基础运行 | 普通/浏览器172项及启动清理、真实renderer死亡、同源/跨源边界、普通UID系统快照2项、Linux/PTY/Agent恢复已有设备证据。最新浏览器异步/下载恢复、Skills信任和Root副本合计21项7.125秒通过。 | 浏览器/SAF/提供器完整矩阵按DEVICE_ACCEPTANCE逐项核对；不以已覆盖子集代替完整矩阵。当前解除限制配置下后台/约15秒熄屏Root tick两项35.423秒通过，不代表全部后台管理策略兼容。 |

实现入口：ROOT_MANAGED_BINDER、ROOT_MANAGED_TASKS_IMPLEMENTATION、FRIDA_SPAWN_PROPOSAL、FRIDA_AGENT_ACCEPTANCE、FRIDA_POLICY_IMPLEMENTATION、CAPTURE_ROTATION_IMPLEMENTATION、NETWORK_PROXY_IMPLEMENTATION、STATIC_REVERSE_IMPLEMENTATION、APK_BUILD_PROPOSAL、SKILLS_IMPLEMENTATION、SKILLS_TRUST_FORMAT、BROWSER_TRANSFER_IMPLEMENTATION、BROWSER_CHOOSER_IMPLEMENTATION、BROWSER_ASYNC_IMPLEMENTATION、SYSTEM_SNAPSHOT。

## 正在推进：公共只读APK依赖基线

- 用户2026-10-30已确认推荐路线：固定公共依赖只读基线＋会话私有可写缓存；复用Gradle官方GRADLE_RO_DEP_CACHE、既有版本/只读挂载/租约。不共享凭据、配置、daemon、transforms或工程输出，不加数据库/daemon。
- 首轮隔离ARM64 CI41069037872：6项边界测试通过，生产Kotlin工具准备后独立完整探针96.528秒成功；快照检查错误要求40位SHA1目录，而固定Gradle会去掉前导零，导致封口失败。未发布缓存归档，整体不算通过。
- 已核对固定Gradle9.8.2实现并修正比较，新增实物回归；Windows7项中5通过/2项Unix专用跳过，Unix实际证据由CI提供。
- 修正版还验证真实依赖缺项：离线拒绝、联网后只写该会话私有缓存、只读基线不变。随后两份全新自有会话，在原270秒下离线完整构建/APK导出/普通UID JNI加载，且基线不可写、摘要不变。Android夹具已编译/lint108任务通过，251个Android测试签名均void；尚未执行。
- 666cc72修正版隔离ARM64资格CI41071359765成功：8项边界通过、两份全新私有缓存离线完整探针73.256/76.219秒，缺项离线拒绝及联网私有回写64.692秒，基线不可写且摘要不变。966文件展开341,863,935字节、压缩303,975,306字节，650项公开依赖文件；手机2026-10-31两份全新自有会话的完整离线构建/导出/普通UID JNI已通过（1项215.886秒，包含两次构建，各自270秒预算）；两份空写缓存、只读写入拒绝、前后摘要及退出/自有会话清理均通过，不直接授予生产runtimeReady。666cc72完整CI41071281794五个job成功。先前失败/取消保持原记录。
- 只有主机与手机资格、缺项回写/取消/不完整发布/活动租约删除屏障及来源/许可材料完成后，才接正式共享版本。现在沿用同一android-build-v1共享版本，把固定基线封入原生审查输入，安装前核对manifest/逐文件摘要与完整清单，启动器默认GRADLE_RO_DEP_CACHE固定只读路径；新组合包484,215,713字节重复生成SHA一致。手机正式安装/标准探针/导出/普通UID JNI整项已通过225.475秒（含安装），原270秒构建预算保持；自有会话清理、Root断连及临时TLS relay移除完成。runtimeReady只由当前身份实际标准探针授予；95e7b7d完整CI41172360549五job与生产ARM64安装专项41172360761均成功；共享版本故障/完整发布材料校验继续。详见APK_DEPENDENCY_CACHE_PROPOSAL及evidence/0.1.40/apk-dependency-cache-qualification-20261030.json。

## 发布边界

- 已完成完整CI41057445450，5个job成功，HEAD356f157仅文档变化，产品代码与5edfdc3一致。5edfdc3的41057202983被后续推送取消，不计成功。
- 当前完整审查包app/build/outputs/release-review-5edfdc3-complete精确绑定5edfdc319093fa0a38c1c93a47ddc8f14c140c1d，打包时clean；9项载荷/重建报告1,691,202,794字节，APK45,188,830字节，10条checksum独立流式复核，worker离线重链接/guard重建通过。
- worker为0ca9bd596c919af1c6483f7747591a67c3de74c0af33413119bde2f58aa69dd8；65份Frida、41项SDK、584项代理材料。unsigned-review/releaseReady=false；后续正式工具/产品字节变化须重新生成精确包，不把测试夹具提交混称旧包来源。
- 正式签名、用户UI问题处理、真实供应商路由/usage、付费模型调用仍未完成；付费测试等用户配置密钥后进行。缓存新依赖材料也必须追踪来源/许可，不能以CI缓存包代替完整发布材料。


2026-10-31材料收尾：公共基线186个二进制坐标，185份对应源归档（含2份官方固定版本tag归档）、6个无class/dex/native的占位坐标，公开POM许可证声明186项均有记录。787个元数据/通知/源文件封口包204,253,459字节；来源缺项与声明缺项均0，但不宣称总体许可闭包或二进制逐字节重建。SDK审查材料增加固定公共材料、原生base/公共cache/成功资格JSON共4项重建输入，总SDK输入45项；原41项及代理584项按现有摘要复用，正在绑定当前自有Git快照重打完整包。

## 2026-10-31最新接续补充（优先于上文旧状态）

- 用户明确：先完成功能与验收，再处理UI；UI阶段先复查既有Eta比较，不要求用户现在重列问题。历史实拍与批准概念图见UI_AUDIT_2026-10-16、UI_AUDIT_2026-10-21、COMPOSER_B_2026-10-18。历史截图不足以评价当前0.1.40整体，届时重新实拍。
- cba97ab完整CI41173766390五job成功。新的完整审查包app/build/outputs/release-review-cba97ab-complete精确绑定cba97ab62ffb274f50ce5af8fc5fbd8375334292，打包时clean；9项载荷/重建报告2,683,811,595字节，APK45,192,270字节，10条checksum已独立复核。SDK材料45项、代理584项、Frida源码65份；保留unsigned-review/releaseReady=false。guard独立重建与APK字节不同，分别记录来源对应，不宣称逐字节重建。
- SDK生产只读基线：标准探针98.354秒；租约/取消/未派发意图删除屏障3项7.895秒。先完整资格再精确pidfd终止Root的恢复用例1项102.468秒通过，旧资格清零、不重放、租约释放。Linux PTY Root死亡1项3.798秒、Root元数据4项16.668秒通过；错误startTicks均拒绝信号。
- 最新Root/Linux/PTY/离线Agent/网络/SDK矩阵为28通过、1跳过（57.565秒），不能把JUnit的29 tests算作29通过。可选PID namespace探针因当前可见/proc/self/ns/pid缺失跳过；普通PROOT正向因普通UID硬链接不可用跳过，拒绝该输入且保留已有Root版本的负向用例已通过。历史跨版本方法覆盖统计不代表当前APK全部通过。
- 用户已批准Frida17.25.0独立资格；候选构建/ELF与普通Shell版本执行通过，未接生产、未Root注入，不能宣称修复0x320或无需策略。下一步固定源码/运行闭包审查与自有夹具资格，见FRIDA_1725_MAPPER_PROPOSAL。

## 本轮先验收的最新结果

2026-10-31当前Debug回归：成功批次229次执行通过、去重227项；两个跳过执行中的renderer死亡已独立补过，仅可选PID namespace仍缺资格。Root取消夹具首轮目录准备竞争已修正，相关批次28通过/1跳过；SAF包名推断与实际ColorOS选择器不同，按实际包配置后生产取消/导入/清理1项通过。保留两次失败，不改全局默认选择器或生产协议。现有Frida六项133.318秒再通过，仍不声称修好历史0x320或开放入口。见DEVICE_ACCEPTANCE_20261031及精简JSON。当前用户要求人工操作能加速时优先明确请求，不反复自行摸索手机选文件/授权步骤。
