package com.marcoslorcar.clementime.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.ScheduleDao
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import com.marcoslorcar.clementime.utils.IcsExporter
import com.marcoslorcar.clementime.work.ScheduleSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject

data class MoreUiState(
    val themeMode: String = "system",
    val appLanguage: String = "en",
    val scrollableTabs: Boolean = false,
    val showNowLine: Boolean = true,
    val nowLineStyle: String = "discrete",
    val highContrast: Boolean = false,
    val selectedTheme: String = "clementine",
    val githubRepoBaseUrl: String = SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL,
    val onboardingTooltipsEnabled: Boolean = true,
    val scheduleNotificationsEnabled: Boolean = false,
    val dayStartTime: LocalTime = LocalTime.of(8, 30),
    val dayEndTime: LocalTime = LocalTime.of(21, 30)
)

sealed interface ExportStatus {
    object Exporting : ExportStatus
    data class Success(val message: String) : ExportStatus
    data class Error(val error: String) : ExportStatus
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleDao: ScheduleDao,
    private val jsonScheduleParser: JsonScheduleParser,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _appLanguage = MutableStateFlow(getCurrentLanguage())

    val uiState: StateFlow<MoreUiState> = combine(
        settingsRepository.themeFlow,
        settingsRepository.scrollableTabsFlow,
        settingsRepository.showNowLineFlow,
        settingsRepository.nowLineStyleFlow,
        settingsRepository.highContrastFlow,
        settingsRepository.selectedThemeFlow,
        settingsRepository.githubRepoBaseUrlFlow,
        settingsRepository.onboardingTooltipsEnabledFlow,
        settingsRepository.scheduleNotificationsEnabledFlow,
        settingsRepository.dayStartHourFlow,
        settingsRepository.dayStartMinuteFlow,
        settingsRepository.dayEndHourFlow,
        settingsRepository.dayEndMinuteFlow,
        _appLanguage
    ) { args: Array<Any?> ->
        MoreUiState(
            themeMode = args[0] as String,
            scrollableTabs = args[1] as Boolean,
            showNowLine = args[2] as Boolean,
            nowLineStyle = args[3] as String,
            highContrast = args[4] as Boolean,
            selectedTheme = args[5] as String,
            githubRepoBaseUrl = args[6] as String,
            onboardingTooltipsEnabled = args[7] as Boolean,
            scheduleNotificationsEnabled = args[8] as Boolean,
            dayStartTime = LocalTime.of(args[9] as Int, args[10] as Int),
            dayEndTime = LocalTime.of(args[11] as Int, args[12] as Int),
            appLanguage = args[13] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MoreUiState(appLanguage = getCurrentLanguage())
    )

    private fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) {
            locales.get(0)?.language ?: "en"
        } else {
            val systemLanguage = Locale.getDefault().language
            if (systemLanguage == "es") "es" else "en"
        }
    }

    fun setThemeMode(theme: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun setAppLanguage(lang: String) {
        val localeList = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localeList)
        _appLanguage.value = lang
    }

    fun setScrollableTabs(scrollable: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScrollableTabs(scrollable)
        }
    }


    fun setShowNowLine(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowNowLine(show)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setNowLineStyle(style: String) {
        viewModelScope.launch {
            settingsRepository.setNowLineStyle(style)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHighContrast(enabled)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }


    fun setSelectedTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setOnboardingTooltipsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOnboardingTooltipsEnabled(enabled)
        }
    }

    fun setScheduleNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScheduleNotificationsEnabled(enabled)
            if (enabled) {
                ScheduleSyncWorker.enqueuePeriodicWork(context)
            } else {
                ScheduleSyncWorker.cancelWork(context)
                settingsRepository.setHasPendingScheduleUpdate(false)
                settingsRepository.setAffectedSubjectIds(emptySet())
            }
        }
    }

    fun setDayStartTime(time: LocalTime) {
        viewModelScope.launch {
            val snappedTime = snapTo30Minutes(time)
            settingsRepository.setDayStartTime(snappedTime.hour, snappedTime.minute)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    fun setDayEndTime(time: LocalTime) {
        viewModelScope.launch {
            val snappedTime = snapTo30Minutes(time)
            settingsRepository.setDayEndTime(snappedTime.hour, snappedTime.minute)
            ScheduleWidgetUtils.updateWidget(context)
        }
    }

    private fun snapTo30Minutes(time: LocalTime): LocalTime {
        return when (time.minute) {
            in 0..14 -> time.withMinute(0)
            in 15..44 -> time.withMinute(30)
            else -> time.plusHours(1).withMinute(0)
        }
    }

    fun setGithubRepoBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setGithubRepoBaseUrl(url)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun simulateUpdate() {
        viewModelScope.launch {
            val activeSubjects = scheduleDao.getAllSubjectsWithSlots().firstOrNull()?.filter { it.subject.isActive }
            if (activeSubjects.isNullOrEmpty()) return@launch

            // Pick a random subject to "update"
            val subjectToUpdate = activeSubjects.random()
            val currentAffected = settingsRepository.affectedSubjectIdsFlow.first()
            
            settingsRepository.setAffectedSubjectIds(currentAffected + subjectToUpdate.subject.id.toString())
            settingsRepository.setHasPendingScheduleUpdate(true)
            
            // Trigger a simple system notification for testing UI flow
            val channelId = "schedule_updates"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    context.getString(R.string.schedule_update_notification_channel_name),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(context, com.marcoslorcar.clementime.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle(context.getString(R.string.schedule_update_notification_title))
                .setContentText(context.getString(R.string.schedule_update_notification_text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                @android.annotation.SuppressLint("MissingPermission")
                notificationManager.notify(1001, builder.build())
            } catch (_: SecurityException) {}
        }
    }

    fun exportData(context: Context, customUri: Uri, onResult: (ExportStatus) -> Unit) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val subjects = scheduleDao.getAllSubjectsWithSlots().first()
                val jsonString = jsonScheduleParser.exportToJson("ClemenTime Export", subjects)
                
                context.contentResolver.openOutputStream(customUri)?.use { out ->
                    out.write(jsonString.toByteArray())
                }
                onResult(ExportStatus.Success(context.getString(R.string.export_success_local)))
            } catch (e: Exception) {
                onResult(ExportStatus.Error(context.getString(R.string.export_error_prefix, e.localizedMessage)))
            }
        }
    }

    fun exportFullYearToIcs(
        context: Context,
        customUri: Uri,
        s1Start: LocalDate,
        s1End: LocalDate,
        s2Start: LocalDate,
        s2End: LocalDate,
        onResult: (ExportStatus) -> Unit
    ) {
        viewModelScope.launch {
            onResult(ExportStatus.Exporting)
            try {
                val s1Subjects = scheduleDao.getActiveSubjectsWithSlotsBySemester(1).first()
                val s2Subjects = scheduleDao.getActiveSubjectsWithSlotsBySemester(2).first()

                fun filterSlots(subjects: List<com.marcoslorcar.clementime.data.SubjectWithSlots>): List<com.marcoslorcar.clementime.data.SubjectWithSlots> {
                    return subjects.map { sws ->
                        sws.copy(
                            slots = sws.slots.filter { slot ->
                                !slot.isIgnored && (
                                    slot.entryType == EntryType.THEORY || 
                                    sws.subject.selectedLabGroup == null ||
                                    slot.labGroupName == sws.subject.selectedLabGroup
                                )
                            }
                        )
                    }
                }

                val semesters = listOf(
                    IcsExporter.SemesterExportData(filterSlots(s1Subjects), s1Start, s1End),
                    IcsExporter.SemesterExportData(filterSlots(s2Subjects), s2Start, s2End)
                )

                val icsString = IcsExporter.generateIcsContent(semesters)
                
                // "wt" truncates: plain "w" leaves trailing bytes from a longer previous
                // export after END:VCALENDAR, producing an invalid file.
                val stream = context.contentResolver.openOutputStream(customUri, "wt")
                if (stream == null) {
                    onResult(ExportStatus.Error(context.getString(R.string.export_error_prefix, "")))
                    return@launch
                }
                stream.use { out ->
                    out.write(icsString.toByteArray(Charsets.UTF_8))
                }
                onResult(ExportStatus.Success(context.getString(R.string.export_success_local)))
            } catch (e: Exception) {
                onResult(ExportStatus.Error(context.getString(R.string.export_error_prefix, e.localizedMessage)))
            }
        }
    }
}
