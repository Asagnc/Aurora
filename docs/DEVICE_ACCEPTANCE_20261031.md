# 2026-10-31 当前安装回归验收

本轮先验收，不改UI、不新增产品能力。手机保留安装和数据，未调用付费模型、读取正式密钥、重启或修改Root管理器/Thanox规则。生产仍为0.1.40、Frida17.24.3；独立17.25候选不计入生产通过项。

## 本轮实际结果

| 批次 | 通过 | 跳过 | 宿主耗时（秒） |
| --- | ---: | ---: | ---: |
| 普通功能/浏览器/数据/终端/主题 | 128 | 1 | 39.467 |
| Root/Linux/PTY/离线Agent/socket/发包/SDK租约（修正夹具后） | 28 | 1 | 58.348 |
| 独立浏览器启动恢复/renderer死亡 | 2 | 0 | 1.465 |
| 抓包/轮转/静态逆向/代理 | 14 | 0 | 51.912 |
| 现有Frida策略/Root和ART/普通App attach/spawn/异常/取消 | 6 | 0 | 133.318 |
| 普通UID协议/FD/封印内存/原生包/生命周期边界 | 50 | 0 | 5.816 |
| 生产Skills页面/真实ColorOS文件选择器取消与自有ZIP导入 | 1 | 0 | 64.164 |

成功批次合计229次通过执行，去重227个用例。两个跳过执行中，renderer死亡已单独补测通过；余下可选PID namespace探针在当前可见/proc接口下不具资格。不是255项全部通过，也不把跨版本历史方法覆盖当成当前通过。

完整精简证据：[device-regression-20261031.json](evidence/0.1.40/device-regression-20261031.json)。安装的Debug APK及AndroidTest APK与本地主机构建字节已核对；普通批次执行后只重建/替换了测试APK，产品APK未替换。原始设备身份、日志、屏幕和数据库只留在ignored app/build。

## 发现的问题与复验

Root取消用例首轮失败：会话数据库创建后尚未建立目录，异步guest派发前的文件轮询可先到达Root端点。夹具改为进入block前显式准备commandRoot，不延时碰运气、不吞Root错误、不改生产退出/租约语义。相关29项重新执行28通过、1环境跳过，零失败；AndroidTest构建/lint108任务通过。

SAF首轮根据PackageManager推断DocumentsUI，但实际前台为ColorOS选择器，夹具等待错误包导致15秒超时。显式传入实际观察到的com.coloros.filemanager后，取消、重新打开、从Download只选择本轮随机命名ZIP、生产导入、移除源ZIP后继续读取及自有Skill清理均通过。未改默认选择器、未打开其他文件，不把263字节ZIP当484MB SDK归档验收。

此前本日SDK Root死亡1项102.468秒、Linux PTY Root死亡1项3.798秒、元数据Root重启4项16.668秒均有实际证明，详见[root-sdk-faults-20261031.json](evidence/0.1.40/root-sdk-faults-20261031.json)。宿主只对已校验UID/startTicks的精确pidfd发信号，错误ticks先拒绝。宿主验收增加独立通过状态计数，拒绝把JUnit含跳过的OK摘要当全部通过。

## 仍未开放或未完成

- Frida0x320历史原生崩溃根因仍未知；本轮六项未复现。releaseQualified=false保持。17.25仅独立构建/普通Shell版本通过，Root注入/故障矩阵和来源闭包未完成。
- 484MB SDK实际可视SAF完整安装、自有真实私仓/凭据撤销、完整长时后台/高负载/故障矩阵仍需补，不由现有子集替代。
- 正式签名、默认正式APK运行/性能、UI和真实付费供应商usage留待对应阶段与用户配置；完整未签名审查包见RELEASE_REVIEW_20261031。
