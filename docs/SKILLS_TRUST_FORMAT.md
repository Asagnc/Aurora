# Aurora Skills 发布者验签 v1

这是Aurora专用旁路封装，不是Agent Skills、minisign、OpenPGP或Sigstore格式。JCA算法为Ed25519，公钥为严格44字节SPKI DER（12字节前缀302a300506032b6570032100，随后32字节公钥），界面输入其标准有填充Base64。公钥指纹为该DER的SHA256。不要输入私钥。

ZIP最大64MiB。先流式计算所选归档实际SHA256和字节数，然后签署下列ASCII字节；字段顺序、大小写、冒号、LF和最后LF全部固定，字节数为无前导零的十进制。来源使用小写owner/repo，绑定仓库范围，不绑定可移动标签、不提供时间戳或新鲜度证明。

```text
Aurora Skill Archive v1
source:github:owner/repo
sha256:<64位小写十六进制>
bytes:<实际ZIP字节数>
```

签名为对上述短声明的纯Ed25519签名，64字节。旁路文件仅两行ASCII，第二行为签名的标准有填充Base64，两行均用LF结束；总上限1024字节，禁止额外字段/空行/CRLF/其他版本。

```text
aurora-skill-ed25519-v1
<64字节签名的标准Base64>
```

作者可用自己的Ed25519工具对短声明签名，再发布ZIP和旁路文件。Aurora不生成/保管作者私钥，不自动信任归档自带公钥。GitHub自动生成的仓库ZIP的压缩字节不承诺永久固定，任何变化均应使旧签名失败；推荐签署自己发布的精确ZIP文件，公共GitHub release ZIP或本地导入ZIP可使用所选仓库策略。私有认证首版支持仓库/tree URL经API zipball下载；私有release asset API不在此版范围，不能把令牌转发到普通GitHub下载URL绕过限制。配置的旁路地址只能是无userinfo/query/fragment的HTTPS443地址；私有旁路可用同仓库API contents地址（读取默认引用），其他HTTPS地址仅匿名读取。若需固定引用可用对应公开不可变地址或用户选择精确本地归档，错配不会回退无签名。

验签证据随版本来源JSON保存：scope、archiveSha256、archiveBytes、keySha256、signature；它证明安装时所选归档通过了该公钥检查。文件内容随后仍由既有内容清单哈希验证。改换公钥、撤销配置、需要签名而未签名的版本，会阻止新的快照/显式版本选择/启用；正在执行的快照继续持有原版本。显式安装/更新也会在同一目录锁内复核策略，期间配置改变则拒绝发布。未配置签名的公共包继续显示未认证。

主机黄金向量使用RFC8032公开测试公钥与独立Python cryptography生成的签名，私钥不进入产品或仓库。输入为`owned skill archive bytes`随后LF（26字节），来源github:example/skills，SHA256为6f6c357ea242eadbc1bbfb4d213649b11087e151e6b78398acf310457d2efdfd；预期签名见SkillSignaturesTest.kt。该内容仅密码学测试数据，不冒充合法ZIP；ZIP格式/CRC/路径等仍由真实安装预检分别验证。
