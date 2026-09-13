package com.marcoslorcar.clementime.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

object ScheduleWidgetUtils {
    const val MIDNIGHT_ALARM_REQUEST_CODE = 1001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateWidget(context: Context?) {
        if (context == null) return
        try {
            scope.launch {
                // Small delay to ensure DB transaction is fully finalized before widget read
                kotlinx.coroutines.delay(500.milliseconds)
                ScheduleWidget().updateAll(context)
                scheduleMidnightAlarm(context)
            }
        } catch (_: Throwable) {}
    }

    fun updateWidgetFromBroadcast(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
        scope.launch {
            try {
                ScheduleWidget().updateAll(context)
                scheduleMidnightAlarm(context)
            } catch (_: Throwable) {
            } finally {
                try {
                    pendingResult?.finish()
                } catch (_: Throwable) {}
            }
        }
    }

    fun calculateNextMidnightMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        // Add 1 second so that LocalDate.now() inside the receiver is guaranteed to be the new day
        return nextMidnight.toInstant().toEpochMilli() + 1000L
    }

    fun scheduleMidnightAlarm(context: Context?) {
        if (context == null) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduleWidgetReceiver::class.java).apply {
                action = ScheduleWidgetReceiver.ACTION_MIDNIGHT_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerMillis = calculateNextMidnightMillis()
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerMillis, pendingIntent)
        } catch (_: Throwable) {}
    }

    fun cancelMidnightAlarm(context: Context?) {
        if (context == null) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduleWidgetReceiver::class.java).apply {
                action = ScheduleWidgetReceiver.ACTION_MIDNIGHT_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Throwable) {}
    }
}
