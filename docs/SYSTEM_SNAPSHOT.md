# 结构化系统快照

system_snapshot已注册Agent，默认读取hardware/memory/storage/battery/root_connection，也可传唯一的sections子集。复用现有工具账本，不另建服务/轮询器/执行后端，不运行shell、不申请新权限、不触发Root连接。

硬件/系统信息使用Build和Os.uname，不返回serial、Android ID或已安装应用清单。内存为ActivityManager.getMemoryInfo的近似系统总量/可用量/低内存阈值，不是本进程RSS或性能基准。存储为App data所在文件系统的StatFs总量/availableBytes，不能保证配额或实际分配成功。电池容量/电流来自BatteryManager，unsupported保持null，电流保留平台符号/微安单位，不用其推导任务耗电。Root仅报告缓存的连接phase，runtimeQualified保持null，不把连接等同Frida/SDK/代理/抓包资格。

每次记录elapsedRealtimeMs开始/结束区间、atomic=false；各部分失败显示available=false，取消继续抛出。没有全局原子采样承诺。新增2项Android普通UID用例只编译，尚未执行：实际API可用性/时间区间/无Root请求，以及重复/未知/空字段在采样前拒绝。此入口不宣称已实现任意系统写API、包管理或权限管理。

官方API依据：
- https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
- https://developer.android.com/reference/android/os/StatFs
- https://developer.android.com/reference/android/os/BatteryManager
