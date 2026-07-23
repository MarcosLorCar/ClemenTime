package com.github.marcoslorcar.clementime.ui.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

object ScheduleWidgetUtils {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateWidget(context: Context) {
        scope.launch {
            // Small delay to ensure DB transaction is fully finalized before widget read
            kotlinx.coroutines.delay(500.milliseconds)
            ScheduleWidget().updateAll(context)
        }
    }
}
