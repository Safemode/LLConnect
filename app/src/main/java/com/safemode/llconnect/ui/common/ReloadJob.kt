package com.safemode.llconnect.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs at most one reload at a time: each new launch cancels the one still in flight, so a slow
 * earlier load can't finish after a newer one and overwrite fresh data with stale. Pair with the
 * cancellation-safe repository calls, which let the cancelled load actually stop instead of
 * completing and writing state anyway.
 *
 * Also owns the pull-to-refresh indicator. Only the newest reload clears it, so a cancelled
 * refresh can't hide the spinner of the one that replaced it, and a reload that replaces a
 * running refresh (a reconnect, returning to the screen) keeps the spinner up until it finishes.
 * The spinner stays up for at least [minRefreshMillis]: a nearby server can answer within a frame
 * or two, and the spinner would vanish before it could be seen. The new data shows as soon as it
 * arrives; only the spinner waits.
 * Call from the main thread.
 */
class ReloadJob(private val minRefreshMillis: Long = MIN_REFRESH_MILLIS) {
    private var job: Job? = null
    private var refreshStartedAt = 0L

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun launch(scope: CoroutineScope, refresh: Boolean = false, block: suspend () -> Unit) {
        job?.cancel()
        if (refresh && !_refreshing.value) {
            _refreshing.value = true
            refreshStartedAt = nowMillis()
        }
        // Started lazily so `job` already points at this launch when its finally block runs.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
                if (_refreshing.value) {
                    val remaining = minRefreshMillis - (nowMillis() - refreshStartedAt)
                    if (remaining > 0) delay(remaining)
                }
            } finally {
                if (job === coroutineContext[Job]) _refreshing.value = false
            }
        }
        job = next
        next.start()
    }

    private fun nowMillis() = System.nanoTime() / 1_000_000

    private companion object {
        const val MIN_REFRESH_MILLIS = 600L
    }
}
