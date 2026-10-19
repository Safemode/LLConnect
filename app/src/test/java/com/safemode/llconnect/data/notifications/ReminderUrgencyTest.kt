package com.safemode.llconnect.data.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderUrgencyTest {

    @Test fun urgentVariantsAreDue() {
        assertTrue(isReminderDue("Urgent"))
        assertTrue(isReminderDue("Very Urgent"))
        assertTrue(isReminderDue("veryurgent"))
        assertTrue(isReminderDue("Past Due"))
        assertTrue(isReminderDue("Overdue"))
    }

    @Test fun calmVariantsAreNotDue() {
        assertFalse(isReminderDue("Not Urgent"))
        assertFalse(isReminderDue("notdue"))
        assertFalse(isReminderDue(null))
        assertFalse(isReminderDue(""))
    }
}
