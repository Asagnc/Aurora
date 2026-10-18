# 胶囊 B 与 Android 16 真机验收

2026-10-18，0.1.8。用户选择 [对照图右侧 B](assets/approved-composer-B-20261018.png)，并授权开始测试。保留 [对话视觉方向](assets/chat-direction-20261018.png)。两图为内置 imagegen 生成的概念参考，示例任务、型号、计数不是实测结果；左侧 A 未采用。实际截图与测试证据另列。

## 已落地

- 单一无描边胶囊输入面，32dp 圆角，写入区最小 30dp；去掉内部指标分隔线，不嵌套模型选择卡片。附件小圆底、灯泡思考、具体型号、上下文圆环、发送/停止按同一基线排列。
- 灯泡与上下文控件保留 48dp 布局目标；发送按钮视觉 40dp，外层分配 48dp，避免与圆环点击区挤压。正常宽度/字号显示上下文百分比，低于350dp或字体大于1.2倍时只显示圆环；完整读数仍可点开或由无障碍描述获取。
- 圈内着色限于0–100%，数值不静默截成100%；缺失数据为未知，不填假零。控件不做持续统计查询。
- 模型显示具体型号；严格匹配的 Claude 型号仅简化展示为 Sonnet 5.5 等，完整 ID 保留在选择器和无障碍描述。自定义长名使用省略号，不修改模型配置或协议 ID。
- 输入框外同一行显示整轮估算 tok/s、最近输入 tokens、最近缓存读取占比。它们可原位展开详情；窗口上限明确来自配置，没有虚构上下文组成估算。详情高度有界、可滚动和关闭。
- 执行记录默认一行，点击才展开最近记录与结果；新工具事件不强制再次展开。完成数量标明“最近”，不将50项有界历史当作完整任务总量。
- 顶栏显示会话标题与 Aurora 身份、统一会话入口；助手回复在开放画布上显示身份。已有主题与用户配色偏好保留，不强制改成概念图紫色。
- 真机补获SELinux状态文件尾部NUL导致Android UIAutomator XML导出异常；服务读取时去掉终止符，Root连接测试增加无NUL断言。修复后重新验证Root界面树。

## 验证与边界

本地 core 45 项、debug / release / lint 通过；lint 保留一项既有 MiMo VectorPath 警告。API37 x86_64 模拟器33项普通测试与1项独立恢复通过。

Android16 ARM64 真我 GT6 SE：通过 install-r 从原0.1.3覆盖，未卸载或清空应用；33项普通测试、1项独立浏览器恢复、4项显式 RootRequired 测试通过。FolkPatch 既有授权可用，没有通过 Magisk 弹窗授权。Root测试覆盖：只读连接/断开/重连；真实 UID0 执行与受控产物 app 归属/读取；断开取消并等待 TERM 收尾后重连；直接取消等待；忽略TERM时超时收尾。测试只使用临时目录与自身监督进程，不改系统配置或读取个人文件。

原始 JUnit 结果：[普通真机](evidence/0.1.8/phone-ordinary.txt)、[独立恢复](evidence/0.1.8/phone-recovery.txt)、[Root](evidence/0.1.8/phone-root.txt)、[模拟器](evidence/0.1.8/emulator.txt)。普通测试中也执行了实际 ARM64 原生程序、输出预算、父退出与跨进程故障路径，不能再将新后端概括为“ARM64仅编译”。

实际截图：[真机胶囊B（示例页）](assets/composer-018/phone-chat.png)、[上下文展开（示例页）](assets/composer-018/phone-context.png)、[主应用Root连接（真实握手）](assets/composer-018/phone-root.png)。示例页只用于验证生产UI组件，不调用模型，也不伪装为真实Agent任务。

界面使用无网络/无凭据/不写入会话的 debug示例页检查，截图明确标记示例；与真实Root测试分开。检查正常布局、思考与型号选择、原位上下文、键盘，以及约320dp宽/1.3倍字体/自定义长型号。模拟器遇到的是SystemUI无响应对话框，选择等待后恢复；不将其计作Aurora崩溃，也不宣称整台设备零异常。测试后恢复模拟器尺寸/字号，手机返回正式主界面并保留安装。

外部Root管理器撤权、任意Root目录/挂载清理、独立守护进程、后台长任务仍未完整验收或实现。没有使用真实付费模型；示例缓存94%和速度不是性能或计费基准，也没有与DSHA/Eta做同负载性能测试。实际字体、图标和系统栏遵循Android及用户主题，不宣称生成图逐像素一致。

## 设计参考与差异

DSHA以其实际项目 https://github.com/DSH-APP/DSHA 为对照，不再将官方Web移动布局与DSHA混为一谈。参考其紧凑控制行、长型号和安全区处理；Aurora的Compose实现独立编写。遵循[Android触摸目标](https://developer.android.com/develop/ui/compose/accessibility/api-defaults#minimum-touch-target-sizes)与[IME Insets](https://developer.android.com/develop/ui/compose/system/keyboard-animations)，以实际设备布局检查补充静态概念图。
