package com.safemode.llconnect.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.ReminderItem
import com.safemode.llconnect.ui.common.ReloadJob
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class RemindersViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<ReminderItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ReminderItem>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val reloads = ReloadJob()

    init {
        load()
        refreshWhenServerReachable { refresh() }
    }

    fun load() {
        reloads.launch(viewModelScope) { reload(showLoading = true) }
    }

    fun refresh() {
        reloads.launch(viewModelScope) {
            _refreshing.value = true
            try {
                reload(showLoading = false)
            } finally {
                _refreshing.value = false
            }
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
        _state.value = repository.getAllReminders().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load reminders.") },
        )
    }
}
