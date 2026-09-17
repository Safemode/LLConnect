package com.safemode.llconnect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.settings.ConnectionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data class Success(val message: String) : TestResult
    data class Failure(val message: String) : TestResult
}

class SettingsViewModel : ViewModel() {
    private val settings = Graph.settingsRepository
    private val repository = Graph.repository

    private val _config = MutableStateFlow(ConnectionConfig())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            _config.value = settings.config.first()
            _loaded.value = true
        }
    }

    fun update(transform: (ConnectionConfig) -> ConnectionConfig) {
        _config.value = transform(_config.value)
        _saved.value = false
        _testResult.value = TestResult.Idle
    }

    fun save(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            settings.save(_config.value)
            _saved.value = true
            onDone?.invoke()
        }
    }

    /** Persists current settings, then verifies them against /api/whoami. */
    fun saveAndTest() {
        viewModelScope.launch {
            settings.save(_config.value)
            // Ensure the provider sees the newest config immediately.
            Graph.apiProvider.updateConfig(_config.value)
            _saved.value = true
            _testResult.value = TestResult.Testing
            val who = repository.whoAmI()
            _testResult.value = who.fold(
                onSuccess = { user ->
                    val name = user.userName ?: user.emailAddress ?: "user"
                    TestResult.Success("Connected as $name.")
                },
                onFailure = { e ->
                    TestResult.Failure(e.message ?: "Connection failed.")
                },
            )
        }
    }
}
