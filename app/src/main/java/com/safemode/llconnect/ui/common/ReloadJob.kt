package com.safemode.llconnect.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
 * Call from the main thread.
 */
class ReloadJob {
    private var job: Job? = null

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun launch(scope: CoroutineScope, refresh: Boolean = false, block: suspend () -> Unit) {
        job?.cancel()
        if (refresh) _refreshing.value = true
        // Started lazily so `job` already points at this launch when its finally block runs.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                if (job === coroutineContext[Job]) _refreshing.value = false
            }
        }
        job = next
        next.start()
    }
}
