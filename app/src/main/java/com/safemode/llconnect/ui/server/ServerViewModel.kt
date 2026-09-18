package com.safemode.llconnect.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.WhoAmI
import com.safemode.llconnect.data.settings.AuthMode
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ServerInfo(
    val user: WhoAmI?,
    val version: String?,
    /** True when connected via API key, where whoami returns the key's name, not a username. */
    val usingApiKey: Boolean,
)

class ServerViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    /** Editable connection form. Persisted only when the user taps Save & test. */
    private val _form = MutableStateFlow(ConnectionConfig())
    val form: StateFlow<ConnectionConfig> = _form.asStateFlow()

    private val _state = MutableStateFlow<UiState<ServerInfo>>(UiState.Loading)
    val state: StateFlow<UiState<ServerInfo>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settings.config.first()
            _form.value = config
            reload(config, showLoading = true)
        }
    }

    /** Edits the connection form in memory; persisted on [saveAndTest]. */
    fun update(transform: (ConnectionConfig) -> ConnectionConfig) {
        _form.value = transform(_form.value)
    }

    /** Manual retry using the latest saved config. */
    fun load() {
        viewModelScope.launch { reload(settings.config.first(), showLoading = true) }
    }

    /** Pull-to-refresh: re-query the currently saved connection. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(settings.config.first(), showLoading = false)
            _refreshing.value = false
        }
    }

    /**
     * Persists the connection fields (merged onto the latest saved config so unrelated
     * preferences aren't clobbered), pushes them to the API provider, and re-queries the server.
     */
    fun saveAndTest() {
        viewModelScope.launch {
            val form = _form.value
            val merged = settings.config.first().copy(
                scheme = form.scheme,
                host = form.host,
                port = form.port,
                authMode = form.authMode,
                apiKey = form.apiKey,
                basicUsername = form.basicUsername,
                basicPassword = form.basicPassword,
                cultureInvariant = form.cultureInvariant,
            )
            settings.save(merged)
            Graph.apiProvider.updateConfig(merged)
            _form.value = merged
            reload(merged, showLoading = true)
        }
    }

    private suspend fun reload(config: ConnectionConfig, showLoading: Boolean) {
        Graph.apiProvider.updateConfig(config)
        if (!config.isConfigured) {
            _state.value = UiState.NotConfigured
            return
        }
        if (showLoading) _state.value = UiState.Loading
        val who = repository.whoAmI()
        if (who.isFailure) {
            _state.value = UiState.Error(who.exceptionOrNull()?.message ?: "Failed to reach server.")
            return
        }
        val version = repository.version().getOrNull()
        _state.value = UiState.Success(
            ServerInfo(
                user = who.getOrNull(),
                version = version,
                usingApiKey = config.authMode == AuthMode.API_KEY,
            ),
        )
    }
}
