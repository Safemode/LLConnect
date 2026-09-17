package com.safemode.llconnect.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.WhoAmI
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ServerInfo(
    val user: WhoAmI?,
    val version: String?,
)

class ServerViewModel : ViewModel() {
    private val repository = Graph.repository
    private val settings = Graph.settingsRepository

    private val _state = MutableStateFlow<UiState<ServerInfo>>(UiState.Loading)
    val state: StateFlow<UiState<ServerInfo>> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        // React to connection changes (e.g. after saving Settings) by reloading automatically.
        viewModelScope.launch {
            settings.config
                .map { ConnectionKey.from(it) to it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, config) -> reload(config, showLoading = true) }
        }
    }

    /** Manual retry using the latest saved config. */
    fun load() {
        viewModelScope.launch { reload(settings.config.first(), showLoading = true) }
    }

    /** Pull-to-refresh. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(settings.config.first(), showLoading = false)
            _refreshing.value = false
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
        _state.value = UiState.Success(ServerInfo(user = who.getOrNull(), version = version))
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

    fun makeBackup() {
        viewModelScope.launch {
            _message.value = "Requesting backup…"
            repository.makeBackup().fold(
                onSuccess = { _message.value = "Backup created on the server." },
                onFailure = { _message.value = it.message ?: "Backup failed." },
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
