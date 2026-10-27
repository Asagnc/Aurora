# Skills Ed25519 软件验签底层纠正（2026-10-27 检索后选择）

真机Android16/SDK36已明确：KeyFactory.Ed25519落到AndroidKeyStore且拒绝导入SPKI；KeyFactory.EdDSA不存在。Android Signature文档列出Ed25519/API33，并不保证任意外部发布者公钥可由平台软件provider导入。先前据此推荐“只用平台JCA、无需密码库”的推断不成立。Android16-release实际Conscrypt OpenSSLProvider源码未注册EdDSA/Ed25519导入，与上游master新增实现不能混为一谈。现有v1短声明、严格SPKI/签名长度、来源钉键/撤销/快照策略仍可保留，失败不得冒充验签成功。

## 比较

| 路线 | 已核实事实/代价 | 取舍 |
| --- | --- | --- |
| 仅平台JCA | 该真机无法导入已知公开黄金向量；改算法别名仍失败 | 不再声称可用 |
| Bouncy Castle bcprov-jdk18on 1.86 轻量Ed25519 API | Maven Central release1.86，官方2026-09-11稳定版；纯Java、无JNI、不需要全局注册provider，单JAR无额外框架依赖。归档7,224,011字节，SHA256 2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f。R8实际增量尚需测量，不以JAR大小当APK增量 | 推荐 |
| Google Tink Android 1.24.0 | Maven当前正式版本；官方支持Ed25519，属于更完整的keyset/序列化/密码操作框架，本需求只有固定短声明验签 | 可用替代，当前无须引入整套keyset管理 |
| 打包Conscrypt或调用隐藏NativeCrypto | Android16内置实现缺所需软件入口；新native分发还需ABI/JNI/生命周期资格；不能以master代码或反射非SDK接口代替稳定可用支持 | 不推荐 |
| 自写/搬运曲线实现或未维护的小库 | 验证、审计和长期修复成本，且不能借“无依赖”证明正确性 | 不采用 |

## 推荐范围

仅固定引入bcprov1.86的轻量Ed25519验签及公钥有效性检查，不创建/注册全局JCA provider，不更改OkHttp/TLS或现有AndroidKeyStore AES-GCM凭据库。SPKI严格前缀/32字节公钥、64字节签名、固定短声明及离线旁路格式不变；不改成Ed25519ph，不默默接受其他曲线/算法。模型仍不能写可信公钥。只调用验签，不生成或访问任何发布者/用户正式私钥。

验收：独立RFC8032/现有Python黄金向量、坏签名/错键/小阶或非法点/非规范编码/错范围/声明变更拒绝；JDK与Android普通UID实际验证；同包AES-GCM vault/迁移/撤销/旧版本获取回归；Debug和R8 Release均验证并测量APK实际增量、许可与依赖精确固定。BC现已按下述用户授权集成，运行资格按实际批次记录；研究JAR仍只保留在忽略的build目录。

用户2026-10-27要求“网上检索主流做法，比如claude，然后取最优”。按此次授权，在重新核对官方实践后选择BC1.86轻量API，替换不可用的平台导入。没有新增密钥管理/来源数据库/签名格式，也没有把签名变成所有Skills的强制入口。Debug真机黄金向量、凭据库、迁移/恢复5项已通过；R8 Release与包体增量仍继续验收。

## Claude官方实践与最终取舍

| 核对对象 | 官方实际机制 | Aurora处理 |
| --- | --- | --- |
| Claude Skills | 标准SKILL.md与资源、按需加载，强调可信来源和内容审查；官方资料没有要求每个Skill附发布者Ed25519签名 | 保持标准目录/ZIP兼容，不要求普通使用者生成或配置签名密钥 |
| Claude Code插件信任 | 区分官方/第三方来源、内容审查；git来源可固定ref/sha，归档可固定sha256，不匹配拒绝安装 | 沿用既有来源绑定、固定版本/摘要、发布前策略重核和撤销；不把仓库名称或哈希本身说成发布者身份认证 |
| Claude企业扫描 | 企业可选上传/编辑时内容扫描，区分pass/warn/fail；不覆盖所有旧内容/MCP或所有配置 | 与密码验签是不同能力，本轮不新增付费模型调用或假装已有恶意内容扫描器 |
| 已批准的Aurora可选发布者签名 | 用户显式信任公钥，对来源、归档摘要及字节数进行离线验签 | 只修复不可用的Android软件验签底层。签名证明绑定公钥签署了这些字节，不证明脚本/指令安全，不扩展工具权限 |

主流做法不是统一采用某个密码库。这里保留已批准的Ed25519格式；平台在实机不可用，Tink可实现但本需求没有keyset轮转/多密码原语需求，BC轻量API可直接严格验证外部公开点和短声明，无需注册provider或迁移凭据库。因此选择BC是当前Aurora约束下的工程取舍，不声称所有产品通用最优。

本次另核对固定Maven JAR公开API与SHA256；完整公钥点验证拒绝非法/小阶/非规范点，验签使用纯Ed25519，非ph。许可证以原始META-INF/LICENSE.md字节随APK assets/licenses/bcprov-1.86-LICENSE.txt携带。实际APK增量须以R8前后产物测量，不能用JAR压缩大小代替。

新增官方对照来源：

- [Claude Skills安全边界](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview#security-considerations)
- [Claude Code插件来源/归档完整性与内容审查](https://code.claude.com/docs/en/plugins/security)
- [Claude Code git来源固定ref/sha](https://code.claude.com/docs/en/plugin-marketplaces)
- [Claude企业Skill和插件内容扫描的范围](https://support.claude.com/en/articles/15927065-get-started-with-skill-and-plugin-scanning)
- [BC1.86正式发布](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/)
- [Tink算法支持](https://developers.google.com/tink/supported-key-types)

## 官方来源

- Android16实际源码：https://android.googlesource.com/platform/external/conscrypt/+/android16-release/common/src/main/java/org/conscrypt/OpenSSLProvider.java
- 平台Signature算法表：https://developer.android.com/reference/java/security/Signature
- BC正式下载及版本：https://www.bouncycastle.org/download/bouncy-castle-java/
- Maven元数据：https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml
- Tink元数据：https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml
- Tink算法支持：https://developers.google.com/tink/supported-key-types
