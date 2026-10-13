package com.safemode.llconnect.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.ReminderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The once-a-day notification check (scheduled by [NotificationScheduler]). Posts the odometer
 * reminder (local), then — if enabled and the server is reachable — notifies about due/overdue
 * reminders and flushes the offline queue, reporting the result. Always reschedules the next run.
 */
class DailyNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = Graph.settingsRepository.config.first()
        try {
            if (config.notifyOdometerDaily) {
                Notifications.postOdometerReminder(applicationContext)
            }

            val needsNetwork = (config.notifyReminders || config.notifyQueue) && config.isConfigured
            if (needsNetwork) {
                // The foreground reachability probe doesn't run in the background, so establish
                // reachability here before any networked call (a direct probe that bypasses the cache).
                Graph.apiProvider.updateConfig(config)
                val reachable = withContext(Dispatchers.IO) { Graph.apiProvider.probeReachable() }
                Graph.serverStatus.report(reachable)

                if (reachable && config.notifyReminders) {
                    Graph.repository.getAllReminders().onSuccess { reminders ->
                        val lines = reminders.filter { isDue(it.urgency) }.map(::lineFor)
                        Notifications.postReminders(applicationContext, lines)
                    }
                }
                if (reachable && config.notifyQueue) {
                    val outcome = Graph.offlineQueue.flush()
                    Notifications.postQueueResult(applicationContext, outcome.uploaded, outcome.failed)
                }
            }
        } finally {
            // Queue tomorrow's run regardless of what happened this time.
            NotificationScheduler.schedule(applicationContext, config)
        }
        return Result.success()
    }

    private fun lineFor(item: ReminderItem): String {
        val vehicle = item.vehicleName.ifBlank { "Vehicle" }
        return "$vehicle — ${item.description}"
    }

    /** LubeLogger's server-computed urgency: notify on urgent / very urgent / past due. */
    private fun isDue(urgency: String?): Boolean {
        val u = urgency?.lowercase()?.replace(" ", "") ?: return false
        if (u == "noturgent" || u == "notdue") return false
        return "urgent" in u || "pastdue" in u || "overdue" in u
    }
}
