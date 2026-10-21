# Skills 与版本化资源方案

研究日期：2026-10-21。状态：待用户确认；没有接入新依赖、安装器或数据库迁移。

## 推荐组合

采用开放 Agent Skills 的 `SKILL.md` 格式，使用现有 SQLite WAL 管理索引/安装恢复/版本引用，内容按 SHA256 存放在共享只读资源；Agent 按需加载指令和文件，复用已有普通/Root命令及PTY执行。用户入口升级为完整 Skills 页面，Agent 同时拥有发现、读取及安装管理工具。

YAML 推荐 `org.snakeyaml:snakeyaml-engine:3.2`，ZIP 推荐 `org.apache.commons:commons-compress:1.28.0` 的 ZipFile 中央目录读取。以上版本已通过 Maven Central 元数据与发布POM核对；实施前检查Android36、R8、体积和传递依赖，不将Java/JVM兼容直接当作Android验收。

## 实际比较

| 方向 | 查到的情况 | Aurora取舍 |
| --- | --- | --- |
| Agent Skills | SKILL.md的YAML元数据与Markdown正文，支持附属脚本/资料 | 作为互通格式，不强制作者另写Aurora清单 |
| Eta最新源码 | 9adcda01：有安装日志、原子目录移动、注册恢复和资源读取；自写简化frontmatter，ZIP采用流式读取 | 学习安装恢复与模型入口；不复制简化YAML解析器，不据源码推断其存在已验证漏洞 |
| DSHA最新源码 | 891405e0：agent-skills是工作流文档；Cordis/Node插件是另一类运行扩展 | 区分工作流与执行插件；不声称导入其Node插件就能在Aurora原生执行 |
| 自写YAML子集 | 少一个依赖，但多行、引用、类型和错误定位需要长期维护 | 不推荐 |
| Kaml | 官方仓库已归档，最后版本0.104.0 | 不作为新依赖 |
| Kotaml / Engine KMP | 维护中的Kotlin路线，Kotaml最新发布0.111.0；Engine KMP元数据5.0.0 | 可行；目前Aurora没有KMP目标，也不需要完整YAML序列化包装层，优先直接Engine |
| Java ZIP流式解压 | 不能获得中央目录独有的全部属性；流式条目与中央目录可能不一致 | 先下载到有界暂存文件，再用成熟ZipFile预检，不自写完整ZIP实现 |
| 自建私有插件格式/为Skills启动Node | 会增加作者适配或常驻运行时 | 当前不选；执行工具包另有实际运行时契约 |

## Agent效率与缓存

1. 工具定义保持固定，技能名称不塞进动态工具schema枚举。小规模目录采用稳定排序的名称/说明摘要；正文与附属文件按需读。大目录通过分页/搜索发现，避免数百项摘要挤占用户上下文。
2. 会话固定一份技能版本快照；目录及变更通过协议原生历史追加，避免每次安装/开关技能就重写system和所有旧消息。相同快照不逐轮重复注入，技能加载去重以当前有效上下文记录为依据，不能在新协议范围里误判“已经加载”。
3. 正在执行的Agent/PTY继续持有原版本；全局更新只发布新版本。现有会话显式同步时追加变更说明，保留旧工具执行证据；不静默替换运行中的脚本。移除旧版本先检查会话与活动执行引用。
4. 固定目录读取路径及文件摘要，不每轮全盘扫描或重哈希大工具包；初次安装建立索引，实际读取时校验对应文件。变化会给出诊断，不继续伪称相同版本。
5. 这些策略有利于稳定请求前缀；不能保证供应商缓存命中、费用最低或任务成功率最高。验证以协议历史/指纹回归和后续真实usage为准。

## 格式与安装

- 支持本地ZIP、会话中的ZIP以及公共HTTPS/GitHub目录来源；GitHub引用解析后固定提交/发布版和实际内容摘要，不把可移动分支名当版本。优先正式稳定发布；没有发布版时标明提交快照，不冒充稳定工具版本。
- 普通SKILL.md无需包清单；发行版本名可从标准metadata或来源取得，但内容哈希才是实际身份。同名技能使用来源命名空间与独立ID，界面明确显示冲突，不静默覆盖。已知客户端扩展字段保留，不自动赋予额外权限。
- YAML只解析frontmatter，采用有界基础数据结构；保留原始文件，支持多行/引号/metadata。名称等可恢复的格式差异给诊断；缺少说明、无法解析或类型矛盾明确报告。不用“遇到冒号就截断”或静默猜改原文件。限制输入、嵌套/事件数量、递归和别名展开，避免不受控构造对象。
- 包先进入共享资源的staging，预检路径、中央目录预算、重复/冲突路径、压缩方式、符号链接和特殊文件。解压中按实际读到的字节执行文件数/单文件/总量预算，不只相信声明大小或压缩比。初始ZIP64MiB、展开256MiB、4096条目；大型运行时依赖单独管理，不冒充普通Skill包。
- 使用SHA256记录实际下载、逐文件摘要和规范化清单。用户/发布者给出期望摘要时校验；自己计算的摘要只保证后续一致性，不等于来源真实性证明。网络请求使用独立下载上下文，公共HTTPS有限重定向，不继承模型API密钥。
- 完整校验后同文件系统原子发布内容目录，再提交SQLite索引；文件系统与SQLite不是联合事务，必须有安装恢复记录，启动按记录完成/回滚，不把半包列为可用。取消后清理本次暂存；相同内容不重复安装。
- 发布目录正常更新路径只增新版本、不原地修改。普通文件工具只读；任意Root或同UID原生程序能绕过合作式约束，因此不声称哈希目录是安全沙箱。

