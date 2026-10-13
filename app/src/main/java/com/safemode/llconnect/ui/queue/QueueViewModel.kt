package com.safemode.llconnect.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.queue.QueuedEntry
import com.safemode.llconnect.data.remote.ServerReachability
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Queue screen. All state comes straight from [Graph.offlineQueue] (no server fetch of
 * its own); the actions just delegate to it.
 */
class QueueViewModel : ViewModel() {
    private val queue = Graph.offlineQueue

    val entries: StateFlow<List<QueuedEntry>> = queue.entries
    val flushing: StateFlow<Boolean> = queue.flushing
    val serverState: StateFlow<ServerReachability> = Graph.serverStatus.state

    /** Attempt to submit the queue now (no-op if the server is still unreachable). */
    fun retryNow() {
        viewModelScope.launch { queue.flush() }
    }

    fun remove(id: String) {
        viewModelScope.launch { queue.remove(id) }
    }

    fun clear() {
        viewModelScope.launch { queue.clear() }
    }
}
