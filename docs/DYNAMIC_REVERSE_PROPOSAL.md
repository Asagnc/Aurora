# 动态逆向执行方案（待用户确认，2026-10-23）

目标：Agent能在Root设备枚举目标、附加或启动应用、运行Frida脚本、读取结构化事件并停止会话。静态JADX/Apktool不能代替此功能。本文件是新增运行器/组件的选择，不代表已实现或已经通过Android16资格。

## 推荐分工

采用官方Frida 17.24.3 Android ARM64 frida-core devkit构建Aurora独立原生Root任务worker，直接使用本机设备API；复用已有任务监督、截止、取消、日志与会话产物。Aurora应用只传结构化控制和选定脚本，不把注入引擎装入UI进程。先做原生枚举/自有目标资格，再开放attach/spawn/script/消息；停止必须卸载脚本、detach并关闭worker，不常驻Frida服务。

原生脚本用Frida自带QuickJS；Java脚本显式组合官方frida-java-bridge 7.0.13。Frida17已将Java桥从GumJS移出，不能沿用“Java全局对象自动存在”的旧脚本假设。桥在开发/CI时固定来源、打包成可加载脚本，不要求手机另装Node/npm/Python。客户端、注入引擎和桥作为一个固定兼容组合验证，不能各自随意热更新。

首版worker作为ARM64打包原生组件由签名APK携带，使用现有nativeLibraryDir/Root执行通道；不为普通非Root下载代码再exec、不引入新的远程插件信任链。代价是安装包/安装空间增大；devkit下载量不等于最终APK增量，必须先实际构建、裁剪无用符号并测量压缩包/安装/运行RSS。未达到资格或体积不可接受时先呈现证据再调整布局，不静默改为常驻server或追加另一套Linux Python环境。

| 路径 | 优点 | 成本与结论 |
| --- | --- | --- |
| 原生frida-core独立Root worker | 直接本机API、少一层客户端/端口；故障不进入UI进程；无常驻server | 新的原生构建与任务收尾需要资格；APK体积会增加。推荐 |
| 官方frida-server + Python/frida-tools | 主流CLI成熟，脚本生态好 | server与客户端版本配套，另有Python及通信生命周期；默认server监听localhost TCP。可按需运行但不是零常驻成本，首版不选 |
| server + 原生客户端 | 去掉Python，成熟远端协议 | 仍需维护两个进程、端口/认证/重连与版本；保留未来明确远程设备需求再评估 |
| 直接JNI接入UI/RootService进程 | 少worker启动 | OOM/崩溃/GLib事件循环与服务生命周期耦合更重，不选 |
| Gadget重打包 | 适用于可修改目标/无Root路径 | 改包/签名/安装与用户目标不同，且用户不需要昂贵非Root替代，首版不选 |
| LSPosed专用模块 | Java hook启动路径有优势 | 强绑定额外模块与重启/作用域，不能替代通用native动态逆向；不作为Aurora必要依赖 |

这些是依据执行路径的工程判断，尚无Aurora手机延迟/RSS对比，不能宣传最快。

## 生命周期与输出

- 初始范围沿用现有单任务最长270秒，按需执行；长时间交互会话若必要，另核实事件驱动控制与持久恢复，不用无限后台进程伪装已完成。
- 首批使用指定PID或包名，记录捕获的PID/开始时间/目标身份；显式区分已有进程attach与由本任务spawn，退出不得杀死任意原有目标。spawn失败/取消时明确处理尚未resume的本任务子进程。
- Frida事件序号化、带上限写入会话日志，给Agent有界摘要/游标/丢弃计数，不把逐条高频hook事件送Binder/UI。通道满时降低/停止采集并报出，不无限积压。脚本异常、detach原因、目标退出、worker退出分别呈现，不用“worker已停止”冒充目标任务成功。
- unload/detach仅说明会话结束，不能承诺撤销脚本已造成的文件、内存或网络副作用。断连或崩溃没有足够证据时保留未知状态，不自动重放注入；必须验证注入端transport断开后的实际清理。
- 不全局关闭SELinux，不安装隐藏/绕过模块，不依赖特定手机；Root/ptrace/注入资格失败则清晰报出。Frida官方教程不是Android16或realmeUI兼容证明。
- 所有新增ABI/Root控制协议、脚本输入与结果预算在实现时做严格校验。对应源码、许可证与必要重链接材料需核实并随发布交付，不能只保留二进制。

## 已核实来源

2026-10-23官方GitHub releases/latest：Frida17.24.3，2026-10-19发布，非预发布。

- devkit: https://github.com/frida/frida/releases/download/17.24.3/frida-core-devkit-17.24.3-android-arm64.tar.xz
- 压缩60903232字节；发布API摘要SHA256 `0c688824a24949e7be77109fd2c7306bba60cb47f608789e141bb24cd23b1005`。这是来源目录信息，下载后仍须重算、检查归档/架构/闭包，不能称字节资格已完成。
- Java bridge官方npm分发7.0.13：`https://registry.npmjs.org/frida-java-bridge/-/frida-java-bridge-7.0.13.tgz`；integrity `sha512-YSyKjxbxKnSi3KSUy9vciOvTOuq0RRh9dkxzkQVEdfIIZlw20zE8D3Cq9eL2FDqUVj4YKas6Wf09kCjL5zbffg==`。不能只信SHA1字段；下载后核对integrity与固定源码/许可。桥与引擎兼容仍须实测。

官方依据：

- https://frida.re/docs/c-api/ — core注入与devkit。
- https://frida.re/docs/modes/ — injected/server/Gadget差异，server默认localhost:27042。
- https://frida.re/docs/bridges/ — Frida17桥变化与显式导入。
- https://frida.re/docs/javascript-api/ — QJS/V8、消息与脚本API。
- https://frida.re/docs/android/ — Android Root路径，不能据其旧测试设备描述推导新ROM可用。
- https://github.com/frida/frida/releases/tag/17.24.3

来源元数据保存在evidence/0.1.40/frida-official-release-20261023.json与frida-java-bridge-latest-20261023.json。

## 实施门槛

1. 固定摘要/安全展开/许可证/NDK链接、ABI与16KiB页兼容；记录实际APK体积，先验证Android ARM64 devkit的本机设备API可用，不把构建成功当可注入。
2. 自有原生目标：枚举/附加/函数hook/事件/错误/目标正常退出；自有Java目标：bridge available与方法hook。仅自有夹具，不拿其他用户应用做默认测试。
3. 取消、超时、Root服务死亡、目标死亡、spawn暂停阶段取消、日志风暴、两会话并行、删除会话、后台/熄屏。确认无失控worker/未resume目标/误杀原有目标。
4. 最终手机集中执行，量化空闲CPU、启动时间、RSS和事件吞吐；不保证所有ROM/加固目标成功。

## 其他剩余工作继续保留

本方案不批准radare2新运行器、ARM64 aapt2/JDK/AGP构建链或TLS代理。它们分别核实官方架构/来源/缓存/安装与任务布局后提出选择；不下载桌面x86工具充数。模型真实命中/费用测试仍等待用户允许实际连接；当前不发付费请求。
