# Skills Ed25519 软件验签底层纠正（待确认，2026-10-26）

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

验收：独立RFC8032/现有Python黄金向量、坏签名/错键/小阶或非法点/非规范编码/错范围/声明变更拒绝；JDK与Android普通UID均实际通过；同包AES-GCM vault/迁移/撤销/旧版本获取回归；Debug和R8 Release均验证并测量APK实际增量、许可与依赖精确固定。未通过前不授予Skills签名资格。研究下载的JAR仅保留在忽略的build目录，目前尚未集成。

此为对已批准底层的必要调整，按用户和AGENTS.md“新底层/依赖先比较确认”约定等待确认；网络/Root/浏览器等独立测试继续。

## 官方来源

- Android16实际源码：https://android.googlesource.com/platform/external/conscrypt/+/android16-release/common/src/main/java/org/conscrypt/OpenSSLProvider.java
- 平台Signature算法表：https://developer.android.com/reference/java/security/Signature
- BC正式下载及版本：https://www.bouncycastle.org/download/bouncy-castle-java/
- Maven元数据：https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml
- Tink元数据：https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml
- Tink算法支持：https://developers.google.com/tink/supported-key-types
