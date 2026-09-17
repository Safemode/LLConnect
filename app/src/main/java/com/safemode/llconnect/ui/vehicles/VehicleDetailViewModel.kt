package com.safemode.llconnect.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleDetailViewModel(private val vehicleId: String) : ViewModel() {
    private val repository = Graph.repository

    private val _state = MutableStateFlow<UiState<Vehicle>>(UiState.Loading)
    val state: StateFlow<UiState<Vehicle>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        load()
    }

    fun delete() {
        viewModelScope.launch {
            _deleting.value = true
            repository.deleteVehicle(vehicleId).fold(
                onSuccess = { _deleted.value = true },
                onFailure = { _actionError.value = it.message ?: "Delete failed." },
            )
            _deleting.value = false
        }
    }

    fun consumeError() {
        _actionError.value = null
    }

    fun load() {
        viewModelScope.launch { reload(showLoading = true) }
    }

    /** Pull-to-refresh. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            reload(showLoading = false)
            _refreshing.value = false
        }
    }

    private suspend fun reload(showLoading: Boolean) {
        if (showLoading) _state.value = UiState.Loading
        _state.value = repository.getVehicleById(vehicleId).fold(
            onSuccess = { vehicle ->
                if (vehicle != null) UiState.Success(vehicle)
                else UiState.Error("Vehicle not found.")
            },
            onFailure = { UiState.Error(it.message ?: "Failed to load vehicle.") },
        )
    }
}
