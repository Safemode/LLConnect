package com.safemode.llconnect.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs at most one reload at a time: each new launch cancels the one still in flight, so a slow
 * earlier load can't finish after a newer one and overwrite fresh data with stale. Pair with the
 * cancellation-safe repository calls, which let the cancelled load actually stop instead of
 * completing and writing state anyway.
 */
class ReloadJob {
    private var job: Job? = null

    fun launch(scope: CoroutineScope, block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }
}
