package com.prodm.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.prodm.app.data.AppSettings
import com.prodm.app.prodm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object Scheduler {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, ScheduleReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun apply(ctx: Context, s: AppSettings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (!s.scheduleOn) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.schedHour)
            set(Calendar.MINUTE, s.schedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext.prodm
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.settings.flow.first()
                app.core.startScheduledNow()
                Scheduler.apply(context, s)
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext.prodm
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.apply(context, app.settings.flow.first())
            } finally {
                pr.finish()
            }
        }
    }
}
