package com.safemode.llconnect.data.notifications

/**
 * Whether a reminder should trigger a notification, based on LubeLogger's server-computed urgency.
 * Notify on urgent / very urgent / past due / overdue; stay quiet for not-urgent and not-due.
 * Casing and spacing vary across server versions, so this normalizes before matching.
 */
fun isReminderDue(urgency: String?): Boolean {
    val u = urgency?.lowercase()?.replace(" ", "") ?: return false
    if (u == "noturgent" || u == "notdue") return false
    return "urgent" in u || "pastdue" in u || "overdue" in u
}
