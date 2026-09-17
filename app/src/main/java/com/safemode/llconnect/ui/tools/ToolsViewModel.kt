package com.safemode.llconnect.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolsViewModel : ViewModel() {
    private val repository = Graph.repository

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    fun makeBackup() = run("Backup created:") { repository.makeBackup() }
    fun cleanup(deep: Boolean) = run("Cleanup done:") { repository.cleanup(deep) }
    fun tempFiles() = run("Temp files:") { repository.tempFiles() }
    fun sendReminders() = run("Reminders sent:") { repository.sendReminders() }

    private fun run(prefix: String, block: suspend () -> Result<String>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _result.value = block().fold(
                onSuccess = { "$prefix $it" },
                onFailure = { it.message ?: "Action failed." },
            )
            _busy.value = false
        }
    }

    fun consumeResult() {
        _result.value = null
    }
}
