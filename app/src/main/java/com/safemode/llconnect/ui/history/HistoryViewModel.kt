package com.safemode.llconnect.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.ActivityItem
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<ActivityItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ActivityItem>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        val config = settings.config.first()
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getActivity().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load activity.") },
        )
    }
}
