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

    init {
        viewModelScope.launch { _config.value = settings.config.first() }
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
     * Applies a preference change and persists it right away. Only the preference fields are
     * written (merged onto the latest saved config) so connection settings edited elsewhere
     * are never clobbered.
     */
    fun updateAndSave(transform: (ConnectionConfig) -> ConnectionConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        viewModelScope.launch {
            val merged = settings.config.first().copy(
                fuelEconomyUnit = updated.fuelEconomyUnit,
                recordSortOrder = updated.recordSortOrder,
                cacheSize = updated.cacheSize,
            )
            settings.save(merged)
            apiProvider.updateConfig(merged)
        }
    }
}
