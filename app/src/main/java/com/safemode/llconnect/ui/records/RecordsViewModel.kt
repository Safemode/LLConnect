package com.safemode.llconnect.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.RecordRow
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordsViewModel(
    val area: RecordArea,
    private val vehicleId: String,
) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<List<RecordRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<RecordRow>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    /** Silent reload (e.g. when returning to the screen after add/edit/delete). */
    fun reloadSilently() {
        viewModelScope.launch { reload(showLoading = false) }
    }

    /** Pull-to-refresh: reload without replacing the list with a full-screen spinner. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
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
