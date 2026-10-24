# Frida发布输入核对（2026-10-24）

本页是发布材料盘点，不是已完成许可证闭包、源码交付或Android运行资格。没有增加运行时依赖，Frida worker仍未放入APK。新增材料仅约33KiB、位于tools/frida-licenses，不下载/展开整套SDK副本或安装到手机。

## 固定来源

GitHub官方latest发布仍为17.24.3（published_at 2026-10-19T09:26:22Z）。检查顶层tag的真实gitlinks，而不是把子模块当作顶层仓库内的普通目录读取：

| 输入 | 固定commit |
| --- | --- |
| frida-core | 57161b0ddd2311adf18e84f4680f9cb935ce8376 |
| frida-gum | 3f82ef6a5b2413867835a45fe341913455473303 |
| releng | 8429c0c9caf8b071955f64869a1b0ede58ad9f34 |
| Java bridge v7.0.13 | 6ab2a63e4b02ab881f11ea06632cebea4b1b435e |

依据：[官方17.24.3 tag](https://github.com/frida/frida/tree/17.24.3)、[固定releng依赖图](https://github.com/frida/releng/blob/8429c0c9caf8b071955f64869a1b0ede58ad9f34/deps.toml)。依赖图包含工具链、平台条件和可选项，不能把整张图直接当成实际worker静态依赖的精确清单；后续须与最终链接/嵌入对象核对。

## 实际devkit盘点

已复核现有ARM64 devkit归档SHA256为0c688824a24949e7be77109fd2c7306bba60cb47f608789e141bb24cd23b1005，长度60,903,232。流式读取其中libfrida-core.a的GNU ar头与长名称表，不落盘展开静态库；得到2,598个普通成员。原始盘点仅在outputs/frida-source/ar-inventory.json，SHA256 b093d0c238c3207b5b2d9c9746aa01188d1184d203afb11e99a2ed75fa391467，未把大库存入Git。

成员包含frida-data-agent-blob、helper-process-blob和helper-backend-blob。故外层worker ELF的16KiB对齐和系统DT_NEEDED白名单，不能单独覆盖其嵌入式agent/helper的对齐、依赖或源码。归档成员也不等于全部保留在最终worker：最终链接有gc-sections，仍须核对真实保留对象与嵌入资源。

## 已取得的文本

tools/frida-licenses/sources.json记录实际下载的Core/Gum COPYING及单独来源的标准LGPL2全文；均复核Git blob SHA1、SHA256和长度。Core/Gum文本含wxWindows例外，Gum还列有其他来源声明，不能只用一个简写标签覆盖。

Java桥npm包没有随附独立许可证文件；官方v7.0.13树也没有该文件，package.json有许可声明。Core仓库引用的COPYING.LIB亦未在其固定根目录中提供，因此保存的LGPL2标准文本明确来自固定SPDX库，不伪称从Core取得。

依据：[Core COPYING](https://github.com/frida/frida-core/blob/57161b0ddd2311adf18e84f4680f9cb935ce8376/COPYING)、[Gum COPYING](https://github.com/frida/frida-gum/blob/3f82ef6a5b2413867835a45fe341913455473303/COPYING)、[桥固定package.json](https://github.com/frida/frida-java-bridge/blob/6ab2a63e4b02ab881f11ea06632cebea4b1b435e/package.json)、[标准LGPL2文本](https://github.com/spdx/license-list-data/blob/31ba1a50e5397e00a304dbadc76531740e89ee48/text/LGPL-2.0-only.txt)。

## 继续实施的顺序

1. 最终链接对象/嵌入资源与固定依赖版本、逐项notice对齐；单独检查bridge运行时代码与构建期工具，不能把整个npm开发闭包误报为Android运行依赖。
2. 准备真实对应源码/构建与所需重链接材料；验证归档完整、固定输入和可重建性，而非只给可变branch链接。这里尚未完成完整Frida静态闭包，不能沿用guard离线重建证据。
3. 接Root Binder、活动账本完成/恢复与版本生命周期，然后按批准方式携带完整worker与桥；测实际APK增量，不能拿SDK压缩包或单库ZIP代替。
4. 最终真机集中验证加载、嵌入agent/helper、ART、取消/断连/并发回收；未通过不公布ready，不降级为常驻server或额外Python环境。
