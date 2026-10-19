package com.safemode.llconnect.data.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueServerScopeTest {

    private val serverA = "https://a.example.com/"
    private val serverB = "https://b.example.com/"

    @Test fun sameServerFlushes() {
        assertTrue(queuedForCurrentServer(serverA, serverA))
    }

    @Test fun differentServerIsHeld() {
        assertFalse(queuedForCurrentServer(serverA, serverB))
    }

    @Test fun legacyBlankEntryFlushesToCurrent() {
        // Entries queued before server tagging existed keep the old behavior.
        assertTrue(queuedForCurrentServer("", serverB))
    }

    @Test fun unknownCurrentServerDoesNotStrandEntries() {
        assertTrue(queuedForCurrentServer(serverA, ""))
    }
}
