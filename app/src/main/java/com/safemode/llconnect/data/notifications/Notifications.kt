package com.safemode.llconnect.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safemode.llconnect.MainActivity
import com.safemode.llconnect.R

/**
 * Builds and posts the app's notifications. Three channels (odometer / reminders / queue) so each
 * can be tuned independently in system settings. Each notification taps through to [MainActivity]
 * carrying a [EXTRA_NAV_TARGET] route hint that the UI resolves on open.
 */
object Notifications {

    const val EXTRA_NAV_TARGET = "nav_target"
    const val TARGET_ODOMETER = "odometer"
    const val TARGET_REMINDERS = "reminders"
    const val TARGET_QUEUE = "queue"

    private const val CHANNEL_ODOMETER = "odometer"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_QUEUE = "queue"

    private const val ID_ODOMETER = 1001
    private const val ID_REMINDERS = 1002
    private const val ID_QUEUE = 1003

    /** Whether the app is currently allowed to post notifications. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(CHANNEL_ODOMETER, "Odometer reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Daily reminder to log an odometer reading" },
            NotificationChannel(CHANNEL_REMINDERS, "Reminders due", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Vehicle reminders that are urgent or overdue" },
            NotificationChannel(CHANNEL_QUEUE, "Queue uploads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Results of uploading records queued while offline" },
        )
        manager.createNotificationChannels(channels)
    }

    fun postOdometerReminder(context: Context) {
        post(
            context,
            id = ID_ODOMETER,
            channel = CHANNEL_ODOMETER,
            target = TARGET_ODOMETER,
            title = "Log an odometer reading",
            text = "Tap to record today's odometer reading.",
        )
    }

    /** One grouped notification summarizing the due/overdue reminders. */
    fun postReminders(context: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        val title = if (lines.size == 1) "1 reminder due" else "${lines.size} reminders due"
        val style = NotificationCompat.InboxStyle().also { style -> lines.take(6).forEach { style.addLine(it) } }
        post(
            context,
            id = ID_REMINDERS,
            channel = CHANNEL_REMINDERS,
            target = TARGET_REMINDERS,
            title = title,
            text = lines.first(),
            style = style,
        )
    }

    fun postQueueResult(context: Context, uploaded: Int, failed: Int) {
        if (uploaded == 0 && failed == 0) return
        val parts = buildList {
            if (uploaded > 0) add("$uploaded uploaded")
            if (failed > 0) add("$failed failed")
        }
        post(
            context,
            id = ID_QUEUE,
            channel = CHANNEL_QUEUE,
            target = TARGET_QUEUE,
            title = "Queued records synced",
            text = parts.joinToString(", "),
        )
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        target: String,
        title: String,
        text: String,
        style: NotificationCompat.Style? = null,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET, target)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .apply { if (style != null) setStyle(style) }
            .build()

        // Permission is re-checked by canPost() above; the try guards a revoke race on API 33+.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
