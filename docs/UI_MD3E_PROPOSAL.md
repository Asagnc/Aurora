# 设置 UI 与官方 Material 3 Expressive 方案

2026-11-09：用户要求 UI 优先，指出设置详情堆砌文字、组件未统一为 MD3E。此指令覆盖此前“功能验收后再做 UI”的顺序；不变更 Root／任务／存储后端。

## 实际核对

- 当前项目显式固定 material3 1.4.0；AuroraTheme 自定义字号、形状，FullPage 用手工 Row 模拟标题栏。执行环境／工具能力、Linux、Frida 页面默认直接呈现实现细节和长说明。
- Google 官方发布记录：当前稳定 1.4.0，候选 1.5.0-rc01 发布于 2026-11-04。
- 已下载 Google Maven 两版 sources.jar，确认 1.4.0 的 MaterialExpressiveTheme 是 internal，1.5.0-rc01 为 public；不通过反射或 suppress 访问内部 API。
- rc01 官方 AAR 元数据要求 compileSdk37、AGP9.1.0；项目现有 compileSdk37／AGP9.4.1 满足元数据下限，尚不等于实际构建和交互兼容性通过。
- 官方来源：https://developer.android.com/jetpack/androidx/releases/compose-material3 、https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/ 。固定输入摘要见文末。

## 比较

| 方案 | 优点 | 限制／判断 |
| --- | --- | --- |
| 保持 1.4.0，只用公开稳定组件 | 依赖不变，可立即减少文字并统一设置结构 | 官方 Expressive 主题 API 不可公开使用；不能把此路线称为纯官方 MD3E |
| 固定官方 1.5.0-rc01 | 使用公开 MaterialExpressiveTheme、官方主题／组件／动效，避免自绘仿制 | 非最终稳定版，需要用户允许稳定版本规则的明确例外；必须验证依赖解析、Debug／Release／lint及后续真机 |
| 第三方 Expressive 组件或自绘替代 | 可以覆盖部分视觉效果 | 增加依赖／维护或偏离纯官方组件要求，不采用 |

推荐 rc01，但在用户确认前不修改版本或调用仅该版本公开的 API。既有版上可独立完成信息层级、短文案、标准组件和详情按需查看。

## 页面修改与验收

1. 设置入口按模型、设备、外观与数据分组；保留供应商、Root、Linux、Skills、工具、文件等现有能力入口。移除手绘箭头和模拟顶栏，使用官方 TopAppBar／ListItem／Card／Button／Switch。
2. 详情页第一屏先显示实际状态和主要动作；协议、预算、UID、版本摘要、权限机制放入用户主动打开的详情。危险操作的直接影响仍在操作前可见，尤其是 SELinux 策略变更。
3. Linux 基础环境、APK 构建工具、维护恢复分组。高级 PRoot、旧运行元数据清理和协议说明不占默认第一屏；已安装、进行中、失败、取消保持明确。
4. Root 保持管理器先授权、手动连接；不增加授权弹窗或更换 libsu。Frida 正式门控仍 false，不因 UI 修改授予资格。
5. 外观选择使用官方单选分段按钮与开关，保留动态色、自定义强调色、纯黑与彩色图标设置。若批准 rc01，替换手写全局字号／形状为官方 Expressive 默认值。
6. 小屏及大字体下内容可滚动、动作不挤压；返回、展开收起、明暗主题和状态可读。构建与 lint 在电脑验证；真机视觉与交互另行集中验收，不用概念图冒充实机。

历史 Eta 实拍与已批准方案见 UI_AUDIT_2026-10-16、UI_AUDIT_2026-10-21；学习其信息组织，当前用户的官方 MD3E 要求优先。

## Google Maven 输入摘要

- `material3-android-1.4.0-sources.jar`：703105 字节，SHA256 `3e3209422a382566a780e2c1410edd866e4474498098fdbcf942c690ade1ddaa`。
- `material3-android-1.5.0-rc01-sources.jar`：906223 字节，SHA256 `0e9c5b37e75203b44b4987b3ccc0db41637303103ca21739691d7533cbe7cbb1`。
- `material3-android-1.5.0-rc01.aar`：4890524 字节，SHA256 `eb055c58e9d81cbf9cb38bca4541a7dc701660e558f37f91edab2d622ac24e9a`。

2026-11-09用户明确批准固定官方1.5.0-rc01，构建与验收通过后使用。允许本次候选版本例外，不自动允许其他预发行依赖或后端替换。
