package com.safemode.llconnect.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.safemode.llconnect.data.settings.ConnectionConfig
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the once-a-day notification check. A self-rescheduling [OneTimeWorkRequest] fires at the
 * configured time; the worker calls [schedule] again at the end of each run to queue the next day,
 * which keeps the run pinned to the chosen clock time (a [androidx.work.PeriodicWorkRequest] would
 * drift). Timing can still slip under Doze — acceptable for a daily, non-critical reminder.
 */
object NotificationScheduler {
    private const val WORK_NAME = "daily_notifications"

    /**
     * Enqueues the next daily run at [ConnectionConfig.odometerReminderTime], or cancels the work
     * when no notification type is enabled. Safe to call repeatedly (replaces any pending run).
     */
    fun schedule(context: Context, config: ConnectionConfig) {
        val workManager = WorkManager.getInstance(context)
        if (!config.anyNotificationEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInitialDelay(delayToNextMillis(config.odometerReminderTime), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Millis from now until the next occurrence of [timeHhMm] ("HH:mm"); tomorrow if already past. */
    private fun delayToNextMillis(timeHhMm: String): Long {
        val time = parseTime(timeHhMm)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }

    private fun parseTime(timeHhMm: String): LocalTime =
        runCatching { LocalTime.parse(timeHhMm) }.getOrDefault(LocalTime.of(18, 0))
}
