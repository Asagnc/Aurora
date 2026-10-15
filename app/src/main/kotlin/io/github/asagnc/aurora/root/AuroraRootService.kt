package io.github.asagnc.aurora.root

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.system.Os
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

class AuroraRootService : RootService() {
    override fun onBind(intent: Intent): IBinder = object : IRootControl.Stub() {
        override fun snapshot(): Bundle {
            check(Binder.getCallingUid() == applicationInfo.uid) { "Caller is outside Aurora" }
            check(Process.myUid() == 0) { "Service has no root identity" }
            val kernel = Os.uname()
            return Bundle().apply {
                putInt("protocol", 1)
                putInt("uid", Process.myUid())
                putInt("pid", Process.myPid())
                putInt("sdk", Build.VERSION.SDK_INT)
                putString("kernel", kernel.release.take(256))
                putString("architecture", kernel.machine.take(64))
                putString("selinux", runCatching { File("/proc/self/attr/current").readText().trim().take(128) }.getOrDefault("不可读取"))
            }
        }
    }
}
