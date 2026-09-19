package com.safemode.llconnect.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.safemode.llconnect.ui.common.UiState
import com.safemode.llconnect.ui.common.refreshWhenServerReachable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VehiclesViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<List<Vehicle>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Vehicle>>> = _state.asStateFlow()

    private val _baseUrl = MutableStateFlow<String?>(null)
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        // React to connection changes (e.g. after saving Settings) by reloading automatically.
        viewModelScope.launch {
            settings.config
                .map { ConnectionKey.from(it) to it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, config) -> reload(config) }
        }
        refreshWhenServerReachable { reloadSilently() }
    }

    /** Manual retry using the latest saved config. */
    fun load() {
        viewModelScope.launch { reload(settings.config.first()) }
    }

    /** Quietly re-fetch (e.g. when returning to the screen after add/edit/delete). */
    fun reloadSilently() {
        viewModelScope.launch { reload(settings.config.first(), showLoading = false) }
    }

    /** Pull-to-refresh: reload from the server, showing the refresh indicator. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(settings.config.first(), showLoading = false)
            _refreshing.value = false
        }
    }

    private suspend fun reload(config: ConnectionConfig, showLoading: Boolean = true) {
        // Make sure the networking layer is on the newest config before we call it.
        Graph.apiProvider.updateConfig(config)
        _baseUrl.value = config.baseUrl()
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getVehicles().fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Failed to load vehicles.") },
        )
    }

    /** Fields that, when changed, should trigger a reload. */
    private data class ConnectionKey(
        val scheme: String,
        val host: String,
        val port: String,
        val apiKey: String,
        val authMode: String,
        val basicUsername: String,
        val basicPassword: String,
        val cultureInvariant: Boolean,
    ) {
        companion object {
            fun from(c: ConnectionConfig) = ConnectionKey(
                c.scheme, c.host, c.port, c.apiKey, c.authMode.name,
                c.basicUsername, c.basicPassword, c.cultureInvariant,
            )
        }
    }
}
