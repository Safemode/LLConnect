package com.safemode.llconnect.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.RecordRow
import com.safemode.llconnect.ui.common.ReloadJob
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordsViewModel(
    val area: RecordArea,
    private val vehicleId: String,
) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<List<RecordRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<RecordRow>>> = _state.asStateFlow()

    private val reloads = ReloadJob()
    val refreshing: StateFlow<Boolean> = reloads.refreshing

    init {
        load()
        refreshWhenServerReachable { reloadSilently() }
    }

    fun load() {
        reloads.launch(viewModelScope) { reload(showLoading = true) }
    }

    /** Silent reload (e.g. when returning to the screen after add/edit/delete). */
    fun reloadSilently() {
        reloads.launch(viewModelScope) { reload(showLoading = false) }
    }

    /** Pull-to-refresh: reload without replacing the list with a full-screen spinner. */
    fun refresh() {
        reloads.launch(viewModelScope, refresh = true) {
            Graph.probeIfUnreachable()
            reload(showLoading = false)
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getRecords(area, vehicleId).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { failure ->
                // A background refresh that fails shouldn't wipe already-loaded data.
                val current = _state.value
                if (!showLoading && current is UiState.Success) current
                else UiState.Error(failure.message ?: "Failed to load records.")
            },
        )
    }
}
