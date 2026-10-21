# 0.1.17 本地验证

2026-10-21。核心115项通过，新增14项：GitHub来源解析5、提交解析/重复更新1、文件搜索分页3、下载重定向/预算/取消5。SkillNetworkTest原始XML还包含此前3项，用例数不要重复相加。原始XML与完整 local-build.txt 同目录保留。

本地 `:core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug` 全通过；lint0错误、既有MiMo VectorPath警告1。SQLite仍v4，没有新依赖或设备用例数量变化，普通门槛仍63。

界面收尾：文件列表页采用mutableIntStateOf/mutableLongStateOf；移除两项新增装箱提示，最终lint为0错误、1既有警告、0提示。文件预览使用固定标题并将完整路径放入正文，避免长文件路径挤压标题栏。

范围：实际来源解析及分页函数由Android/Agent共用，网络使用确定性OkHttp夹具。原生来源选项、详情事件刷新、文件浏览和分段读取页只完成编译/lint，未在设备点击。没有真实GitHub下载、真实SAF、付费模型、耗电或性能排名证据。当前来源不支持私有认证；含斜杠分支需显式ref，不声称能自动消歧所有tree URL。

官方行为依据：[GitHub提交API](https://docs.github.com/en/rest/commits/commits#get-a-commit)、[最新发布API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。指定ref解析提交后下载固定SHA；仓库根链接无ref时仍查询最新正式发布，无发布时明确显示默认分支快照。

上一版95f435f完整CI39608255424已通过。本版CI以GitHub Actions当前提交记录为准。
