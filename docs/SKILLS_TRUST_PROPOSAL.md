# Skills 私有来源与发布者签名方案（待确认）

核查日期：2026-10-26。当前公共HTTPS/GitHub、SHA256一致性检查、原子发布、版本引用已实现；私有认证和发布者签名未实现。本文件不授予任何新入口资格，不把已计算哈希当成作者认证。

## 私有来源比较

| 方案 | 能力与代价 | 建议 |
| --- | --- | --- |
| GitHub细粒度PAT＋现有AndroidKeyStore凭据库 | 只需选定仓库的Contents只读权限；不启动登录服务，无新依赖；用户自行创建/轮换令牌 | 首版采用 |
| OAuth Device Flow / GitHub App | 适合多人分发、短期凭据；需要应用注册、登录状态、轮换与额外产品流程 | 后续有需求再选 |
| URL凭据、任意自定义Header或共享模型密钥 | 不适合仓库限定与重定向隔离，容易进入历史/日志 | 不采用 |

推荐首版只支持github.com私有仓库，令牌通过原生页面输入，秘密复用CredentialVault的KeyStore AES-GCM与noBackup目录。模型只见凭据配置ID和已脱敏仓库标签，不能读取、写入或替换秘密。配置严格绑定owner/repo；只向api.github.com指定仓库API发送Bearer，不向原始URL、codeload、跨主机重定向传播。解析固定commit后通过官方zipball API取得临时下载链接；允许的官方目标单独检查，临时URL只在当前请求内存使用，不写SkillSource/日志。公共来源继续走无凭据路径。

## 发布者签名比较

| 方案 | 能力与代价 | 建议 |
| --- | --- | --- |
| 只验证用户给出的SHA256 | 检查字节一致；没有独立的发布者公钥身份 | 保留现有功能，不称签名 |
| 明确钉住公钥的Ed25519离线验签 | Android33+内建JCA可用，无新密码库；公钥需用户独立核对，轮换/撤销显式处理 | 首版采用 |
| Sigstore bundle / GitHub attestation | 标准证书、身份与透明日志证明；还需信任根更新、身份策略及Android Java库资格 | 以后单独资格，当前不直接接入 |
| OpenPGP / minisign | 已有作者生态；新解析器/库与算法资格不同，不能冒充上述格式互通 | 有实际发布源时再比较 |

推荐以ZIP为签名对象，采用明确标识Aurora v1的有界旁路声明；声明绑定规范化来源、ZIP SHA256与字节数，Ed25519签名覆盖固定域前缀和固定ASCII编码后的这些字段。公钥是严格Ed25519 SPKI，签名64字节，字段/长度/编码严格校验。此为Aurora专用最小封装，不是Agent Skills标准，也不兼容minisign/Sigstore。摘要先流式读取，验签只处理短声明，避免JCA对整个64MiB ZIP的内部缓冲。具体编码在实现前固化黄金向量并写格式文档。

信任公钥只能由用户在原生页独立导入并查看指纹，归属精确来源；下载包自带公钥不能自动取得信任。要求签名的来源遇到缺失/错键/错范围/坏签名必须拒绝安装和更新，不回退无签名。签名通过不代表工作流安全，不授予Root/额外工具权限，也不提供时间戳、新鲜度或自动防回滚承诺。旧版本继续按已有显式回滚与会话快照管理；验签事实记录当时所用公钥指纹，撤销后禁止新的受信安装，已运行租约按既有生命周期结束。

## 持久化与接入范围

推荐沿用原生SQLite WAL，增加来源配置/信任公钥与版本验签证据，不建立另一个数据库；凭据本体仍在现有KeyStore库。数据库只存秘密UUID引用。先成功写秘密再发布配置引用；删除先撤销引用再删除秘密，启动清理仅针对可证明属于Skills的孤立凭据，不扫描/删除模型凭据。迁移须保留现有Skills、引用和活动账本。公共未签名包与现有普通SKILL.md仍可显式安装，展示未认证状态；来源设置要求签名后不得被Agent降级。

范围包含原生配置/导入页、同一安装器安装/更新、Agent仅选已有配置ID、错误脱敏与迁移恢复。真实私有仓库需要最后用户指定自有仓库/凭据进行验收；不取用本机gh令牌来假装完成产品验收。不访问手机或付费模型。

## 验收门槛

- 主机自有HTTPS/模拟API验证仓库限定、重定向不带令牌、临时URL不落盘、401/403/404/取消/错误脱敏。
- 署名黄金向量、错来源/大小/摘要/算法/签名/未知键/撤销/重复字段/预算拒绝；安装恢复不能将未验签版本发布为受信。
- SQLite升级保留现有记录，KeyStore失败与写秘密/写索引间中断可恢复；UI不展示秘密，Agent schema不接受秘密或信任根写入。
- Debug/Release/R8/AndroidTest编译及完整CI；Android JCA/KeyStore与自有私仓实际验收另记。

## 官方依据

- GitHub PAT及权限：https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens
- 仓库ZIP API、Contents read和私有临时链接：https://docs.github.com/en/rest/repos/contents#download-a-repository-archive-zip
- Agent Skills格式（不定义本方案的签名封装）：https://agentskills.io/specification
- Android Signature算法支持，Ed25519为API33+：https://developer.android.com/reference/java/security/Signature
- Sigstore bundle证书/透明日志材料：https://docs.sigstore.dev/about/bundle/

以上官方资料支持各自协议事实。仓库限定、SQLite/凭据事务、来源策略及最小封装属于本提案设计，尚未实现或授权。
