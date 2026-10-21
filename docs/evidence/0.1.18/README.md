# 0.1.18 Linux 基础安装阶段验证

2026-10-22。local-build.txt：核心测试、Debug、R8 Release、AndroidTest APK、lintDebug成功；lint0错误、1既有MiMo警告。核心126项登记，Windows125通过、1项POSIX提取跳过。官方ARM64样本通过真实摘要与扫描（文件内容113253544字节）；RootfsArchiveTest及LinuxElfTest原始XML同目录保留。

SHA256SUMS、其官方签名和ubuntu-signature.txt保留；固定指纹 `843938DF228D22F7B3742BC0D94AA3F0EFE21092` 的VALIDSIG已核对。GPG未设置个人ownertrust，因此输出unknown trust提示；本流程以预先固定的签名指纹校验，不修改用户个人密钥环。公开最小校验密钥在config/ubuntu-cdimage-2012.asc。

CI新增scripts/verify-linux-image.sh：重新获取官方签名/固定样本，校验签名指纹和锁定摘要，再在Linux重跑核心用例，真实提取并读取guest shell/动态解释器ELF。CI结果以对应提交记录为准；不要把Windows跳过项算作已通过。

新增2项Android持久化/迁移用例只编译，ordinary gate65；现有v3迁移用例适配schema5。没有设备Root/PRoot/chroot执行、真实手机空间/CPU/耗电或付费模型测试。本阶段executionReady=false，只提供基础环境管理，后续执行/PTY/挂载尚未完成。
