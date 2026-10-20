package io.github.asagnc.aurora.runtime

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.net.toUri
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.R

/** Runs only for user-initiated, observable interactive Agent work. Never restarts or replays it. */
class AgentTaskService : Service() {
    private val host get() = (application as AuroraApplication).agentTasks
    private var runId: String? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent 运行任务", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("run")
        if (intent?.action == STOP) {
            if (id != null) host.stop(id)
            if (host.activeId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (id == null || host.activeId != id) {
            if (runId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        runId = id
        try {
            startForeground(NOTIFICATION, notification(id), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            if (!host.serviceReady(id)) stopSelf(startId)
        } catch (failure: Exception) {
            host.serviceFailed(id, failure)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun notification(id: String): Notification {
        val open = PendingIntent.getActivity(this, id.hashCode(), Intent(this, MainActivity::class.java).setData("aurora://agent/$id".toUri()).putExtra("agentOwner", host.owner)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, id.hashCode(), Intent(this, AgentTaskService::class.java)
            .setAction(STOP).setData("aurora://agent/$id".toUri()).putExtra("run", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_agent_notification).setContentTitle("Aurora 正在执行任务")
            .setContentText("点击返回 Aurora，或停止当前任务")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
    }

    override fun onDestroy() {
        runId?.let(host::serviceLost)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL = "agent_runs"
        const val NOTIFICATION = 1001
        const val STOP = "io.github.asagnc.aurora.STOP_AGENT"
    }
}
