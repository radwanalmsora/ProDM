package com.prodm.app.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prodm.app.MainActivity
import com.prodm.app.R
import com.prodm.app.prodm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadService : Service() {
    companion object {
        const val NOTIF_ID = 1
        const val ACTION_PAUSE_ALL = "com.prodm.app.PAUSE_ALL"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val core = prodm.core
        if (intent?.action == ACTION_PAUSE_ALL) core.pauseAll()
        startForeground(
            NOTIF_ID,
            build(core.active.value.size, 0, 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (wake == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "prodm:download")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (observer == null) observer = scope.launch { observe(core) }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun observe(core: DownloadCore) {
        val nm = NotificationManagerCompat.from(this)
        combine(core.active, core.live) { a, l -> a to l }.collect { (active, live) ->
            if (active.isEmpty()) {
                delay(2500)
                if (core.active.value.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            } else {
                var done = 0L
                var total = 0L
                var speed = 0L
                for (id in active) {
                    val l = live[id] ?: continue
                    speed += l.speed
                    if (l.total > 0) {
                        done += l.downloaded
                        total += l.total
                    }
                }
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                runCatching { nm.notify(NOTIF_ID, build(active.size, speed, pct, total <= 0)) }
            }
        }
    }

    private fun build(count: Int, speed: Long, pct: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pause = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, DownloadCore.CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_active, count, fmtSpeed(speed)))
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_pause_all), pause)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
