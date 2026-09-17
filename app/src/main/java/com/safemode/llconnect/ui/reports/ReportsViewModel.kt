package com.safemode.llconnect.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.CostReport
import com.safemode.llconnect.ui.common.DateRange
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReportsViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<CostReport>>(UiState.Loading)
    val state: StateFlow<UiState<CostReport>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _range = MutableStateFlow(DateRange.ALL)
    val range: StateFlow<DateRange> = _range.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    fun setRange(range: DateRange) {
        if (range == _range.value) return
        _range.value = range
        load()
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
        val (start, end) = _range.value.bounds()
        _state.value = repository.getCostReport(start, end).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to build report.") },
        )
    }
}
