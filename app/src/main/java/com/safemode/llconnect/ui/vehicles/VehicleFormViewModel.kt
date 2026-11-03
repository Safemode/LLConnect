package com.safemode.llconnect.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.VehicleAddRequest
import com.safemode.llconnect.data.remote.models.VehicleUpdateRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Runs the vehicle form's save, so it keeps going when the screen is recreated by a rotation or
 * theme change instead of being cancelled part way (and then retried into a duplicate vehicle).
 * The fields stay in the composable; each save's result comes back on [results].
 */
class VehicleFormViewModel : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    // Unbounded so a result that lands while the screen is being recreated waits for the new one.
    private val _results = Channel<Result<Unit>>(Channel.UNLIMITED)
    val results: Flow<Result<Unit>> = _results.receiveAsFlow()

    fun add(body: VehicleAddRequest) = save { Graph.repository.addVehicle(body) }

    fun update(body: VehicleUpdateRequest) = save { Graph.repository.updateVehicle(body) }

    private fun save(block: suspend () -> Result<Unit>) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                _results.trySend(block())
            } finally {
                _saving.value = false
            }
        }
    }
}
