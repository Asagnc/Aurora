# Frida 最小策略适配实现（2026-10-30，验收门控）

沿用已批准的 FRIDA_ANDROID_POLICY_PROPOSAL。实现进入现有 worker / Root Binder；不增加 daemon、数据库、密码库或持久 Root 模块。正式启用由 FridaPolicyAvailability.releaseQualified 门控，当前 false；Debug 也只允许显式 qualification 请求执行验收。Agent 定义未增加策略变更动作。设置页显示验收状态，启用按钮在门控关闭时不可用。

## 执行和记录

- IRootControl 末尾追加 fridaPolicy，不改旧事务序号；snapshot 通过 fridaPolicyProtocol=1 协商，旧服务须重连。校验调用 App UID，从实际 Binder PID 读取 App 域，不接收用户指定的策略路径、Root/App 域或执行文件。
- 请求绑定完整 LinuxRootIdentity；Root 操作与 Frida 执行共用 Mutex，采集资格独立。每次显式启用先清除 native/ART 自测资格；失败、取消或当前策略摘要变化保持未确认，后续 Frida 必须显式重新验证。服务重连不自动继承策略资格。
- Root 私有 root-frida-policy/<boot>/before.policy 保存本次启动的原始策略，用于差异审计，永不作为整份回滚。新基线先通过纯编辑器，暂存文件 fsync，link 无覆盖发布，删除本次暂存再 fsync 目录；未知已有类型、坏基线、链接/多链接或失败状态不自动覆盖。旧 boot 基线保留，不用于新 boot 装载。
- native policy-plan / policy-verify 为纯编辑与审计。policy-activate / policy-attest 要求 Root 真/有效 UID、当前 boot、实际 Root 域、Enforcing、Root 只读稳定普通单链接输入。全量逆向审计必须只出现批准的专用类型、两属性及两组 file 权限。
- 新装载仅允许当前策略仍逐字节等于本次基线；已有精确已批准差异时只重新验证，不重复写入。紧邻写入前再次读取当前策略、boot 和 Enforcing；单次固定 selinuxfs/load 写入后，重新读取并逆向比较，核验专用 memfd 标签和目标域六项权限。失败不回滚，不将运输成功当资格。
- policy-qualify-reload 是 Root 专用显式资格入口，Binder/Agent 从不选择：仅对已有已批准差异复用同一写入/读回实现，验证实际内核接受与归一化。不是后台重放。
- 跨 Root 组件没有 CAS；不承诺消除读取与 load 之间的竞争。完整基线来自原始策略本身，固定 libsepol 的同 key xperm 顺序会翻转；只接受从原始策略派生的两个完整序列化字节像之一，不忽略字段或权限差异。

## 当前实测

worker 0ca9bd596c919af1c6483f7747591a67c3de74c0af33413119bde2f58aa69dd8：17 项纯编辑/输入/越权变体检查通过；11 项 native 启用/拒绝/已有策略不重写及实际单次重载检查通过。重载前后内核摘要一致，Enforcing=1，差异与 memfd 标签/权限验证通过。

本启动之前由资格程序添加了同一最小策略，因此验收只将先前保存且摘要固定的原始本 boot 策略复制到 Root 私有基线；产品不会自动接管无基线的未知已有 frida_memfd 类型。复制后仍用完整差异审计确认，非任意策略导入功能。

Binder 显式启用/实际 ART 探针/再次启用清零资格/Root 断连重连不继承/重新显式验证 1 项通过 19.426 秒。最终 worker 的五项 Frida（普通 App、Root native/ART、早期 spawn、resume 前异常、注入后取消）与两组普通 Binder/Linux 准备协议共21项通过115.127秒。最后加入发布门控与协议协商后需针对性复测。新 boot、实际管理器撤权、UI 入口及后续故障仍待；不得据此把正式门控改为 true。

固定源码/许可随65份Frida来源材料与自有代码打包，最终 worker 完全离线重链接相同；不是完整上游 SDK 重建。原始设备策略、完整上下文、nonce、任务/日志仅留 app/build。两个历史捕获前 unknown 不手动删除。正式签名、付费模型与第三方目标不参与本资格。
