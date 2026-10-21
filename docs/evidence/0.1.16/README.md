# 0.1.16 本地验证

日期：2026-10-21。完整命令日志：local-build.txt。核心101项全部通过；新增14项原始XML同目录保留。

- SkillPublicationTest 6：发布失败恢复、模拟移动后未提交、批量第二包失败、恢复失败重试、发布前取消和发布中取消。
- SkillPublicationCleanupTest 1：保留原始提交错误及清理失败、日志可重试。
- SkillArchiveTransferTest 4：正常关闭、不响应线程中断的读取通过close结束、打开阶段取消、已有目标不覆盖。
- SkillTextSliceTest 3：汉字/emoji跨64KiB、损坏/截断UTF-8、空文件与完整多字节文本。

Debug、R8 Release、AndroidTest APK和lintDebug全部成功；lint0错误、既有MiMo VectorPath警告1。Debug APK SHA256：`49aa2aa9d27e41c7fe8a459071a65d5938c0fd7354e5cb8c02e7cd2bcd0ce237`。

没有连接设备，没有运行新增Android用例/Root/真实SAF/付费模型。本地故障夹具不等于真实断电、外部提供器必然可取消或性能领先。普通设备门槛仍63（设备用例没有新增）；集中真机验收安排不变。

上一版112e496完整CI39606872871已通过。本版CI以当前提交的GitHub Actions状态为准。

依据：
- [协程取消与NonCancellable边界](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html)
- [runInterruptible对可中断阻塞调用的契约](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-interruptible.html)
- [ContentResolver取消信号接口](https://developer.android.com/reference/android/content/ContentResolver)
