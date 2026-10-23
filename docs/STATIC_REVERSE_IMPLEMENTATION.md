## 2026-10-23 已批准静态逆向：来源与准备层

用户确认STATIC_REVERSE_PROPOSAL推荐组合：JADX1.5.6、Apktool3.0.3、Temurin27+35 ARM64 JRE，按需共享Root chroot，复用监督任务。新增编译期可信目录、固定bundle ID、长度/SHA256/取消/物理路径检查；准备层复用RootfsArchive/SkillArchive，ZIP和tar仍遵循各自链接规则，JAR完整复制后再次校验。只写未发布暂存目录，不把准备成功当已安装。

真实发布包重新下载重算摘要，JRE为ELF64 AArch64，304条目/197873172文件字节；JADX实际为lib/jadx-1.5.6-all.jar，已修正原先CLI单jar名称猜测。归档版共149086879压缩字节，不逐会话下载。可复现脚本scripts/verify-static-reverse-sources.py（默认核验本地文件，--download显式下载缺失包），未将大包提交Git。

新增5核心用例，含显式官方样本的扫描/真实JADX和Apktool准备；输入目录及样本文件计入Gradle test inputs，避免未提供样本的缓存掩盖后续验证。当前210核心：201通过、9 POSIX跳过；CI未提供官方逆向样本时该1项另跳过，不能写成来源已验证。Debug Kotlin、lint与Release R8通过。

Windows x86_64 JDK25上两个CLI --version返回正确；自有APK清单夹具解码exit0并产出manifest，但Apktool保留5项应用资源引用警告，不作为完整资源反编译通过。JRE27 ARM64未执行，手机未操作。源码包校验/WindowsCLI成功不能证明Android16 chroot执行。

尚未完成：共享版本发布与回收/租约、Root只读挂载、Agent安装/分析接口、UI状态、实际ARM64 CLI与取消/低内存/会话删除验收。本阶段不对Agent广告工具已安装可运行。

验证日志：outputs/static-reverse-official-qualified.txt、static-reverse-foundation-release.txt；来源/CLI证据见evidence/0.1.40/static-reverse-*。
