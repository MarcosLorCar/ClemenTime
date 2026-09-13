package com.marcoslorcar.clementime.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT_UPDATE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                ScheduleWidgetUtils.updateWidgetFromBroadcast(context, pendingResult)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleWidgetUtils.scheduleMidnightAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ScheduleWidgetUtils.cancelMidnightAlarm(context)
    }

    companion object {
        const val ACTION_MIDNIGHT_UPDATE = "com.marcoslorcar.clementime.action.MIDNIGHT_WIDGET_UPDATE"
    }
}
