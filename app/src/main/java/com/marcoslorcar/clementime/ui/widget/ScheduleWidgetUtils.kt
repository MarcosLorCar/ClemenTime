package com.marcoslorcar.clementime.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object ScheduleWidgetUtils {
    private const val TAG = "ScheduleWidgetUtils"
    const val MIDNIGHT_ALARM_REQUEST_CODE = 1001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    internal suspend fun runSafely(
        onFailure: (Exception) -> Unit = { Log.w(TAG, "Widget update failed", it) },
        action: suspend () -> Unit
    ) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun updateWidget(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            runSafely {
                ScheduleWidget().updateAll(appContext)
                scheduleMidnightAlarm(appContext)
            }
        }
    }

    fun updateWidgetFromBroadcast(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                runSafely {
                    ScheduleWidget().updateAll(appContext)
                    scheduleMidnightAlarm(appContext)
                }
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Failed to finish broadcast pendingResult", e)
                }
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule widget midnight alarm", e)
        }
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel widget midnight alarm", e)
        }
    }
}
