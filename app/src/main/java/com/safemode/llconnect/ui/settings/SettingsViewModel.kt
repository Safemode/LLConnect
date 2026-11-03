package com.safemode.llconnect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Backs the Settings screen, which holds standalone app preferences (fuel economy units,
 * record list order) and cache management. Each change is persisted immediately. Server
 * connection and authentication live on the Server screen instead.
 */
class SettingsViewModel : ViewModel() {
    private val settings = Graph.settingsRepository
    private val apiProvider = Graph.apiProvider

    private val _config = MutableStateFlow(ConnectionConfig())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    /** Current on-disk cache usage in bytes; refreshed on load and after clearing. */
    private val _cacheUsage = MutableStateFlow(0L)
    val cacheUsage: StateFlow<Long> = _cacheUsage.asStateFlow()

    // Serializes saves so two quick changes can't each read the same old config and drop the other.
    private val saveMutex = Mutex()

    init {
        // Follow the saved config rather than reading it once, so the screen settles on what is on
        // disk even if a change is made before the first load finishes.
        viewModelScope.launch { settings.config.collect { _config.value = it } }
        refreshCacheUsage()
    }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            _cacheUsage.value = withContext(Dispatchers.IO) { apiProvider.cacheUsageBytes() }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { apiProvider.clearCache() }
            _cacheUsage.value = withContext(Dispatchers.IO) { apiProvider.cacheUsageBytes() }
        }
    }

    /**
     * Applies a preference change and persists it right away. The change is shown immediately and
     * then applied to the latest saved config, so connection settings edited elsewhere are never
     * clobbered, and a change made before the screen finished loading can't save the defaults.
     * [onSaved] runs with the config as written.
     */
    fun updateAndSave(
        onSaved: (ConnectionConfig) -> Unit = {},
        transform: (ConnectionConfig) -> ConnectionConfig,
    ) {
        _config.value = transform(_config.value)
        viewModelScope.launch {
            val saved = saveMutex.withLock {
                transform(settings.config.first()).also { settings.save(it) }
            }
            apiProvider.updateConfig(saved)
            onSaved(saved)
        }
    }
}
