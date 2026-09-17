package com.safemode.llconnect.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
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
import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.remote.models.GasRecordRequest
import com.safemode.llconnect.data.remote.models.GenericRecordRequest
import com.safemode.llconnect.data.remote.models.OdometerRecordRequest
import com.safemode.llconnect.data.remote.models.TaxRecordRequest
import com.safemode.llconnect.ui.common.DateField
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    vehicleId: String,
    areaName: String,
    recordId: String,
    onBack: () -> Unit,
) {
    val area = remember(areaName) { recordAreaFromName(areaName) }
    val isEdit = recordId.isNotBlank()
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odometer by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var fuelConsumed by remember { mutableStateOf("") }
    var isFillToFull by remember { mutableStateOf(true) }
    var missedFuelUp by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(isEdit) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // In edit mode, fetch the existing record and pre-fill the fields.
    LaunchedEffect(recordId) {
        if (!isEdit) return@LaunchedEffect
        loading = true
        Graph.repository.getRecordForEdit(area, vehicleId, recordId).fold(
            onSuccess = { data ->
                if (data != null) {
                    date = data.date?.ifBlank { date } ?: date
                    odometer = data.odometer?.toString() ?: ""
                    description = data.description.orEmpty()
                    cost = data.cost?.toString() ?: ""
                    fuelConsumed = data.fuelConsumed?.toString() ?: ""
                    isFillToFull = data.isFillToFull ?: true
                    missedFuelUp = data.missedFuelUp ?: false
                    notes = data.notes.orEmpty()
                    tags = data.tags.orEmpty()
                } else {
                    error = "Could not load this record for editing."
                }
                loading = false
            },
            onFailure = {
                error = it.message
                loading = false
            },
        )
    }

    val showOdometer = area in setOf(RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE, RecordArea.GAS, RecordArea.ODOMETER)
    val showDescription = area in setOf(RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE, RecordArea.TAX)
    val showCost = area in setOf(RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE, RecordArea.TAX, RecordArea.GAS)
    val showGas = area == RecordArea.GAS
    val verb = if (isEdit) "Edit" else "Add"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$verb ${area.label.lowercase().trimEnd('s')} record") },
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
            DateField(label = "Date", value = date, onValueChange = { date = it })

            if (showOdometer) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter(Char::isDigit) },
                    label = { Text("Odometer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showDescription) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showGas) {
                OutlinedTextField(
                    value = fuelConsumed,
                    onValueChange = { fuelConsumed = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Fuel consumed") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Fill to full", isFillToFull) { isFillToFull = it }
                ToggleRow("Missed a previous fuel-up", missedFuelUp) { missedFuelUp = it }
            }
            if (showCost) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
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
                        val result = submit(
                            area = area,
                            vehicleId = vehicleId,
                            id = recordId.toLongOrNull().takeIf { isEdit },
                            date = date,
                            odometer = odometer.toLongOrNull(),
                            description = description.ifBlank { null },
                            cost = cost.toDoubleOrNull(),
                            fuelConsumed = fuelConsumed.toDoubleOrNull(),
                            isFillToFull = isFillToFull,
                            missedFuelUp = missedFuelUp,
                            notes = notes.ifBlank { null },
                            tags = tags.ifBlank { null },
                        )
                        submitting = false
                        result.fold(
                            onSuccess = { onBack() },
                            onFailure = { error = it.message },
                        )
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (isEdit) "Update record" else "Save record")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private suspend fun submit(
    area: RecordArea,
    vehicleId: String,
    id: Long?,
    date: String,
    odometer: Long?,
    description: String?,
    cost: Double?,
    fuelConsumed: Double?,
    isFillToFull: Boolean,
    missedFuelUp: Boolean,
    notes: String?,
    tags: String?,
): Result<Unit> {
    val repo = Graph.repository
    val isEdit = id != null
    return when (area) {
        RecordArea.SERVICE, RecordArea.REPAIR, RecordArea.UPGRADE -> {
            val body = GenericRecordRequest(
                id = id,
                date = date,
                odometer = odometer,
                description = description,
                cost = cost,
                notes = notes,
                tags = tags,
            )
            if (isEdit) repo.updateServiceLike(area, body) else repo.addServiceLike(area, vehicleId, body)
        }
        RecordArea.TAX -> {
            val body = TaxRecordRequest(
                id = id,
                date = date,
                description = description,
                cost = cost,
                notes = notes,
                tags = tags,
            )
            if (isEdit) repo.updateTax(body) else repo.addTax(vehicleId, body)
        }
        RecordArea.GAS -> {
            val body = GasRecordRequest(
                id = id,
                date = date,
                odometer = odometer,
                fuelConsumed = fuelConsumed,
                cost = cost,
                isFillToFull = isFillToFull,
                missedFuelUp = missedFuelUp,
                notes = notes,
                tags = tags,
            )
            if (isEdit) repo.updateGas(body) else repo.addGas(vehicleId, body)
        }
        RecordArea.ODOMETER -> {
            val body = OdometerRecordRequest(
                id = id,
                date = date,
                odometer = odometer,
                notes = notes,
                tags = tags,
            )
            if (isEdit) repo.updateOdometer(body) else repo.addOdometer(vehicleId, body)
        }
        else -> Result.failure(IllegalArgumentException("Editing ${area.label} records isn't supported yet."))
    }
}