## 三类空间及清理

- `resources/skills/<内容摘要>/...` 存工作流与资源，`resources/packages/...`预留版本化工具依赖；运行时编译/下载缓存属于共享写空间的独立命名目录。
- 产物、临时解包输入和任务日志默认落到会话；删除会话取消执行并释放版本引用，保留公共技能/依赖。
- 版本卸载先从新任务目录禁用，仍被引用时显示占用来源；未引用的旧版本可批量清理。下载暂存、安装内容和用户保留项目分别统计，避免把已安装工具当垃圾删掉。
- 复用原生SQLite WAL，新增技能/版本、安装操作及会话版本引用记录；非破坏迁移与恢复需专项测试。具体表拆分按事务边界实现，不增加数据库框架。

## 执行能力不伪装

Skill是指导Agent完成任务的工作流，不自动生成其依赖的Python、Node、Linux或系统权限。正文和参考文件只读；脚本通过已有执行器、实际可用解释器与固定版本路径运行，工作目录和产物归本会话。不逐轮复制整套依赖，不增加常驻Skill进程，不自动执行安装钩子。

Android对现代targetSdk应用有可写应用目录执行限制。普通模式继续使用系统/APK已打包的可执行程序读取脚本；缺失解释器/ABI/权限时报告实际缺失。Root下载工具是否能执行还受SELinux/管理器影响，必须探测并真机验证。不降低targetSdk，也不为非Root强行增加高损耗替代。PRoot/chroot与原生编译链仍是后续单独实现，不能因为装进一个SKILL.md就把它们列为已支持。

外部技能的allowed-tools/兼容性字段作为元数据，不代替用户授权或Root实际状态；DSHA专用设备桥等不存在时给出兼容性诊断。对于已授权任务，正常读取已安装资料不重复弹确认。

## 用户页面

抽屉现有Skills入口进入原生完整页面：搜索/分类、名称和用途、来源/版本、已安装/运行条件缺失/正在使用等实际状态；点开查看正文、文件与依赖、版本和存储详情。支持导入、启停、更新、回滚、卸载和未占用版本清理。更新不打断旧任务；安装错误说明发生在哪一步，并可重试本次操作。进度不显示虚构百分比。

## 验收与实施顺序

先校验两项依赖的Android/R8适配与具体传递版本，再做frontmatter/归档/摘要核心；随后安装恢复、SQLite迁移、版本引用与Agent入口；最后接原生页面。模型工具与用户界面必须用同一安装/读取服务。

测试包括常见合法YAML、坏文档、嵌套/别名、未知扩展、同名冲突、ZIP路径越界/重复/链接/坏CRC/压缩预算、取消/缺空间、发布与数据库之间各崩溃点、更新时读取旧版本、PTY存续、删除会话不删共享包、忙版本卸载拒绝、稳定前缀/去重。离线测试可先跑；设备、真实网站和模型行为另记，不借历史结果宣称新功能已验收。

## 核查来源

- Agent Skills格式：https://agentskills.io/specification
- Agent集成：https://agentskills.io/client-implementation/adding-skills-support
- Eta核查版本：https://github.com/Mangi-11/Eta/tree/9adcda01fb4b2e3440f1ade5ffdc91dd36e8471d/app/src/main/kotlin/io/github/mangi/eta/agent/skill
- DSHA工作流与插件分工：https://github.com/DSH-APP/DSHA/blob/891405e006da2316ff593cb4a74485de6c1d7b19/agent-skills/README.md
- Engine发布元数据：https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/maven-metadata.xml
- Engine发布POM：https://repo.maven.apache.org/maven2/org/snakeyaml/snakeyaml-engine/3.2/snakeyaml-engine-3.2.pom
- Kaml维护声明：https://github.com/charleskorn/kaml
- Kotaml：https://github.com/Heapy/kotaml/releases/tag/v0.111.0
- ZIP实现取舍：https://commons.apache.org/proper/commons-compress/zip.html
- Android执行限制：https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission

来源解释格式/库/平台事实；Aurora的快照、缓存放置、安装事务、资源预算和页面组织为本提案设计判断，不是上游替Aurora作出的性能保证。
