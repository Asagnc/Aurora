# Frida 17.26.2 主包接入提议（独立资格后决策）

本文件保留晋升时的比较与独立证据。用户已批准17.26.2，现已接入main并完成当前启动主包九项复验；releaseQualified=false。主包当前结果见evidence/0.1.40/frida-1726-main-integration.json，旧独立worker与新主包worker分别记录。

## 比较与建议

| 方案 | 已有证据与限制 | 结论 |
| --- | --- | --- |
| 继续17.24.3 | 解除限制后的组合已多次通过，但历史PC0x320未确认根因，正式入口关闭 | 保留不可变审查基线 |
| 17.25.0 mapper | 独立包首轮六项均失败，新agent触发libc定位SIGABRT；失败记录已恢复清账 | 不接入 |
| 当前稳定17.26.2 | 包含17.25.1的Android16/Bionic命名空间初始化修复及后续映射修正；固定worker、现有Binder/guard/账本/最小策略路线取得下述证据 | 推荐接入main做主包集成验收 |

不自行维护上游补丁，不引入第二个daemon、数据表或Root授权后端。保留现有最小适配与Enforcing；独立结果不证明无需适配，也不证明旧0x320的因果修复。

## 可审查的实际结果

- 独立身份9个去重用例、22次通过执行；包括Root native/ART、普通App attach、早期spawn、resume前异常、取消、Root/worker/App死亡恢复。完整正确参数组合131.554秒通过，用户重启解锁后的完整六项132.171秒再通过，待恢复票据复核0。保留首轮两次参数前置失败，App自杀prepare不计通过。
- SDK固定61,096,720字节/SHA256 3df0d73af88a7f81faa8f09f8d8c73ee83498c04d21014d54830463f250e340f；worker89,537,880字节/SHA256 05abea25b50257732963a0f22ce2f0efd66812a68c91f93198260dc1afb9bd20。普通Shell实际版本、ELF依赖及外层16KiB通过；64位agent标注NDK r29/14206865，与原运行库材料对应。32位辅助载荷不作16KiB运行资格声明。
- 新core/Gum直接wrap、releng、Vala及实际封入的freestanding运行库补齐14份固定来源，60,368,478字节；GitHub固定tree的许可blob逐项核对。原65份材料保留为基线/编译器来源，共79项保守输入超集。releng明确禁用GLib sysprof，文档默认关闭；Apple专属新libcxx不当Android运行依赖。不是总体法律闭包或完整上游SDK源码重编译。
- app/build/outputs/frida-mapper-1726-review-inputs/frida-mapper-17.26.2-source-materials.zip 共477,598,734字节/SHA256 381a1f4013eb54cf8b3009024ca63976eacc1a3cf7f7a40578dea1c80ec6c8c0；从中提取并与当前可信源码逐字节核对，离线重新编译得到同一05abea25 worker。
- 主包当前生产源码与85b790e相同；1636479完整CI42571394022五job成功，主包新启动SDK构建/导出/JNI100.062秒通过。该CI仍编译生产17.24.3，不能当作17.26.2主包集成CI。

精简证据：evidence/0.1.40/frida-1726-qualification.json、frida-1726-source-review.json；固定增量和wrap清单见tools/frida-mapper-qualification/source-delta-17.26.2.json、runtime-inputs-17.26.2.json。

## 建议接入边界

确认后才更新生产SDK固定字节、精确归档库存/来源目录和版本描述，保留protocol/guard/schema与旧unknown记录，不重放、不强删。重新构建主包、完整CI、主包当前身份/源材料与设备矩阵；规定资格未完成时正式门控保持关闭。当前未签名完整审查包作为旧版保留，新产品字节必须重打精确对应包。正式签名仍等用户配置，UI和付费模型不提前混入。

来源：[17.25.1官方Android修复](https://frida.re/news/2026/11/01/frida-17-25-1-released/)、[17.25.2映射修正](https://frida.re/news/2026/11/02/frida-17-25-2-released/)、[17.26.2官方发布](https://frida.re/news/2026/11/07/frida-17-26-2-released/)。

2026-11-09用户已明确采用17.26.2，继续主包集成验收。后续例行接入/验证直接推进，正式门控仍待规定验收。
