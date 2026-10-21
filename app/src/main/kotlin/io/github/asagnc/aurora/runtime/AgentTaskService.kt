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
    private val terminals get() = (application as AuroraApplication).terminals
    private var runId: String? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent 运行任务", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("run")
        if (intent?.action == REFRESH) {
            if (host.activeId == null && !terminals.hasActive) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == STOP) {
            host.stop()
            terminals.stopAll()
            if (host.activeId == null && !terminals.hasActive) stopSelf(startId)
            return START_NOT_STICKY
        }
        val terminal = intent?.getStringExtra("terminal")
        if (terminal != null) {
            try {
                startForeground(NOTIFICATION, notification(host.activeId ?: terminal), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                if (!terminals.serviceReady(terminal) && host.activeId == null && !terminals.hasActive) stopSelf(startId)
            } catch (failure: Exception) { terminals.serviceFailed(terminal, failure) }
            return START_NOT_STICKY
        }
        if (id == null || host.activeId != id) {
            if (runId == null && !terminals.hasActive) stopSelf(startId)
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
        val open = PendingIntent.getActivity(this, id.hashCode(), Intent(this, MainActivity::class.java).setData("aurora://agent/$id".toUri()).putExtra("agentOwner", host.owner ?: terminals.activeOwner)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, id.hashCode(), Intent(this, AgentTaskService::class.java)
            .setAction(STOP).setData("aurora://agent/$id".toUri()).putExtra("run", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_agent_notification).setContentTitle("Aurora 正在执行任务")
            .setContentText("点击返回 Aurora，或停止当前任务")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "停止全部任务", stop).build()).build()
    }

    override fun onDestroy() {
        runId?.let(host::serviceLost)
        terminals.stopAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal fun refresh(context: android.content.Context) {
            // stopSelfResult uses the delivered start ID, preserving a newer pending start.
            runCatching { context.startService(Intent(context, AgentTaskService::class.java).setAction(REFRESH)) }
        }
        private const val REFRESH = "io.github.asagnc.aurora.REFRESH_TASKS"
        const val CHANNEL = "agent_runs"
        const val NOTIFICATION = 1001
        const val STOP = "io.github.asagnc.aurora.STOP_AGENT"
    }
}
