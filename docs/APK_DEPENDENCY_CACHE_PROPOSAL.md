# APK 公共依赖复用比较（2026-10-30，已确认，先资格后接入）

新会话完整 Java/Compose/双 ABI JNI 构建在270秒预算内超时；同一自有会话的诊断继续下载Release lint依赖，也超时。保留部分缓存后，未关闭lint、未放宽预算的标准探针、APK导出和普通UID JNI加载1项通过94.172秒。不是冷缓存通过。日志明确到达DEX与lint任务；不能归因于先前Thanox冻结。

目前 GRADLE_USER_HOME=/mnt/.gradle-cache，/mnt是会话自己的project挂载；删除会话也删除该缓存。重复新会话会再次承担公共依赖解析和下载。这是现有功能的启动成本问题，不能以预热结果冒充新会话性能。

| 路线 | 隔离、代价及兼容性 | 选择 |
| --- | --- | --- |
| 维持会话独立缓存 | 无新增共享状态；重复下载，首次可能超过现有预算 | 可保留现状但不能声称冷启动已通过 |
| 共用可写GRADLE_USER_HOME | 复用较多缓存，但包含锁、配置、凭据与构建状态；改变会话归属边界 | 不推荐 |
| 固定版本的公共只读依赖基线＋会话自己的可写缓存 | 复用官方Gradle机制，保留当前私有写入与归属；需要新持久缓存布局、验证和体积测量 | 推荐先资格，再接入 |
| 临时拉长构建预算或关闭lint | 掩盖下载/检查成本，不能解决会话间复用 | 不作为修复 |

Gradle官方提供GRADLE_RO_DEP_CACHE，标为incubating；只读缓存无锁，缺项写入原会话缓存，读取期间不能更新基线。缓存复制不包含锁与gc.properties，生产和使用必须匹配Gradle缓存格式。仓库身份和校验仍须保留，不能只凭相同GAV混用来源。

推荐范围：只处理固定探针所需、来自Google Maven/Maven Central/Gradle Plugin Portal的公开依赖；保留Gradle9.8.2、AGP9.4.1和现有工具版本。复用已有共享版本发布、租约、Root只读挂载和恢复流程，不加数据库、daemon、密码库或远程缓存服务。私仓、凭据、代理配置、daemon、transforms与工程输出继续留在会话，不汇入公共基线。

基线必须由独立自有准备区生成，记录仓库、坐标、元数据/文件SHA256与工具版本，完整封口后发布新版本；不直接共享正在使用的会话缓存，不从任意用户工程回收缓存，不修改活版本。默认不改用户工程仓库或关闭TLS校验。源材料/许可记录随目录追踪；本地准备缓存不冒充已完成上游可重建闭包。

实际体积尚未测得，不虚报节省空间；会增加一份共享公共基线，可减少新会话的重复下载，但不会消除私有变换和构建CPU成本。官方功能仍在孵化，必须实际验证固定组合，不因文档存在就授予手机资格。

开放标准：两份全新自有会话使用独立可写目录，在原270秒预算下完成完整探针和JNI加载；固定基线离线命中与缺项回写分别验证；基线挂载写入失败，跨会话写入/凭据不传播；取消/断电式准备中断不发布半版本，活动租约阻断删除；缓存摘要/来源变化拒绝；记录下载字节、磁盘与耗时。若未达到，则保持原行为且报告失败，不自动扩大权限或延长预算。

依据：[Gradle依赖缓存](https://docs.gradle.org/current/userguide/dependency_caching.html)、[依赖校验](https://docs.gradle.org/current/userguide/dependency_verification.html)、[Android官方仓库](https://developer.android.com/build/remote-repositories)。临时验收CONNECT夹具另已补入官方maven.google.com精确域名；此前拒绝该域名不影响94.172秒的通过事实，也不能据此宣布首次超时已修好。

用户于2026-10-30明确采用推荐方案，先资格再接入。当前仅新增隔离ARM64 CI资格脚本：独立公开依赖准备、所有子进程退出屏障、逐文件来源/摘要封口、只读挂载以及两份全新私有缓存的离线完整探针。尚未发布或安装手机公共基线，正式运行路径不变；CI通过也不替代真机资格。


首轮ARM64 CI 41069037872：6项边界测试通过，固定输入经生产Kotlin安装准备，独立完整Java/Compose/双ABI JNI/DEX探针96.528秒成功；封口拒绝Gradle去掉SHA1前导零的历史目录表示，未发布任何缓存归档，整体资格失败。已按固定Gradle9.8.2的GroupedAndNamedUniqueFileStore.stripLeadingZeros核对并修正完整内容地址比较，仍逐文件SHA256校验；新增前导零实物回归。来源：[固定Gradle实现](https://github.com/gradle/gradle/blob/v9.8.2/subprojects/core/src/main/java/org/gradle/internal/resource/local/GroupedAndNamedUniqueFileStore.java)。

资格脚本增加真实缺项边界：从独立只读副本移除固定foundation Android AAR，要求离线因该缺项失败，再联网完成完整探针并核实相同SHA256文件只写入该项目的私有缓存；完整基线与缺项只读副本均不得变化。Android显式验收夹具验证CI归档/manifest SHA、既有有界TAR扫描、正常文件与工具/探针身份；只在自有新会话中解包，临时只读挂载、两份空写缓存离线完整构建及普通UID JNI。尚未执行，不授予runtimeReady，不安装生产共享版本。编译/lint108任务与365源API守卫通过，251个Android @Test返回void。


第二轮ARM64 CI41070232235：固定完整准备探针完成，但封口又拒绝Compose的重命名变体（cache内animation-core.aar，公开URL为animation-core-android-1.12.1.aar），整体仍失败、无归档发布。已实际读取Google Maven公开.module核对：平台发布可指回原始multiplatform组件，并通过variants.files声明name/url/size/sha256。现在仅在公开.module及原始组件请求链成立、声明URL确实已请求且实际文件大小/SHA256相同时接受重命名；不按模糊文件名推断来源。新增真实格式回归包含内容地址本身合法但与.module摘要不符的拒绝。Windows8项6通过/2项Unix专用跳过，下一轮ARM64待执行。
