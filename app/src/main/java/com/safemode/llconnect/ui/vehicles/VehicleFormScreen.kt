package com.safemode.llconnect.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.safemode.llconnect.Graph
import com.safemode.llconnect.data.remote.models.Vehicle
import com.safemode.llconnect.data.remote.models.VehicleAddRequest
import com.safemode.llconnect.data.remote.models.VehicleUpdateRequest
import com.safemode.llconnect.ui.common.FuelTypeField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(
    vehicleId: String,
    onBack: () -> Unit,
) {
    val isEdit = vehicleId.isNotBlank()
    val scope = rememberCoroutineScope()

    var year by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("Gasoline") }
    var identifier by remember { mutableStateOf("LicensePlate") }
    var tags by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(isEdit) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vehicleId) {
        if (!isEdit) return@LaunchedEffect
        loading = true
        Graph.repository.getVehicleById(vehicleId).fold(
            onSuccess = { v ->
                if (v != null) {
                    year = v.year?.takeIf { it > 0 }?.toString().orEmpty()
                    make = v.make.orEmpty()
                    model = v.model.orEmpty()
                    licensePlate = v.licensePlate.orEmpty()
                    fuelType = v.deriveFuelType()
                    identifier = v.vehicleIdentifier?.ifBlank { "LicensePlate" } ?: "LicensePlate"
                    tags = v.tags?.joinToString(" ").orEmpty()
                } else {
                    error = "Could not load this vehicle."
                }
                loading = false
            },
            onFailure = {
                error = it.message
                loading = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit vehicle" else "Add vehicle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter(Char::isDigit).take(4) },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                label = { Text("Make") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = licensePlate,
                onValueChange = { licensePlate = it },
                label = { Text("License plate") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FuelTypeField(value = fuelType, onValueChange = { fuelType = it })
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (space-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    error = null
                    submitting = true
                    scope.launch {
                        val result = if (isEdit) {
                            Graph.repository.updateVehicle(
                                VehicleUpdateRequest(
                                    id = vehicleId.toLongOrNull() ?: 0L,
                                    year = year.toLongOrNull(),
                                    make = make.ifBlank { null },
                                    model = model.ifBlank { null },
                                    identifier = identifier,
                                    licensePlate = licensePlate.ifBlank { null },
                                    fuelType = fuelType,
                                    tags = tags.ifBlank { null },
                                ),
                            )
                        } else {
                            Graph.repository.addVehicle(
                                VehicleAddRequest(
                                    year = year.toLongOrNull(),
                                    make = make.ifBlank { null },
                                    model = model.ifBlank { null },
                                    identifier = identifier,
                                    licensePlate = licensePlate.ifBlank { null },
                                    fuelType = fuelType,
                                    tags = tags.ifBlank { null },
                                ),
                            )
                        }
                        submitting = false
                        result.fold(
                            onSuccess = { onBack() },
                            onFailure = { error = it.message },
                        )
                    }
                },
                enabled = !submitting && make.isNotBlank() && model.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isEdit) "Update vehicle" else "Save vehicle")
            }
        }
    }
}

private fun Vehicle.deriveFuelType(): String = when {
    isElectric == true -> "Electric"
    isDiesel == true -> "Diesel"
    else -> "Gasoline"
}
